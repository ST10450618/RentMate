-- RM-0049: recurring bills.
--
-- A bill can repeat weekly or monthly. A daily pg_cron job creates the next
-- copy of each repeating bill, with the same split, once its next due date has
-- arrived. Only the issuer can stop a bill repeating.
--
-- Each copy points at the bill it came from (repeat_parent_id), so a repeating
-- bill forms a chain. Only the latest bill in a chain makes the next copy.

-- 1. Columns ------------------------------------------------------------------
alter table public.bills
  add column repeat text not null default 'Never' check (repeat in ('Never', 'Weekly', 'Monthly')),
  add column repeat_parent_id uuid references public.bills (id) on delete set null,
  add column repeat_stopped boolean not null default false;

-- One copy per bill, ever. This is what makes the job safe to run twice.
create unique index bills_repeat_parent_unique
  on public.bills (repeat_parent_id) where repeat_parent_id is not null;

-- Members could update any bill column they issued. Limit that to the fields
-- the app edits; repeat settings change only through the functions below.
revoke update on public.bills from public, anon, authenticated;
grant update (title, amount, due_date, split_method) on public.bills to authenticated;

-- 2. create_bill with a repeat option ---------------------------------------------
-- Adding a parameter would leave the old function beside the new one, so the
-- old one is dropped. The default keeps six-argument calls working.
drop function if exists public.create_bill(uuid, text, numeric, timestamptz, text, jsonb);

create function public.create_bill(
  household_id_in uuid,
  title_in text,
  amount_in numeric,
  due_date_in timestamptz,
  split_method_in text,
  shares_in jsonb,
  repeat_in text default 'Never'
)
returns public.bills
language plpgsql
security definer
set search_path = public
as $$
declare
  caller uuid := auth.uid();
  new_bill public.bills;
  total_cents bigint;
  n int;
  ids uuid[];
  vals numeric[];
  cents bigint[];
  running bigint := 0;
  value_sum numeric := 0;
  i int;
begin
  if caller is null or not public.is_household_member(household_id_in) then
    raise exception 'Not a member of this household' using errcode = '42501';
  end if;
  if title_in is null or btrim(title_in) = '' then
    raise exception 'Title is required';
  end if;
  if amount_in is null or amount_in <= 0 then
    raise exception 'Amount must be greater than 0';
  end if;
  if split_method_in is null or split_method_in not in ('Equal', 'Percentage', 'ExactAmount') then
    raise exception 'Split method must be Equal, Percentage or ExactAmount';
  end if;
  if repeat_in is null or repeat_in not in ('Never', 'Weekly', 'Monthly') then
    raise exception 'Repeat must be Never, Weekly or Monthly';
  end if;
  if shares_in is null or jsonb_typeof(shares_in) <> 'array' or jsonb_array_length(shares_in) = 0 then
    raise exception 'At least one share is required';
  end if;

  total_cents := round(amount_in * 100)::bigint;
  if total_cents <= 0 then
    raise exception 'Amount must be at least 0.01';
  end if;

  select array_agg((e ->> 'user_id')::uuid order by o),
         array_agg(coalesce((e ->> 'value')::numeric, 0) order by o)
    into ids, vals
  from jsonb_array_elements(shares_in) with ordinality as t(e, o);

  n := array_length(ids, 1);

  if exists (select 1 from unnest(ids) u where u is null)
     or (select count(distinct u) from unnest(ids) u) <> n then
    raise exception 'Each person can only appear once in the split';
  end if;
  if exists (
    select 1 from unnest(ids) u
    where not exists (
      select 1 from public.household_members hm
      where hm.household_id = household_id_in and hm.user_id = u
    )
  ) then
    raise exception 'Everyone in the split must be a member of this household';
  end if;
  if exists (select 1 from unnest(vals) v where v < 0) then
    raise exception 'Split values cannot be negative';
  end if;

  cents := array_fill(0::bigint, array[n]);

  if split_method_in = 'Equal' then
    for i in 1..n loop
      cents[i] := round(total_cents::numeric / n)::bigint;
    end loop;
  elsif split_method_in = 'Percentage' then
    select sum(v) into value_sum from unnest(vals) v;
    if abs(value_sum - 100) > 0.01 then
      raise exception 'Percentages must add up to 100 (they add up to %)', value_sum;
    end if;
    for i in 1..n loop
      cents[i] := round(total_cents * vals[i] / 100)::bigint;
    end loop;
  else
    for i in 1..n loop
      cents[i] := round(vals[i] * 100)::bigint;
    end loop;
    if (select sum(c) from unnest(cents) c) <> total_cents then
      raise exception 'Amounts must add up to the bill total of % (they add up to %)',
        amount_in, (select sum(c) from unnest(cents) c)::numeric / 100;
    end if;
  end if;

  -- Equal and Percentage: the last share absorbs the rounding remainder.
  if split_method_in <> 'ExactAmount' then
    for i in 1..n - 1 loop
      running := running + cents[i];
    end loop;
    cents[n] := total_cents - running;
    if cents[n] < 0 then
      raise exception 'The split does not fit the bill amount';
    end if;
  end if;

  insert into public.bills (household_id, title, amount, due_date, split_method, issued_by_user_id, repeat)
  values (household_id_in, btrim(title_in), total_cents::numeric / 100, due_date_in, split_method_in, caller, repeat_in)
  returning * into new_bill;

  insert into public.bill_shares (bill_id, user_id, amount_owed)
  select new_bill.id, ids[k], cents[k]::numeric / 100
  from generate_series(1, n) k;

  return new_bill;
