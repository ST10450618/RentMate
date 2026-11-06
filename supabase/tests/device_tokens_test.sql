-- RM-0021: proves the device token rules from migration 0015.
--
-- Run as the postgres user against a database with every migration applied:
--   supabase db reset
--   docker exec -i supabase_db_rentmate psql -U postgres -d postgres \
--        -v ON_ERROR_STOP=1 -q < supabase/tests/device_tokens_test.sql
-- Everything is rolled back at the end. Each check prints "PASS: ..." as a
-- NOTICE. The first failing check raises an exception and stops the script.

begin;

insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-0000-0000-00000000000a', 'alice@test.local', '{"full_name":"Alice"}'),
  ('00000000-0000-0000-0000-00000000000b', 'bob@test.local',   '{"full_name":"Bob"}');

set local role authenticated;

-- alice registers her phone; registering again only refreshes the row --------
do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
          set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  perform public.register_device_token('token-phone');
  perform public.register_device_token('token-phone');
  if (select count(*) from public.device_tokens) <> 1 then
    raise exception 'FAIL: registering the same token twice made two rows';
  end if;
  raise notice 'PASS: a user registers a token, and repeating it does not duplicate the row';
end $$;

-- bob cannot see alice's token, and a direct insert is refused --------------
do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
          set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  if exists (select 1 from public.device_tokens) then
    raise exception 'FAIL: bob can read alice''s token';
  end if;
  raise notice 'PASS: a user cannot read another user''s tokens';

  begin
    insert into public.device_tokens (token, user_id) values ('forged', '00000000-0000-0000-0000-00000000000a');
    raise exception 'FAIL: a direct insert was allowed';
  exception when insufficient_privilege then
    raise notice 'PASS: direct inserts are refused';
  end;

  -- bob's unregister does not touch alice's token.
  perform public.unregister_device_token('token-phone');
end $$;

reset role;
do $$
begin
  if (select user_id from public.device_tokens where token = 'token-phone') <> '00000000-0000-0000-0000-00000000000a' then
    raise exception 'FAIL: another user removed alice''s token';
  end if;
  raise notice 'PASS: unregister leaves other users'' tokens alone';
end $$;
set local role authenticated;

-- bob signs in on alice's phone: the token moves to bob ---------------------
do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
          set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  perform public.register_device_token('token-phone');
  if (select count(*) from public.device_tokens where token = 'token-phone') <> 1 then
    raise exception 'FAIL: bob cannot see the token he registered';
  end if;
  raise notice 'PASS: a token moves to the account that signed in last';

  perform public.unregister_device_token('token-phone');
  if exists (select 1 from public.device_tokens) then
    raise exception 'FAIL: unregister did not remove the caller''s token';
  end if;
  raise notice 'PASS: a user removes their own token at sign-out';
end $$;

rollback;
