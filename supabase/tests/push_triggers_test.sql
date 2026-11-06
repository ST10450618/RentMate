-- RM-0022: proves the push triggers from migration 0016.
--
-- Run as the postgres user against a database with every migration applied:
--   psql ... -v ON_ERROR_STOP=1 -q -f supabase/tests/push_triggers_test.sql
-- Everything is rolled back at the end. Each check prints "PASS: ..." as a
-- NOTICE. The first failing check raises an exception and stops the script.
--
-- Most checks replace private.send_push with a stub that records its calls in a
-- temp table. That keeps the test free of the network and of Vault state. The
-- stub is rolled back with everything else. One check at the end uses the real
-- send_push with no Vault secrets.

begin;

insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-0000-0000-00000000000a', 'thandi@test.local', '{"full_name":"Thandi"}'),
  ('00000000-0000-0000-0000-00000000000b', 'bob@test.local',    '{"full_name":"Bob"}'),
  ('00000000-0000-0000-0000-00000000000c', 'carol@test.local',  '{"full_name":"Carol"}'),
  ('00000000-0000-0000-0000-00000000000d', 'dan@test.local',    '{"full_name":"Dan"}');

insert into public.households (id, name, invite_code) values
  ('10000000-0000-0000-0000-00000000000a', 'House A', 'TSTAAA');

-- Thandi (a), Bob (b) and Carol (c) share House A. Dan (d) is an outsider.
insert into public.household_members (household_id, user_id) values
  ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'),
  ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b'),
  ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000c');

-- 0. The real send_push with no Vault secrets: the bill still saves ----------------------
do $$
declare
  b public.bills;
begin
  if exists (select 1 from vault.decrypted_secrets where name in ('push_function_url', 'push_webhook_secret')) then
    raise exception 'FAIL: the test needs a database without the push Vault secrets';
  end if;
  set local role authenticated;
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
          set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  b := public.create_bill(
    '10000000-0000-0000-0000-00000000000a', 'Rates', 300, now(), 'Equal',
    '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":0},
      {"user_id":"00000000-0000-0000-0000-00000000000b","value":0},
      {"user_id":"00000000-0000-0000-0000-00000000000c","value":0}]'::jsonb);
  set constraints all immediate;  -- the real trigger runs and only raises a NOTICE
  set constraints all deferred;
  if not exists (select 1 from public.bills where id = b.id) then
    raise exception 'FAIL: the bill is missing';
  end if;
  reset role;
  raise notice 'PASS: with no Vault secrets a bill still saves (push skipped with a NOTICE)';
end $$;

create temp table push_calls (
  n serial, user_ids uuid[], channel text, title text, body text, ref_id uuid
);
grant all on push_calls to public;
grant usage on sequence push_calls_n_seq to public;

create or replace function private.send_push(
  user_ids uuid[], channel text, title text, body text, ref_id uuid default null
) returns void language plpgsql security definer set search_path = '' as $$
begin
  insert into pg_temp.push_calls (user_ids, channel, title, body, ref_id)
  values (user_ids, channel, title, body, ref_id);
end $$;

set local role authenticated;

-- 1. create_bill by Thandi notifies Bob and Carol, each with their own share ----
do $$
declare
  b public.bills;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
          set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  b := public.create_bill(
    '10000000-0000-0000-0000-00000000000a', 'Electricity', 1200, now() + interval '7 days', 'ExactAmount',
    '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":400},
      {"user_id":"00000000-0000-0000-0000-00000000000b","value":300},
      {"user_id":"00000000-0000-0000-0000-00000000000c","value":500}]'::jsonb);

  if (select count(*) from push_calls) <> 0 then
    raise exception 'FAIL: pushes were sent before the transaction committed';
  end if;
  set constraints all immediate;  -- fires the deferred trigger, as commit would
  set constraints all deferred;

  if (select count(*) from push_calls) <> 2 then
    raise exception 'FAIL: expected 2 pushes, got %', (select count(*) from push_calls);
  end if;
  if exists (select 1 from push_calls where '00000000-0000-0000-0000-00000000000a' = any (user_ids)) then
    raise exception 'FAIL: the issuer was notified';
  end if;
  raise notice 'PASS: the issuer is not notified';

  if not exists (select 1 from push_calls
                 where user_ids = array['00000000-0000-0000-0000-00000000000b']::uuid[]
                   and channel = 'bills' and title = 'New bill: Electricity' and ref_id = b.id
                   and body = 'Thandi added R1 200,00. Your share is R300,00.') then
    raise exception 'FAIL: Bob did not get his own share. Got: %', (select body from push_calls where user_ids[1] = '00000000-0000-0000-0000-00000000000b');
  end if;
  if not exists (select 1 from push_calls
                 where user_ids = array['00000000-0000-0000-0000-00000000000c']::uuid[]
                   and body = 'Thandi added R1 200,00. Your share is R500,00.') then
    raise exception 'FAIL: Carol did not get her own share';
  end if;
  raise notice 'PASS: create_bill notifies each housemate with their own share';
