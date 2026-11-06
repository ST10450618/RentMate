-- RM-0006: proves the RLS rules from migration 0007.
--
-- Run it as the postgres user against a database with every migration
-- applied, for example a local stack:
--   supabase db reset
--   psql "postgresql://postgres:postgres@127.0.0.1:54322/postgres" \
--        -v ON_ERROR_STOP=1 -f supabase/tests/rls_test.sql
-- or paste it into the Supabase SQL editor.
--
-- Everything runs in one transaction that is rolled back at the end, so no
-- test data is left behind. Each check prints "PASS: ..." as a NOTICE; the
-- first failing check raises an exception and stops the script.

begin;

-- Fixtures (as postgres, RLS does not apply) --------------------------------
-- alice and bob share household A, carol lives in household B, dave has no
-- household. alice issued one bill, split between alice and bob.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-0000-0000-00000000000a', 'alice@test.local', '{"full_name":"Alice"}'),
  ('00000000-0000-0000-0000-00000000000b', 'bob@test.local',   '{"full_name":"Bob"}'),
  ('00000000-0000-0000-0000-00000000000c', 'carol@test.local', '{"full_name":"Carol"}'),
  ('00000000-0000-0000-0000-00000000000d', 'dave@test.local',  '{"full_name":"Dave"}');

insert into public.households (id, name, invite_code) values
  ('10000000-0000-0000-0000-00000000000a', 'House A', 'TSTAAA'),
  ('10000000-0000-0000-0000-00000000000b', 'House B', 'TSTBBB');

insert into public.household_members (household_id, user_id) values
  ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'),
  ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b'),
  ('10000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-00000000000c');

insert into public.bills (id, household_id, title, amount, due_date, issued_by_user_id) values
  ('20000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-00000000000a',
   'Electricity', 300.00, now() + interval '7 days', '00000000-0000-0000-0000-00000000000a');

insert into public.bill_shares (id, bill_id, user_id, amount_owed) values
  ('30000000-0000-0000-0000-00000000000a', '20000000-0000-0000-0000-00000000000a',
   '00000000-0000-0000-0000-00000000000a', 150.00),
  ('30000000-0000-0000-0000-00000000000b', '20000000-0000-0000-0000-00000000000a',
   '00000000-0000-0000-0000-00000000000b', 150.00);

-- From here on, act as a signed-in app user. Each block sets the JWT
-- subject the same way PostgREST does for each request; auth.uid() reads it.

set local role authenticated;

-- Joining a household -------------------------------------------------------
do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000d","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000d', true);
  begin
    insert into public.household_members (household_id, user_id)
    values ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000d');
    raise exception 'FAIL: non-member inserted their own membership row without a code';
  exception when insufficient_privilege then
    raise notice 'PASS: non-member cannot join by inserting into household_members';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000d","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000d', true);
  begin
    perform public.join_household('WRONG1');
    raise exception 'FAIL: join_household accepted a wrong invite code';
  exception when raise_exception then
    if sqlerrm like 'FAIL:%' then raise; end if;
    raise notice 'PASS: join_household rejects a wrong invite code';
  end;
  if public.is_household_member('10000000-0000-0000-0000-00000000000a') then
    raise exception 'FAIL: wrong code still created a membership';
  end if;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000d","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000d', true);
  perform public.join_household(' tstaaa ');
  if not public.is_household_member('10000000-0000-0000-0000-00000000000a') then
    raise exception 'FAIL: join_household with the right code did not add the member';
  end if;
  raise notice 'PASS: join_household with the right code adds the member';
end $$;

-- Bills ---------------------------------------------------------------------
do $$
declare n int; amt numeric;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  update public.bills set amount = 1.00 where id = '20000000-0000-0000-0000-00000000000a';
  get diagnostics n = row_count;
  select amount into amt from public.bills where id = '20000000-0000-0000-0000-00000000000a';
  if n <> 0 or amt <> 300.00 then
    raise exception 'FAIL: member edited another member''s bill (rows=%, amount=%)', n, amt;
  end if;
  raise notice 'PASS: member cannot edit another member''s bill';

  delete from public.bills where id = '20000000-0000-0000-0000-00000000000a';
  get diagnostics n = row_count;
  if n <> 0 then
    raise exception 'FAIL: member deleted another member''s bill';
  end if;
  raise notice 'PASS: member cannot delete another member''s bill';
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  begin
    insert into public.bills (household_id, title, amount, due_date, issued_by_user_id)
    values ('10000000-0000-0000-0000-00000000000a', 'Fake', 10, now(),
            '00000000-0000-0000-0000-00000000000a');
    raise exception 'FAIL: member created a bill in someone else''s name';
  exception when insufficient_privilege then
    raise notice 'PASS: member cannot create a bill in someone else''s name';
  end;

  -- RM-0010: direct inserts are gone; bills are created through create_bill.
  begin
    insert into public.bills (household_id, title, amount, due_date, issued_by_user_id)
    values ('10000000-0000-0000-0000-00000000000a', 'Internet', 600, now(),
            '00000000-0000-0000-0000-00000000000b');
    raise exception 'FAIL: member inserted a bill directly, skipping split validation';
  exception when insufficient_privilege then
    raise notice 'PASS: member cannot insert a bill directly (must use create_bill)';
  end;

  perform public.create_bill('10000000-0000-0000-0000-00000000000a', 'Internet', 600, now(),
    'Equal', '[{"user_id":"00000000-0000-0000-0000-00000000000a"},{"user_id":"00000000-0000-0000-0000-00000000000b"}]'::jsonb);
  if not exists (select 1 from public.bills
                 where title = 'Internet' and issued_by_user_id = '00000000-0000-0000-0000-00000000000b') then
    raise exception 'FAIL: create_bill did not issue the bill as the caller';
  end if;
  raise notice 'PASS: member can create their own bill through create_bill';
