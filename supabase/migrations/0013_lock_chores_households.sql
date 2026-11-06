-- RM-0048: close the direct-edit loophole on chores and households.
--
-- Until now "chores: members only" (FOR ALL) and "update your household" let any
-- member rewrite rotation_order, current_rotation_index, point_value and
-- invite_code with a plain PostgREST call. RLS cannot restrict columns, so this
-- uses column privileges: authenticated keeps UPDATE/INSERT only on the columns
-- the app genuinely writes. SECURITY DEFINER functions (complete_chore,
-- create_household, join_household) run as the function owner and are unaffected.

-- 1. households -----------------------------------------------------------------
-- Creation goes through create_household, so no direct insert is needed.
drop policy if exists "create a household" on public.households;
revoke insert on public.households from public, anon, authenticated;

-- Members may edit only name, landlord contact and the leaderboard toggle.
-- invite_code, id and created_at are never writable directly.
revoke update on public.households from public, anon, authenticated;
grant update (name, landlord_name, landlord_email, hide_leaderboard)
  on public.households to authenticated;

-- 2. chores ---------------------------------------------------------------------
-- Only title and recurrence stay editable. rotation_order, current_rotation_index
-- and point_value can be set on insert only (validated below) and are afterwards
-- changed solely by SECURITY DEFINER code.
revoke update on public.chores from public, anon, authenticated;
grant update (title, recurrence) on public.chores to authenticated;

-- current_rotation_index, id and created_at are not insertable: they take their
-- defaults (0, uuid, now()).
revoke insert on public.chores from public, anon, authenticated;
grant insert (household_id, title, recurrence, point_value, rotation_order)
  on public.chores to authenticated;

-- Every rotation_order entry must be a member of the chore's household.
create function public.is_valid_rotation(household_id_in uuid, rotation uuid[])
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(array_length(rotation, 1), 0) between 1 and 50
     and not exists (
       select 1 from unnest(rotation) as r(uid)
       where r.uid is null
          or not exists (
            select 1 from public.household_members m
            where m.household_id = household_id_in and m.user_id = r.uid
          )
     );
$$;

revoke all on function public.is_valid_rotation(uuid, uuid[]) from public, anon;
grant execute on function public.is_valid_rotation(uuid, uuid[]) to authenticated;

-- Split the FOR ALL policy so inserts can be checked (point range matches the
-- app's Limits.POINTS_MIN..POINTS_MAX).
drop policy if exists "chores: members only" on public.chores;

create policy "chores: members read" on public.chores
  for select using (public.is_household_member(household_id));

create policy "chores: members add" on public.chores
  for insert with check (
    public.is_household_member(household_id)
    and point_value between 1 and 100
    and public.is_valid_rotation(household_id, rotation_order)
  );

create policy "chores: members edit" on public.chores
  for update using (public.is_household_member(household_id))
  with check (public.is_household_member(household_id));

create policy "chores: members delete" on public.chores
  for delete using (public.is_household_member(household_id));