end $$;

-- 2. A non-member still cannot add a bill ----------------------------------------------
do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000d","role":"authenticated"}', true),
          set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000d', true);
  begin
    perform public.create_bill(
      '10000000-0000-0000-0000-00000000000a', 'Sneaky', 100, now(), 'Equal',
      '[{"user_id":"00000000-0000-0000-0000-00000000000d","value":0}]'::jsonb);
    raise exception 'FAIL: a non-member added a bill';
  exception when insufficient_privilege then
    null;
  end;
  begin
    insert into public.bills (household_id, title, amount, due_date, issued_by_user_id)
    values ('10000000-0000-0000-0000-00000000000a', 'Sneaky', 100, now(), '00000000-0000-0000-0000-00000000000d');
    raise exception 'FAIL: a non-member inserted a bill directly';
  exception when insufficient_privilege or check_violation then
    null;
  end;
  set constraints all immediate;
  set constraints all deferred;
  if (select count(*) from push_calls) <> 2 then
    raise exception 'FAIL: a refused bill still sent a push';
  end if;
  raise notice 'PASS: a non-member cannot add a bill, and nothing is sent';
end $$;

-- 3. Repair status change ---------------------------------------------------------------
reset role;
insert into public.maintenance_requests (id, household_id, raised_by_user_id, title, description, category, urgency)
values ('50000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-00000000000a',
        '00000000-0000-0000-0000-00000000000b', 'Leaking tap', 'Drips all night', 'Plumbing', 'Low');
truncate push_calls;
set local role authenticated;

do $$
declare
  r record;
begin
  -- Bob raised the request and sends it. Thandi and Carol hear about it.
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
          set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  perform public.send_to_landlord('10000000-0000-0000-0000-00000000000a');

  select * into r from push_calls;
  if (select count(*) from push_calls) <> 1 then
    raise exception 'FAIL: expected 1 maintenance push, got %', (select count(*) from push_calls);
  end if;
  if r.channel <> 'maintenance' or r.title <> 'Repair update: Leaking tap' or r.body <> 'Sent to the landlord.'
     or r.ref_id <> '50000000-0000-0000-0000-00000000000a' then
    raise exception 'FAIL: wrong maintenance push: % / % / %', r.channel, r.title, r.body;
  end if;
  if r.user_ids @> array['00000000-0000-0000-0000-00000000000b']::uuid[] then
    raise exception 'FAIL: the person who made the change was notified';
  end if;
  if not (r.user_ids @> array['00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000c']::uuid[])
     or array_length(r.user_ids, 1) <> 2 then
    raise exception 'FAIL: wrong people notified: %', r.user_ids;
  end if;
  raise notice 'PASS: a status change notifies the other housemates, not the person who made it';

  -- Thandi records the landlord's reply. Bob (the raiser) is now notified too.
  truncate push_calls;
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
          set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  -- Thandi is not the sender or the raiser, so this must be refused.
  begin
    perform public.advance_maintenance_status('50000000-0000-0000-0000-00000000000a');
    raise exception 'FAIL: a bystander advanced the request';
  exception when insufficient_privilege then
    null;
  end;
  if (select count(*) from push_calls) <> 0 then
    raise exception 'FAIL: a refused change sent a push';
  end if;

  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
          set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  perform public.advance_maintenance_status('50000000-0000-0000-0000-00000000000a');
  select * into r from push_calls;
  if r.body <> 'The landlord has seen it.' or array_length(r.user_ids, 1) <> 2 then
    raise exception 'FAIL: wrong acknowledged push: % %', r.body, r.user_ids;
  end if;
  raise notice 'PASS: each later step sends its own text';
end $$;