end;
$$;

revoke all on function public.create_bill(uuid, text, numeric, timestamptz, text, jsonb, text) from public, anon;
grant execute on function public.create_bill(uuid, text, numeric, timestamptz, text, jsonb, text) to authenticated;

-- 3. stop_bill_repeat -----------------------------------------------------------
-- Marks the bill and every later copy as stopped, so no new copy is made.
-- A missing bill and someone else's bill give the same error.
create function public.stop_bill_repeat(bill_id_in uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null or not exists (
    select 1 from public.bills where id = bill_id_in and issued_by_user_id = auth.uid()
  ) then
    raise exception 'Only the person who added this bill can stop it repeating' using errcode = '42501';
  end if;

  with recursive chain as (
    select id from public.bills where id = bill_id_in
    union all
    select b.id from public.bills b join chain c on b.repeat_parent_id = c.id
  )
  update public.bills set repeat_stopped = true
  where id in (select id from chain);
end;
$$;

revoke all on function public.stop_bill_repeat(uuid) from public, anon;
grant execute on function public.stop_bill_repeat(uuid) to authenticated;

-- 4. generate_recurring_bills -----------------------------------------------------
-- Makes one copy for each repeating bill whose next due date has arrived. A
-- bill that is several cycles behind catches up one cycle per run. The unique
-- index on repeat_parent_id guarantees a bill is copied only once.
create function public.generate_recurring_bills(as_of date default current_date)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  src record;
  new_id uuid;
  made integer := 0;
begin
  for src in
    select b.*,
           case b.repeat
             when 'Weekly' then b.due_date + interval '7 days'
             else b.due_date + interval '1 month'
           end as next_due
    from public.bills b
    where b.repeat <> 'Never'
      and not b.repeat_stopped
      and not exists (select 1 from public.bills c where c.repeat_parent_id = b.id)
  loop
    continue when src.next_due::date > as_of;

    insert into public.bills (household_id, title, amount, due_date, split_method,
                              issued_by_user_id, repeat, repeat_parent_id)
    values (src.household_id, src.title, src.amount, src.next_due, src.split_method,
            src.issued_by_user_id, src.repeat, src.id)
    on conflict (repeat_parent_id) where repeat_parent_id is not null do nothing
    returning id into new_id;

    if new_id is not null then
      insert into public.bill_shares (bill_id, user_id, amount_owed, is_paid, paid_at)
      select new_id, s.user_id, s.amount_owed, false, null
      from public.bill_shares s where s.bill_id = src.id;
      made := made + 1;
    end if;
    new_id := null;
  end loop;
  return made;
end;
$$;

-- Only the scheduler (postgres) runs this.
revoke all on function public.generate_recurring_bills(date) from public, anon, authenticated;

-- 5. Daily schedule -------------------------------------------------------------------
-- pg_cron must be enabled for the project. If it is not, the migration still
-- applies and the job has to be scheduled by hand.
do $$
begin
  create extension if not exists pg_cron;
  perform cron.unschedule(jobid) from cron.job where jobname = 'generate-recurring-bills';
  perform cron.schedule('generate-recurring-bills', '0 4 * * *', 'select public.generate_recurring_bills()');
exception when others then
  raise notice 'pg_cron not available (%). Schedule generate_recurring_bills() by hand.', sqlerrm;
end $$;
