-- profiles RLS only let a user read their own row (id = auth.uid()). Every
-- screen that embeds profiles(display_name) for OTHER household members
-- (bill shares, chore assignees, shopping item added_by/purchased_by,
-- dashboard member list) got a null embed back, showing as "?" or a raw
-- UUID in the UI. Same class of bug as migration 0004, different table.

drop policy if exists "read own profile" on public.profiles;
create policy "read household member profiles" on public.profiles
  for select using (
    id = auth.uid()
    or exists (
      select 1
      from public.household_members hm1
      join public.household_members hm2 on hm1.household_id = hm2.household_id
      where hm1.user_id = auth.uid() and hm2.user_id = profiles.id
    )
  );
