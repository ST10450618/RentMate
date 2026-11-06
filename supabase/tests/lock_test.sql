-- RM-0048: proves migration 0013 locks chores and households against direct edits.
--
-- Run as the postgres user against a database with every migration applied:
--   psql "postgresql://postgres:postgres@127.0.0.1:54322/postgres" \
--        -v ON_ERROR_STOP=1 -f supabase/tests/lock_test.sql
-- Everything runs in one transaction that is rolled back at the end. Each
-- check prints "PASS: ..." as a NOTICE; the first failure raises and stops.

begin;

-- Fixtures (as postgres): alice and bob share House A, carol is an outsider.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-0000-0000-00000000000a', 'alice@test.local', '{"full_name":"Alice"}'),
  ('00000000-0000-0000-0000-00000000000b', 'bob@test.local',   '{"full_name":"Bob"}'),
  ('00000000-0000-0000-0000-00000000000c', 'carol@test.local', '{"full_name":"Carol"}');

insert into public.households (id, name, invite_code) values
  ('10000000-0000-0000-0000-00000000000a', 'House A', 'TSTAAA');
insert into public.household_members (household_id, user_id) values
  ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'),
  ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b');
insert into public.chores (id, household_id, title, recurrence, point_value, rotation_order, current_rotation_index) values
  ('40000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-00000000000a', 'Dishes', 'Weekly', 5, array['00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b']::uuid[], 0);

set local role authenticated;

-- Blocked: chores columns ----------------------------------------------------------
do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  begin
    update public.chores set rotation_order = array['00000000-0000-0000-0000-00000000000b']::uuid[] where id = '40000000-0000-0000-0000-00000000000a';
    get diagnostics n = row_count;
    raise exception 'FAIL: member changing chores.rotation_order was accepted';
  exception when insufficient_privilege then
    raise notice 'PASS: member changing chores.rotation_order is rejected';
  end;
end $$;

do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  begin
    update public.chores set current_rotation_index = 1 where id = '40000000-0000-0000-0000-00000000000a';
    get diagnostics n = row_count;
    raise exception 'FAIL: member changing chores.current_rotation_index was accepted';
  exception when insufficient_privilege then
    raise notice 'PASS: member changing chores.current_rotation_index is rejected';
  end;
end $$;

do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  begin
    update public.chores set point_value = 1000 where id = '40000000-0000-0000-0000-00000000000a';
    get diagnostics n = row_count;
    raise exception 'FAIL: member changing chores.point_value was accepted';
  exception when insufficient_privilege then
    raise notice 'PASS: member changing chores.point_value is rejected';
  end;
end $$;

-- Blocked: households columns -------------------------------------------------------
do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  begin
    update public.households set invite_code = 'HACKED' where id = '10000000-0000-0000-0000-00000000000a';
    get diagnostics n = row_count;
    raise exception 'FAIL: member changing households.invite_code was accepted';
  exception when insufficient_privilege then
    raise notice 'PASS: member changing households.invite_code is rejected';
  end;
end $$;

do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  begin
    insert into public.households (name, invite_code) values ('Mine', 'ZZZZZZ');
    get diagnostics n = row_count;
    raise exception 'FAIL: direct insert into households was accepted';
  exception when insufficient_privilege then
    raise notice 'PASS: direct insert into households is rejected';
  end;
end $$;

-- Blocked: bad chore inserts ---------------------------------------------------------
do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    insert into public.chores (household_id, title, recurrence, point_value, rotation_order) values ('10000000-0000-0000-0000-00000000000a', 'X', 'Weekly', 5, array['00000000-0000-0000-0000-00000000000c']::uuid[]);
    raise exception 'FAIL: insert with a non-member in rotation_order was accepted';
  exception when insufficient_privilege then
    raise notice 'PASS: insert with a non-member in rotation_order is rejected';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    insert into public.chores (household_id, title, recurrence, point_value, rotation_order) values ('10000000-0000-0000-0000-00000000000a', 'X', 'Weekly', 100000, array['00000000-0000-0000-0000-00000000000a']::uuid[]);
    raise exception 'FAIL: insert with an absurd point_value was accepted';
  exception when insufficient_privilege then
    raise notice 'PASS: insert with an absurd point_value is rejected';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    insert into public.chores (household_id, title, recurrence, point_value, rotation_order) values ('10000000-0000-0000-0000-00000000000a', 'X', 'Weekly', 5, '{}'::uuid[]);
    raise exception 'FAIL: insert with an empty rotation_order was accepted';
  exception when insufficient_privilege then
    raise notice 'PASS: insert with an empty rotation_order is rejected';
  end;