end $$;

do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  update public.bills set title = 'Electricity (Nov)' where id = '20000000-0000-0000-0000-00000000000a';
  get diagnostics n = row_count;
  if n <> 1 then
    raise exception 'FAIL: issuer could not edit their own bill';
  end if;
  raise notice 'PASS: issuer can edit their own bill';

  begin
    update public.bills set household_id = '10000000-0000-0000-0000-00000000000b'
    where id = '20000000-0000-0000-0000-00000000000a';
    raise exception 'FAIL: issuer moved a bill into a household they are not in';
  exception when insufficient_privilege then
    raise notice 'PASS: issuer cannot move a bill into another household';
  end;
end $$;

do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000c","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000c', true);
  select count(*) into n from public.bills where household_id = '10000000-0000-0000-0000-00000000000a';
  if n <> 0 then
    raise exception 'FAIL: other household can read House A bills';
  end if;
  raise notice 'PASS: other household cannot read the bills';
end $$;

-- Bill shares ---------------------------------------------------------------
do $$
declare n int; paid boolean;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  update public.bill_shares set is_paid = true, amount_owed = 0
  where id = '30000000-0000-0000-0000-00000000000b';
  get diagnostics n = row_count;
  select is_paid into paid from public.bill_shares where id = '30000000-0000-0000-0000-00000000000b';
  if n <> 0 or paid then
    raise exception 'FAIL: member updated a share directly';
  end if;
  raise notice 'PASS: shares cannot be updated directly';

  delete from public.bill_shares where id = '30000000-0000-0000-0000-00000000000a';
  get diagnostics n = row_count;
  if n <> 0 then
    raise exception 'FAIL: member deleted a share directly';
  end if;
  raise notice 'PASS: shares cannot be deleted directly';

  begin
    insert into public.bill_shares (bill_id, user_id, amount_owed)
    values ('20000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b', 0);
    raise exception 'FAIL: non-issuer added a share to someone else''s bill';
  exception when insufficient_privilege then
    raise notice 'PASS: only the bill''s issuer can add shares';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    insert into public.bill_shares (bill_id, user_id, amount_owed)
    values ('20000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000c', 10);
    raise exception 'FAIL: issuer added a share for someone outside the household';
  exception when insufficient_privilege then
    raise notice 'PASS: shares can only be for household members';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000c","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000c', true);
  begin
    perform public.mark_share_paid('30000000-0000-0000-0000-00000000000b');
    raise exception 'FAIL: outsider marked a share paid';
  exception when insufficient_privilege then
    raise notice 'PASS: outsider cannot mark a share paid';
  end;
end $$;

-- RM-0010: only the creditor (the bill's issuer) can mark a share paid.
do $$
declare paid boolean;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  begin
    perform public.mark_share_paid('30000000-0000-0000-0000-00000000000b');
    raise exception 'FAIL: debtor marked their own share paid';
  exception when insufficient_privilege then
    raise notice 'PASS: share owner (debtor) cannot mark their own share paid';
  end;
  select is_paid into paid from public.bill_shares where id = '30000000-0000-0000-0000-00000000000b';
  if paid then
    raise exception 'FAIL: debtor''s share ended up paid';
  end if;
end $$;

do $$
declare paid boolean;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  perform public.mark_share_paid('30000000-0000-0000-0000-00000000000a');
  select is_paid into paid from public.bill_shares where id = '30000000-0000-0000-0000-00000000000a';
  if not paid then
    raise exception 'FAIL: issuer could not mark a share on their bill paid';
  end if;
  raise notice 'PASS: bill issuer can mark a share paid through the RPC';
end $$;

do $$ begin raise notice 'All RLS checks passed.'; end $$;

rollback;
