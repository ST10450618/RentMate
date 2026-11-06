-- RM-0030: make settle_up deterministic so the Kotlin DebtSimplifier can match it.
--
-- Before: balances were read in unsorted GROUP BY order, so the list of
-- transfers could differ between runs. Now:
--   * balances are summed in integer cents (no numeric rounding drift);
--   * creditors and debtors are each ordered by amount descending, then
--     user_id ascending;
--   * the largest debtor is matched to the largest creditor, moving to the next
--     one on either side as soon as that side reaches zero (the lists are not
--     re-sorted after a partial payment);
--   * users whose balance is exactly zero are skipped.
-- Signature, return columns, security and grants are unchanged (create or
-- replace keeps the existing grants). Shared fixtures:
-- supabase/tests/fixtures/settle_up_fixtures.json.

create or replace function public.settle_up(household_id_in uuid)
returns table (
  from_user_id uuid,
  from_display_name text,
  to_user_id uuid,
  to_display_name text,
  amount numeric
)
language plpgsql
stable
set search_path = public
as $$
declare
  creditors uuid[] := '{}';
  creditor_cents bigint[] := '{}';
  debtors uuid[] := '{}';
  debtor_cents bigint[] := '{}';
  r record;
  ci int := 1;
  di int := 1;
  transfer_cents bigint;
begin
  if not public.is_household_member(household_id_in) then
    raise exception 'Not a member of this household';
  end if;

  for r in
    select t.user_id, sum(t.net)::bigint as net_cents from (
      select bs.user_id as user_id, -round(bs.amount_owed * 100)::bigint as net
      from public.bill_shares bs
      join public.bills b on b.id = bs.bill_id
      where b.household_id = household_id_in and not bs.is_paid and bs.user_id <> b.issued_by_user_id
      union all
      select b.issued_by_user_id as user_id, round(bs.amount_owed * 100)::bigint as net
      from public.bill_shares bs
      join public.bills b on b.id = bs.bill_id
      where b.household_id = household_id_in and not bs.is_paid and bs.user_id <> b.issued_by_user_id
    ) t
    group by t.user_id
    having sum(t.net) > 0
    order by sum(t.net) desc, t.user_id asc
  loop
    creditors := array_append(creditors, r.user_id);
    creditor_cents := array_append(creditor_cents, r.net_cents);
  end loop;

  for r in
    select t.user_id, (-sum(t.net))::bigint as net_cents from (
      select bs.user_id as user_id, -round(bs.amount_owed * 100)::bigint as net
      from public.bill_shares bs
      join public.bills b on b.id = bs.bill_id
      where b.household_id = household_id_in and not bs.is_paid and bs.user_id <> b.issued_by_user_id
      union all
      select b.issued_by_user_id as user_id, round(bs.amount_owed * 100)::bigint as net
      from public.bill_shares bs
      join public.bills b on b.id = bs.bill_id
      where b.household_id = household_id_in and not bs.is_paid and bs.user_id <> b.issued_by_user_id
    ) t
    group by t.user_id
    having sum(t.net) < 0
    order by -sum(t.net) desc, t.user_id asc
  loop
    debtors := array_append(debtors, r.user_id);
    debtor_cents := array_append(debtor_cents, r.net_cents);
  end loop;

  while ci <= coalesce(array_length(creditors, 1), 0) and di <= coalesce(array_length(debtors, 1), 0) loop
    transfer_cents := least(creditor_cents[ci], debtor_cents[di]);

    from_user_id := debtors[di];
    from_display_name := coalesce((select display_name from public.profiles where id = debtors[di]), 'Unknown');
    to_user_id := creditors[ci];
    to_display_name := coalesce((select display_name from public.profiles where id = creditors[ci]), 'Unknown');
    amount := round(transfer_cents / 100.0, 2);
    return next;

    creditor_cents[ci] := creditor_cents[ci] - transfer_cents;
    debtor_cents[di] := debtor_cents[di] - transfer_cents;

    if creditor_cents[ci] = 0 then ci := ci + 1; end if;
    if debtor_cents[di] = 0 then di := di + 1; end if;
  end loop;
end;
$$;
