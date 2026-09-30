-- The original policy only let a user see their OWN membership row
-- (user_id = auth.uid()), so any query for the full roster - bill splits,
-- chore rotation order, the Dashboard's member list - only ever returned
-- one row, regardless of how many people had actually joined.
drop policy if exists "read own memberships" on public.household_members;

create policy "read household members" on public.household_members
  for select using (public.is_household_member(household_id));
