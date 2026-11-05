-- RM-0008: proves the chore rules from migration 0009.
--
-- Run as the postgres user against a database with every migration applied:
--   psql "postgresql://postgres:postgres@127.0.0.1:54322/postgres" \
--        -v ON_ERROR_STOP=1 -f supabase/tests/chores_test.sql
-- Everything runs in one transaction that is rolled back at the end. Each
-- check prints "PASS: ..." as a NOTICE; the first failure raises and stops.

begin;

-- Fixtures (as postgres): alice and bob share House A, carol is an outsider.
-- "Dishes" (weekly, 5 pts) rotates alice -> bob and starts on alice.
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
  ('40000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-00000000000a', 'Dishes', 'Weekly', 5,
   array['00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b']::uuid[], 0);

set local role authenticated;

-- Non-assignee ---------------------------------------------------------------
do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  begin
    perform public.complete_chore('40000000-0000-0000-0000-00000000000a');
    raise exception 'FAIL: non-assignee completed the chore';
  exception when insufficient_privilege then
    raise notice 'PASS: non-assignee cannot complete the chore';
  end;
  select count(*) into n from public.chore_completions;
  if n <> 0 then raise exception 'FAIL: rejected completion left % rows', n; end if;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000c","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000c', true);
  begin
    perform public.complete_chore('40000000-0000-0000-0000-00000000000a');
    raise exception 'FAIL: outsider completed the chore';
  exception when insufficient_privilege then
    raise notice 'PASS: outsider cannot complete the chore';
  end;
end $$;

-- Assignee ---------------------------------------------------------------------
do $$
declare pts int; idx int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  perform public.complete_chore('40000000-0000-0000-0000-00000000000a');
  select points_awarded into pts from public.chore_completions
   where chore_id = '40000000-0000-0000-0000-00000000000a' and completed_by_user_id = '00000000-0000-0000-0000-00000000000a';
  if pts is distinct from 5 then
    raise exception 'FAIL: expected 5 points, got %', pts;
  end if;
  raise notice 'PASS: assignee completes the chore and earns point_value';
  select current_rotation_index into idx from public.chores where id = '40000000-0000-0000-0000-00000000000a';
  if idx <> 1 then raise exception 'FAIL: rotation did not advance (index %)', idx; end if;
  raise notice 'PASS: rotation advanced to the next housemate';
end $$;

-- Duplicate in the same cycle: bob is now the assignee but the week is done.
do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  begin
    perform public.complete_chore('40000000-0000-0000-0000-00000000000a');
    raise exception 'FAIL: second completion in the same cycle was accepted';
  exception when unique_violation then
    raise notice 'PASS: duplicate completion in the same cycle is rejected';
  end;
  select count(*) into n from public.chore_completions;
  if n <> 1 then raise exception 'FAIL: expected 1 completion, found %', n; end if;
end $$;

-- Next cycle: an old completion no longer blocks the new week.
do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  -- members can read completions
  select count(*) into n from public.chore_completions;
  if n <> 1 then raise exception 'FAIL: member cannot read completions (saw %)', n; end if;
  raise notice 'PASS: members can read completions';
end $$;

reset role;
update public.chore_completions set completed_at = completed_at - interval '8 days';
set local role authenticated;

do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  perform public.complete_chore('40000000-0000-0000-0000-00000000000a');
  select count(*) into n from public.chore_completions;
  if n <> 2 then raise exception 'FAIL: new-cycle completion missing (count %)', n; end if;
  raise notice 'PASS: a completion from a previous cycle does not block the new cycle';
end $$;

-- Direct writes -----------------------------------------------------------------
do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    insert into public.chore_completions (chore_id, completed_by_user_id, points_awarded)
    values ('40000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a', 1000);
    raise exception 'FAIL: member inserted a completion directly';
  exception when insufficient_privilege then
    raise notice 'PASS: direct insert into chore_completions is rejected';
  end;
  update public.chore_completions set points_awarded = 1000;
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: member updated completions directly'; end if;
  delete from public.chore_completions;
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: member deleted completions directly'; end if;
  raise notice 'PASS: completions cannot be updated or deleted directly';
end $$;

-- hide_leaderboard ----------------------------------------------------------------
do $$
declare hidden boolean;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  update public.households set hide_leaderboard = true where id = '10000000-0000-0000-0000-00000000000a';
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  select hide_leaderboard into hidden from public.households where id = '10000000-0000-0000-0000-00000000000a';
  if hidden is distinct from true then
    raise exception 'FAIL: bob does not see alice''s hide_leaderboard change';
  end if;
  raise notice 'PASS: hiding the leaderboard on one phone hides it for the other member';
end $$;

do $$
declare n int; hidden boolean;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000c","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000c', true);
  update public.households set hide_leaderboard = false where id = '10000000-0000-0000-0000-00000000000a';
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: non-member changed hide_leaderboard'; end if;
  raise notice 'PASS: non-member cannot change hide_leaderboard';
end $$;

do $$ begin raise notice 'All chore checks passed.'; end $$;

rollback;
