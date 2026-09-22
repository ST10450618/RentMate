-- Ported 1:1 from RentMate.Api/RentMate.Api/Services/*. PostgREST exposes
-- each of these as POST /rest/v1/rpc/<function_name>.

-- US-5: 6-character invite code, excludes ambiguous characters (0/O, 1/I/L).
create function public.create_household(household_name text)
returns public.households
language plpgsql
security definer
set search_path = public
as $$
declare
  alphabet text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
  code text;
  new_household public.households;
begin
  loop
    code := (
      select string_agg(substr(alphabet, floor(random() * length(alphabet) + 1)::int, 1), '')
      from generate_series(1, 6)
    );
    exit when not exists (select 1 from public.households where invite_code = code);
  end loop;

  insert into public.households (name, invite_code)
  values (household_name, code)
  returning * into new_household;

  insert into public.household_members (household_id, user_id)
  values (new_household.id, auth.uid());

  return new_household;
end;
$$;

-- US-5: no membership row is created for an invalid code.
create function public.join_household(code text)
returns public.households
language plpgsql
security definer
set search_path = public
as $$
declare
  target public.households;
begin
  select * into target from public.households where invite_code = upper(trim(code));
  if target.id is null then
    raise exception 'Invalid invite code';
  end if;

  insert into public.household_members (household_id, user_id)
  values (target.id, auth.uid())
  on conflict (household_id, user_id) do nothing;

  return target;
end;
$$;

-- US-9: records a completion and advances the rotation by one position.
create function public.complete_chore(chore_id_in uuid, offline_timestamp_in timestamptz default null)
returns public.chores
language plpgsql
security definer
set search_path = public
as $$
declare
  target public.chores;
  member_count int;
begin
  select * into target from public.chores where id = chore_id_in;
  if target.id is null then
    raise exception 'Chore not found';
  end if;
  if not public.is_household_member(target.household_id) then
    raise exception 'Not a member of this household';
  end if;

  insert into public.chore_completions (chore_id, completed_by_user_id, offline_timestamp, points_awarded)
  values (chore_id_in, auth.uid(), offline_timestamp_in, target.point_value);

  member_count := array_length(target.rotation_order, 1);
  if member_count is null or member_count = 0 then
    return target;
  end if;

  update public.chores
  set current_rotation_index = (current_rotation_index + 1) % member_count
  where id = chore_id_in
  returning * into target;

  return target;
end;
$$;

-- US-9: next N cycles from the chore's current rotation index.
create function public.forecast_chore(chore_id_in uuid, cycles int default 4)
returns table (cycle_number int, assignee_user_id uuid)
language plpgsql
stable
set search_path = public
as $$
declare
  target public.chores;
  member_count int;
begin
  select * into target from public.chores where id = chore_id_in;
  member_count := array_length(target.rotation_order, 1);
  if member_count is null or member_count = 0 then
    return;
  end if;

  for i in 0..(cycles - 1) loop
    cycle_number := i + 1;
    assignee_user_id := target.rotation_order[(target.current_rotation_index + i) % member_count + 1];
    return next;
  end loop;
end;
$$;

-- US-11: greedy debt simplification. Net balance per user: positive means
-- the household owes them, negative means they owe the household.
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
      from_display_name := (select display_name from public.profiles where id = debtors[di]);
      to_user_id := creditors[ci];
      to_display_name := (select display_name from public.profiles where id = creditors[ci]);
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

-- US-11: marks the payer's unpaid shares (on bills issued by the payee)
-- paid, oldest first, up to the settled amount.
create function public.confirm_settlement(household_id_in uuid, to_user_id_in uuid, amount_in numeric)
returns public.settlements
language plpgsql
security definer
set search_path = public
as $$
declare
  new_settlement public.settlements;
  remaining numeric := amount_in;
  share record;
begin
  if not public.is_household_member(household_id_in) then
    raise exception 'Not a member of this household';
  end if;

  insert into public.settlements (household_id, from_user_id, to_user_id, amount)
  values (household_id_in, auth.uid(), to_user_id_in, amount_in)
  returning * into new_settlement;

  for share in
    select bs.id, bs.amount_owed
    from public.bill_shares bs
    join public.bills b on b.id = bs.bill_id
    where bs.user_id = auth.uid()
      and not bs.is_paid
      and b.household_id = household_id_in
      and b.issued_by_user_id = to_user_id_in
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

-- US-12: points per member, this calendar month and total.
create function public.leaderboard(household_id_in uuid)
returns table (user_id uuid, display_name text, month_points bigint, total_points bigint)
language sql
stable
set search_path = public
as $$
  select
    p.id,
    p.display_name,
    coalesce(sum(cc.points_awarded) filter (
      where date_trunc('month', cc.completed_at) = date_trunc('month', now())
    ), 0) as month_points,
    coalesce(sum(cc.points_awarded), 0) as total_points
  from public.household_members hm
  join public.profiles p on p.id = hm.user_id
  left join public.chores c on c.household_id = hm.household_id
  left join public.chore_completions cc on cc.chore_id = c.id and cc.completed_by_user_id = p.id
  where hm.household_id = household_id_in
  group by p.id, p.display_name;
$$;
