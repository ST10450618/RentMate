-- RM-0022: push notifications for new bills, repair updates and chores that are due.
--
-- The database calls the send-push Edge Function through pg_net. The function
-- URL and its shared secret live in Supabase Vault, so no secret sits in this
-- file. Until both Vault secrets exist, every push is skipped with a NOTICE.
-- A push problem must never undo the user's own write, so every step that
-- sends catches its errors and raises a NOTICE instead.
--
-- One-time setup is in supabase/functions/send-push/README.md.

-- 1. pg_net -----------------------------------------------------------------------
-- If the extension is not available, the migration still applies and pushes
-- stay off until it is enabled.
do $$
begin
  create extension if not exists pg_net;
exception when others then
  raise notice 'pg_net not available (%). Enable it to send pushes.', sqlerrm;
end $$;

-- 2. private schema ---------------------------------------------------------------
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

-- South African money text: 1200 -> "1 200,00".
create function private.format_rand(amount numeric)
returns text
language sql
immutable
as $$
  select replace(replace(to_char(amount, 'FM999,999,999,990.00'), ',', ' '), '.', ',')
$$;

-- 3. send_push ----------------------------------------------------------------------
create function private.send_push(
  user_ids uuid[],
  channel text,
  title text,
  body text,
  ref_id uuid default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  fn_url text;
  fn_secret text;
begin
  if user_ids is null or coalesce(array_length(user_ids, 1), 0) = 0 then
    return;
  end if;

  select decrypted_secret into fn_url
  from vault.decrypted_secrets where name = 'push_function_url';
  select decrypted_secret into fn_secret
  from vault.decrypted_secrets where name = 'push_webhook_secret';

  if fn_url is null or fn_secret is null then
    raise notice 'push skipped: Vault secrets push_function_url and push_webhook_secret are not set';
    return;
  end if;

  begin
    perform net.http_post(
      url := fn_url,
      headers := jsonb_build_object('Content-Type', 'application/json', 'x-push-secret', fn_secret),
      body := jsonb_build_object(
        'user_ids', to_jsonb(user_ids),
        'channel', channel,
        'title', title,
        'body', body,
        'id', ref_id
      )
    );
  exception when others then
    raise notice 'push failed: %', sqlerrm;
  end;
end;
$$;

revoke all on function private.send_push(uuid[], text, text, text, uuid) from public, anon, authenticated;
revoke all on function private.format_rand(numeric) from public, anon, authenticated;

-- 4. New bill -------------------------------------------------------------------------
-- The shares are inserted after the bill row, so this is a deferred constraint
-- trigger. It fires at commit, when the shares exist. Each person with a share
-- gets their own amount. The issuer is skipped, so a cron copy of a repeating
-- bill (which runs without a signed-in user) works the same way.
create function private.notify_bill_added()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  issuer_name text;
  share record;
begin
  select display_name into issuer_name from public.profiles where id = new.issued_by_user_id;

  for share in
    select user_id, amount_owed from public.bill_shares
    where bill_id = new.id and user_id <> new.issued_by_user_id
  loop
    perform private.send_push(
      array[share.user_id],
      'bills',
      'New bill: ' || new.title,
      coalesce(issuer_name, 'A housemate') || ' added R' || private.format_rand(new.amount)
        || '. Your share is R' || private.format_rand(share.amount_owed) || '.',
      new.id
    );
  end loop;
  return null;
exception when others then
  raise notice 'bill push failed: %', sqlerrm;
  return null;
end;
$$;

revoke all on function private.notify_bill_added() from public, anon, authenticated;

create constraint trigger bills_push_after_insert
  after insert on public.bills
  deferrable initially deferred
  for each row execute function private.notify_bill_added();

-- 5. Repair request status change -----------------------------------------------------
create function private.notify_maintenance_status()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  who uuid[];
  plain text;
begin
  plain := case new.status
    when 'Open' then 'Back to open.'
    when 'Sent' then 'Sent to the landlord.'
    when 'Acknowledged' then 'The landlord has seen it.'
    when 'Resolved' then 'Marked as fixed.'
    else new.status
  end;

  -- The raiser and every housemate, except the person who made the change.
  select coalesce(array_agg(distinct u), '{}') into who
  from (
    select new.raised_by_user_id as u
    union
    select user_id from public.household_members where household_id = new.household_id
  ) people
  where u is distinct from auth.uid();

  perform private.send_push(who, 'maintenance', 'Repair update: ' || new.title, plain, new.id);
  return null;
exception when others then
  raise notice 'maintenance push failed: %', sqlerrm;
  return null;
end;
$$;

revoke all on function private.notify_maintenance_status() from public, anon, authenticated;

create trigger maintenance_requests_push_after_status
  after update of status on public.maintenance_requests
  for each row
  when (old.status is distinct from new.status)
  execute function private.notify_maintenance_status();

-- 6. Chores that are due ---------------------------------------------------------------
-- Same cycle rules as complete_chore in 0009. A chore with no completion in
-- its current cycle is due. The current assignee is
-- rotation_order[(current_rotation_index % n) + 1]. A chore with no rotation
-- has no assignee, so it is skipped. Returns how many people were notified.
create function private.notify_chores_due()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  c record;
  assignee uuid;
  n int;
  cycle_start timestamptz;
  sent int := 0;
begin
  for c in select * from public.chores loop
    n := coalesce(array_length(c.rotation_order, 1), 0);
    continue when n = 0;

    cycle_start := case c.recurrence
      when 'Weekly' then date_trunc('week', now())
      when 'Monthly' then date_trunc('month', now())
      else '-infinity'::timestamptz
    end;
    continue when exists (
      select 1 from public.chore_completions
      where chore_id = c.id and completed_at >= cycle_start
    );

    assignee := c.rotation_order[(c.current_rotation_index % n) + 1];
    continue when assignee is null;

    perform private.send_push(
      array[assignee], 'chores', 'Your turn: ' || c.title,
      'Mark it done in RentMate when you finish.', c.id
    );
    sent := sent + 1;
  end loop;
  return sent;
end;
$$;

revoke all on function private.notify_chores_due() from public, anon, authenticated;

-- 7. Daily schedule ---------------------------------------------------------------------
-- 05:00 UTC is 07:00 in South Africa. If pg_cron is not available, the
-- migration still applies and the job has to be scheduled by hand.
do $$
begin
  create extension if not exists pg_cron;
  perform cron.unschedule(jobid) from cron.job where jobname = 'notify-chores-due';
  perform cron.schedule('notify-chores-due', '0 5 * * *', 'select private.notify_chores_due()');
exception when others then
  raise notice 'pg_cron not available (%). Schedule private.notify_chores_due() by hand.', sqlerrm;
end $$;
