-- RM-0010: settle-up is confirmed by the creditor, and bills support equal,
-- percentage and exact-amount splits validated on the server.
--
-- 1. confirm_settlement. The old version recorded a payment FROM the caller,
--    so the debtor could confirm their own payment. The caller is now the
--    creditor: they confirm that from_user_id_in paid them.
-- 2. create_bill. Bills and shares are created together by one function that
--    checks the split adds up, so the client can no longer insert shares that
--    do not match the bill. The direct INSERT policies from 0007 are dropped.

-- 1. confirm_settlement -------------------------------------------------------
drop function if exists public.confirm_settlement(uuid, uuid, numeric);

create function public.confirm_settlement(household_id_in uuid, from_user_id_in uuid, amount_in numeric)
returns public.settlements
language plpgsql
security definer
set search_path = public
as $$
declare
  caller uuid := auth.uid();
  new_settlement public.settlements;
  remaining numeric := amount_in;
  share record;
begin
  if caller is null or not public.is_household_member(household_id_in) then
    raise exception 'Not a member of this household' using errcode = '42501';
  end if;
  if from_user_id_in is null or from_user_id_in = caller then
    raise exception 'Choose the person who paid you';
  end if;
  if not exists (
    select 1 from public.household_members
    where household_id = household_id_in and user_id = from_user_id_in
  ) then
    raise exception 'That person is not in this household';
  end if;
  if amount_in is null or amount_in <= 0 then
    raise exception 'Amount must be greater than 0';
  end if;

  -- The caller is always the creditor (to_user_id).
  insert into public.settlements (household_id, from_user_id, to_user_id, amount)
  values (household_id_in, from_user_id_in, caller, amount_in)
  returning * into new_settlement;

  -- Mark the payer's unpaid shares on bills the caller issued as paid,
  -- oldest due first, up to the settled amount.
  for share in
    select bs.id, bs.amount_owed
    from public.bill_shares bs
    join public.bills b on b.id = bs.bill_id
    where bs.user_id = from_user_id_in
      and not bs.is_paid
      and b.household_id = household_id_in
      and b.issued_by_user_id = caller
    order by b.due_date asc
  loop
    if remaining <= 0 then
      exit;
    end if;
    if share.amount_owed <= remaining then
      update public.bill_shares set is_paid = true, paid_at = now() where id = share.id;
      remaining := remaining - share.amount_owed;
    end if;
  end loop;

  return new_settlement;
end;
$$;

revoke all on function public.confirm_settlement(uuid, uuid, numeric) from public, anon;
grant execute on function public.confirm_settlement(uuid, uuid, numeric) to authenticated;

-- 2. create_bill --------------------------------------------------------------
-- shares_in is a JSON array of {"user_id": "...", "value": n}.
--   Equal:       value is ignored; the amount is split evenly, the last share
--                absorbs the rounding remainder.
--   Percentage:  value is a percentage; they must add up to 100 (within 0.01).
--                The last share absorbs the rounding remainder.
--   ExactAmount: value is a rand amount; they must add up to the bill amount.
-- All maths is in whole cents.
create function public.create_bill(
  household_id_in uuid,
  title_in text,
  amount_in numeric,
  due_date_in timestamptz,
  split_method_in text,
  shares_in jsonb
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

  insert into public.bills (household_id, title, amount, due_date, split_method, issued_by_user_id)
  values (household_id_in, btrim(title_in), total_cents::numeric / 100, due_date_in, split_method_in, caller)
  returning * into new_bill;

  insert into public.bill_shares (bill_id, user_id, amount_owed)
  select new_bill.id, ids[k], cents[k]::numeric / 100
  from generate_series(1, n) k;

  return new_bill;
end;
$$;

revoke all on function public.create_bill(uuid, text, numeric, timestamptz, text, jsonb) from public, anon;
grant execute on function public.create_bill(uuid, text, numeric, timestamptz, text, jsonb) to authenticated;

-- With create_bill in place, direct inserts would bypass the split checks.
drop policy if exists "bills: members add their own" on public.bills;
drop policy if exists "bill_shares: issuer adds" on public.bill_shares;
