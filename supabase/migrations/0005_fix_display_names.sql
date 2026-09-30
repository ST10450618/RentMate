-- Two related fixes:
--
-- 1. handle_new_user only checked raw_user_meta_data->>'full_name', but the
--    native Google ID-token sign-in flow (signInWith(IDToken), not the OAuth
--    redirect flow) populates the standard OIDC claim key 'name' instead.
--    That left display_name null for at least one account.
--
-- 2. settle_up looked up display_name with a plain subquery, which returns
--    null if the profile is missing or its name is null - crashing the
--    Kotlin client's non-nullable decode. Coalesced to a fallback.

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, email, display_name)
  values (
    new.id,
    new.email,
    coalesce(
      new.raw_user_meta_data ->> 'full_name',
      new.raw_user_meta_data ->> 'name',
      new.email
    )
  );
  return new;
end;
$$;

drop function if exists public.settle_up(uuid);
create function public.settle_up(household_id_in uuid)
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
  tolerance numeric := 0.01;
  balances record;
  creditors uuid[] := '{}';
  creditor_amounts numeric[] := '{}';
  debtors uuid[] := '{}';
  debtor_amounts numeric[] := '{}';
  ci int := 1;
  di int := 1;
  transfer_amount numeric;
begin
  if not public.is_household_member(household_id_in) then
    raise exception 'Not a member of this household';
  end if;

  for balances in
    select user_id, sum(net) as net_amount from (
      select bs.user_id as user_id, -bs.amount_owed as net
      from public.bill_shares bs
      join public.bills b on b.id = bs.bill_id
      where b.household_id = household_id_in and not bs.is_paid and bs.user_id <> b.issued_by_user_id
      union all
      select b.issued_by_user_id as user_id, bs.amount_owed as net
      from public.bill_shares bs
      join public.bills b on b.id = bs.bill_id
      where b.household_id = household_id_in and not bs.is_paid and bs.user_id <> b.issued_by_user_id
    ) t
    group by user_id
    having abs(sum(net)) > tolerance
  loop
    if balances.net_amount > 0 then
      creditors := array_append(creditors, balances.user_id);
      creditor_amounts := array_append(creditor_amounts, balances.net_amount);
    else
      debtors := array_append(debtors, balances.user_id);
      debtor_amounts := array_append(debtor_amounts, -balances.net_amount);
    end if;
  end loop;

  while ci <= array_length(creditors, 1) and di <= array_length(debtors, 1) loop
    transfer_amount := round(least(creditor_amounts[ci], debtor_amounts[di]), 2);

    if transfer_amount > 0 then
      from_user_id := debtors[di];
      from_display_name := coalesce((select display_name from public.profiles where id = debtors[di]), 'Unknown');
      to_user_id := creditors[ci];
      to_display_name := coalesce((select display_name from public.profiles where id = creditors[ci]), 'Unknown');
      amount := transfer_amount;
      return next;
    end if;

    creditor_amounts[ci] := creditor_amounts[ci] - transfer_amount;
    debtor_amounts[di] := debtor_amounts[di] - transfer_amount;

    if creditor_amounts[ci] <= tolerance then ci := ci + 1; end if;
    if debtor_amounts[di] <= tolerance then di := di + 1; end if;
  end loop;
end;
$$;

-- Backfill any existing profiles that already ended up with a null name.
update public.profiles p
set display_name = coalesce(u.raw_user_meta_data ->> 'full_name', u.raw_user_meta_data ->> 'name', p.email)
from auth.users u
where u.id = p.id and p.display_name is null;