reset role;
do $$
begin
  truncate push_calls;
  update public.maintenance_requests set status = 'Acknowledged', title = 'Leaking tap'
  where id = '50000000-0000-0000-0000-00000000000a';
  if (select count(*) from push_calls) <> 0 then
    raise exception 'FAIL: an update with the same status sent a push';
  end if;
  update public.maintenance_requests set description = 'Still dripping'
  where id = '50000000-0000-0000-0000-00000000000a';
  if (select count(*) from push_calls) <> 0 then
    raise exception 'FAIL: a non-status update sent a push';
  end if;
  raise notice 'PASS: an update that keeps the same status notifies nobody';

  -- Without a signed-in user (for example a dashboard edit) everyone hears.
  perform set_config('request.jwt.claims', '', true), set_config('request.jwt.claim.sub', '', true);
  update public.maintenance_requests set status = 'Resolved'
  where id = '50000000-0000-0000-0000-00000000000a';
  if (select array_length(user_ids, 1) from push_calls) <> 3 then
    raise exception 'FAIL: a change with no signed-in user should notify all 3 people';
  end if;
  raise notice 'PASS: with no signed-in user, the raiser and all housemates are notified';
end $$;

-- 4. Chores due -------------------------------------------------------------------------
-- "Dishes": weekly, rotation a -> b, starts on a. "Bins": weekly, already done
-- this week. "Paint": once, already done. "Lawn": monthly, assignee c, not done.
-- "Solo": no rotation, so nobody to notify.
insert into public.chores (id, household_id, title, recurrence, rotation_order, current_rotation_index) values
  ('40000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-00000000000a', 'Dishes', 'Weekly',
   array['00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b']::uuid[], 0),
  ('40000000-0000-0000-0000-00000000000b', '10000000-0000-0000-0000-00000000000a', 'Bins', 'Weekly',
   array['00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b']::uuid[], 1),
  ('40000000-0000-0000-0000-00000000000c', '10000000-0000-0000-0000-00000000000a', 'Paint', 'Once',
   array['00000000-0000-0000-0000-00000000000a']::uuid[], 0),
  ('40000000-0000-0000-0000-00000000000d', '10000000-0000-0000-0000-00000000000a', 'Lawn', 'Monthly',
   array['00000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-00000000000c']::uuid[], 3),
  ('40000000-0000-0000-0000-00000000000e', '10000000-0000-0000-0000-00000000000a', 'Solo', 'Weekly', '{}', 0);

insert into public.chore_completions (chore_id, completed_by_user_id, points_awarded) values
  ('40000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-00000000000a', 1),
  ('40000000-0000-0000-0000-00000000000c', '00000000-0000-0000-0000-00000000000a', 1);

do $$
declare
  sent int;
begin
  truncate push_calls;
  sent := private.notify_chores_due();
  if sent <> 2 or (select count(*) from push_calls) <> 2 then
    raise exception 'FAIL: expected 2 chore pushes, got % (% calls)', sent, (select count(*) from push_calls);
  end if;
  if not exists (select 1 from push_calls
                 where user_ids = array['00000000-0000-0000-0000-00000000000a']::uuid[]
                   and channel = 'chores' and title = 'Your turn: Dishes'
                   and body = 'Mark it done in RentMate when you finish.'
                   and ref_id = '40000000-0000-0000-0000-00000000000a') then
    raise exception 'FAIL: Thandi did not get the Dishes push';
  end if;
  -- Lawn: index 3 over 2 people -> rotation_order[(3 % 2) + 1] = Carol.
  if not exists (select 1 from push_calls
                 where user_ids = array['00000000-0000-0000-0000-00000000000c']::uuid[]
                   and title = 'Your turn: Lawn') then
    raise exception 'FAIL: Carol did not get the Lawn push';
  end if;
  raise notice 'PASS: only the current assignee of a chore that is not done this cycle is notified';

  if exists (select 1 from push_calls where title in ('Your turn: Bins', 'Your turn: Paint', 'Your turn: Solo')) then
    raise exception 'FAIL: a finished or unassigned chore sent a push';
  end if;
  raise notice 'PASS: finished, one-off done and unassigned chores send nothing';
end $$;

-- 5. A failing push cannot undo the bill --------------------------------------------------
create or replace function private.send_push(
  user_ids uuid[], channel text, title text, body text, ref_id uuid default null
) returns void language plpgsql security definer set search_path = '' as $$
begin
  raise exception 'push service is down';
end $$;

set local role authenticated;
do $$
declare
  b public.bills;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
          set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  b := public.create_bill(
    '10000000-0000-0000-0000-00000000000a', 'Water', 90, now(), 'Equal',
    '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":0},
      {"user_id":"00000000-0000-0000-0000-00000000000b","value":0},
      {"user_id":"00000000-0000-0000-0000-00000000000c","value":0}]'::jsonb);
  set constraints all immediate;
  set constraints all deferred;
  if not exists (select 1 from public.bills where id = b.id)
     or (select count(*) from public.bill_shares where bill_id = b.id) <> 3 then
    raise exception 'FAIL: the bill is gone after a push failure';
  end if;
  raise notice 'PASS: a failing push does not roll back the bill';
end $$;

rollback;
