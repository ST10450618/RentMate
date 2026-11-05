-- RM-0009: proves the maintenance status rules from migration 0010.
--
-- Run as the postgres user against a database with every migration applied:
--   supabase db reset
--   psql "postgresql://postgres:postgres@127.0.0.1:54322/postgres" \
--        -v ON_ERROR_STOP=1 -f supabase/tests/maintenance_test.sql
-- Everything is rolled back at the end. Each check prints "PASS: ..." as a
-- NOTICE; the first failing check raises an exception and stops the script.

begin;

-- alice and bob share household A. alice logs a leaking tap.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-0000-0000-00000000000a', 'alice@test.local', '{"full_name":"Alice"}'),
  ('00000000-0000-0000-0000-00000000000b', 'bob@test.local',   '{"full_name":"Bob"}');

insert into public.households (id, name, invite_code, landlord_email) values
  ('10000000-0000-0000-0000-00000000000a', 'House A', 'TSTAAA', 'landlord@test.local');

insert into public.household_members (household_id, user_id) values
  ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'),
  ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b');

insert into public.maintenance_requests (id, household_id, raised_by_user_id, title, description, category, urgency) values
  ('40000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-00000000000a',
   '00000000-0000-0000-0000-00000000000a', 'Leaking tap', 'Kitchen tap drips', 'Plumbing', 'Medium');

set local role authenticated;

-- bob cannot change alice's request directly or through the RPC -------------
do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  update public.maintenance_requests set status = 'Resolved'
  where id = '40000000-0000-0000-0000-00000000000a';
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: a housemate updated a request directly'; end if;
  raise notice 'PASS: direct status updates are refused';

  begin
    perform public.advance_maintenance_status('40000000-0000-0000-0000-00000000000a');
    raise exception 'FAIL: a housemate advanced someone else''s open request';
  exception when insufficient_privilege then
    raise notice 'PASS: only the person who logged a request can send it on its own';
  end;
end $$;

-- bob sends every open request to the landlord, so bob becomes the sender ---
do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  if public.send_to_landlord('10000000-0000-0000-0000-00000000000a') <> 1 then
    raise exception 'FAIL: send_to_landlord did not send the open request';
  end if;
  if (select sent_by_user_id from public.maintenance_requests where id = '40000000-0000-0000-0000-00000000000a')
     is distinct from '00000000-0000-0000-0000-00000000000b' then
    raise exception 'FAIL: send_to_landlord did not record the sender';
  end if;
  raise notice 'PASS: send_to_landlord records who sent the request';

  if (public.advance_maintenance_status('40000000-0000-0000-0000-00000000000a')).status <> 'Acknowledged' then
    raise exception 'FAIL: the sender could not record the landlord''s response';
  end if;
  raise notice 'PASS: the sender records the landlord''s response';
end $$;

-- alice logged it, so alice may still record the next response --------------
do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  if (public.advance_maintenance_status('40000000-0000-0000-0000-00000000000a')).status <> 'Resolved' then
    raise exception 'FAIL: the person who logged the request could not mark it resolved';
  end if;
  raise notice 'PASS: the person who logged the request can mark it resolved';
end $$;

rollback;
