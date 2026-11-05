-- RM-0008: chores can only be completed by the current assignee, once per
-- cycle, and points are awarded only inside complete_chore().
--
-- Error contract for complete_chore (the app maps these to friendly text):
--   42501  not the current assignee / not a household member
--   23505  the chore was already completed in the current cycle
-- (P0002 "Chore not found" is unchanged in spirit: no_data_found.)
--
-- Cycle definition: Weekly = since date_trunc('week', now()) (Monday 00:00),
-- Monthly = since date_trunc('month', now()), Once = any completion ever.
-- The current assignee is rotation_order[current_rotation_index + 1]
-- (Postgres arrays are 1-based). An empty rotation means any member may
-- complete the chore.

create or replace function public.complete_chore(chore_id_in uuid, offline_timestamp_in timestamptz default null)
returns public.chores
language plpgsql
security definer
set search_path = public
as $$
declare
  target public.chores;
  member_count int;
  cycle_start timestamptz;
begin
  -- Lock the row so two simultaneous completions cannot both pass the checks.
  select * into target from public.chores where id = chore_id_in for update;
  if target.id is null then
    raise exception 'Chore not found' using errcode = 'P0002';
  end if;
  if not public.is_household_member(target.household_id) then
    raise exception 'Not a member of this household' using errcode = '42501';
  end if;

  member_count := coalesce(array_length(target.rotation_order, 1), 0);
  if member_count > 0
     and target.rotation_order[(target.current_rotation_index % member_count) + 1] is distinct from auth.uid() then
    raise exception 'Only the assigned housemate can complete this chore' using errcode = '42501';
  end if;

  cycle_start := case target.recurrence
    when 'Weekly' then date_trunc('week', now())
    when 'Monthly' then date_trunc('month', now())
    else '-infinity'::timestamptz
  end;
  if exists (
    select 1 from public.chore_completions
    where chore_id = chore_id_in and completed_at >= cycle_start
  ) then
    raise exception 'This chore was already completed this cycle' using errcode = '23505';
  end if;

  insert into public.chore_completions (chore_id, completed_by_user_id, offline_timestamp, points_awarded)
  values (chore_id_in, auth.uid(), offline_timestamp_in, target.point_value);

  if member_count = 0 then
    return target;
  end if;

  update public.chores
  set current_rotation_index = (current_rotation_index + 1) % member_count
  where id = chore_id_in
  returning * into target;

  return target;
end;
$$;

revoke all on function public.complete_chore(uuid, timestamptz) from public, anon;
grant execute on function public.complete_chore(uuid, timestamptz) to authenticated;

-- Completions are written only by complete_chore (SECURITY DEFINER bypasses
-- RLS); members may read them but never insert, update or delete directly.
drop policy if exists "chore_completions: members only" on public.chore_completions;
create policy "chore_completions: members read" on public.chore_completions
  for select using (public.is_household_member((select household_id from public.chores where id = chore_id)));

-- Household-wide leaderboard visibility, replacing the per-phone setting.
-- Any member may change it through the existing "update your household" policy.
alter table public.households add column hide_leaderboard boolean not null default false;