end $$;

do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    insert into public.chores (household_id, title, point_value, rotation_order, current_rotation_index) values ('10000000-0000-0000-0000-00000000000a', 'X', 5, array['00000000-0000-0000-0000-00000000000a']::uuid[], 1);
    get diagnostics n = row_count;
    raise exception 'FAIL: insert with a nonzero current_rotation_index was accepted';
  exception when insufficient_privilege then
    raise notice 'PASS: insert with a nonzero current_rotation_index is rejected';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000c","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000c', true);
  begin
    insert into public.chores (household_id, title, point_value, rotation_order) values ('10000000-0000-0000-0000-00000000000a', 'X', 5, array['00000000-0000-0000-0000-00000000000c']::uuid[]);
    raise exception 'FAIL: outsider inserted a chore into the household';
  exception when insufficient_privilege then
    raise notice 'PASS: outsider cannot insert a chore into the household';
  end;
end $$;

-- Unchanged data check ---------------------------------------------------------------
do $$
declare r public.chores; code text;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  select * into r from public.chores where id = '40000000-0000-0000-0000-00000000000a';
  if r.point_value <> 5 or r.current_rotation_index <> 0 or r.rotation_order <> array['00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b']::uuid[] then
    raise exception 'FAIL: chore was modified by a blocked write';
  end if;
  select invite_code into code from public.households where id = '10000000-0000-0000-0000-00000000000a';
  if code <> 'TSTAAA' then raise exception 'FAIL: invite_code changed'; end if;
  raise notice 'PASS: blocked writes left chore and invite_code untouched';
end $$;

-- Still working ------------------------------------------------------------------------
do $$
declare r public.chores;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  insert into public.chores (household_id, title, recurrence, point_value, rotation_order)
  values ('10000000-0000-0000-0000-00000000000a', 'Bins', 'Weekly', 10, array['00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b']::uuid[]) returning * into r;
  if r.current_rotation_index <> 0 then raise exception 'FAIL: new chore index %', r.current_rotation_index; end if;
  raise notice 'PASS: member can add a chore with valid values (as Add Chore does)';
  update public.chores set title = 'Bins out' where id = r.id;
  raise notice 'PASS: member can still edit a chore title';
  delete from public.chores where id = r.id;
  raise notice 'PASS: member can still delete a chore';
end $$;

do $$
declare h public.households; pts int; idx int; n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  perform public.complete_chore('40000000-0000-0000-0000-00000000000a');
  select points_awarded into pts from public.chore_completions where chore_id = '40000000-0000-0000-0000-00000000000a';
  select current_rotation_index into idx from public.chores where id = '40000000-0000-0000-0000-00000000000a';
  if pts <> 5 or idx <> 1 then raise exception 'FAIL: complete_chore gave % pts, index %', pts, idx; end if;
  raise notice 'PASS: complete_chore still awards points and advances the rotation';

  update public.households set hide_leaderboard = true where id = '10000000-0000-0000-0000-00000000000a';
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL: hide_leaderboard update matched % rows', n; end if;
  if not (select hide_leaderboard from public.households where id = '10000000-0000-0000-0000-00000000000a') then
    raise exception 'FAIL: hide_leaderboard not saved';
  end if;
  raise notice 'PASS: hide_leaderboard toggle still works';

  update public.households set name = 'Renamed', landlord_email = 'l@test.local' where id = '10000000-0000-0000-0000-00000000000a';
  raise notice 'PASS: household name and landlord_email still editable';

  h := public.create_household('House New');
  if h.invite_code is null then raise exception 'FAIL: create_household returned no invite code'; end if;
  raise notice 'PASS: create_household still works';
end $$;

do $$
declare h public.households;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000c","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000c', true);
  h := public.join_household('TSTAAA');
  if h.id <> '10000000-0000-0000-0000-00000000000a' then raise exception 'FAIL: join_household returned wrong household'; end if;
  raise notice 'PASS: join_household still works';
end $$;

reset role;
rollback;
