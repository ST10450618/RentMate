-- RM-0027: proves the attachment storage rules from migration 0011.
--
-- Run as the postgres user against a database with every migration applied:
--   supabase db reset
--   docker exec -i supabase_db_rentmate psql -U postgres -d postgres \
--        -v ON_ERROR_STOP=1 -q < supabase/tests/attachments_test.sql
-- Everything is rolled back at the end. Each check prints "PASS: ..." as a
-- NOTICE; the first failing check raises an exception and stops the script.
--
-- Storage's HTTP API runs each request as the "authenticated" role with the
-- caller's JWT claims, then answers 403 when the RLS check on
-- storage.objects fails. These checks do the same thing in SQL.

begin;

-- Fixtures (as postgres, RLS does not apply) --------------------------------
-- alice and bob share household A, carol lives in household B.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-0000-0000-00000000000a', 'alice@test.local', '{"full_name":"Alice"}'),
  ('00000000-0000-0000-0000-00000000000b', 'bob@test.local',   '{"full_name":"Bob"}'),
  ('00000000-0000-0000-0000-00000000000c', 'carol@test.local', '{"full_name":"Carol"}');

insert into public.households (id, name, invite_code) values
  ('10000000-0000-0000-0000-00000000000a', 'House A', 'TSTAAA'),
  ('10000000-0000-0000-0000-00000000000b', 'House B', 'TSTBBB');

insert into public.household_members (household_id, user_id) values
  ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'),
  ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b'),
  ('10000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-00000000000c');

insert into public.maintenance_requests (id, household_id, raised_by_user_id, title, description, category, urgency) values
  ('40000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-00000000000a',
   '00000000-0000-0000-0000-00000000000a', 'Leaking tap', 'Kitchen tap drips', 'Plumbing', 'Medium'),
  ('40000000-0000-0000-0000-00000000000c', '10000000-0000-0000-0000-00000000000b',
   '00000000-0000-0000-0000-00000000000c', 'Broken window', 'Lounge window', 'Structural', 'High');

insert into public.bills (id, household_id, title, amount, due_date, issued_by_user_id) values
  ('20000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-00000000000a',
   'Electricity', 300.00, now() + interval '7 days', '00000000-0000-0000-0000-00000000000a');

-- The bucket exists and is private ------------------------------------------
do $$
begin
  if not exists (select 1 from storage.buckets where id = 'attachments' and public = false) then
    raise exception 'FAIL: private bucket "attachments" is missing';
  end if;
  raise notice 'PASS: private bucket "attachments" exists';
end $$;

-- The one-parent rule holds even without RLS (as postgres) ------------------
do $$
begin
  begin
    insert into public.attachments (household_id, uploaded_by, storage_path)
    values ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a',
            '10000000-0000-0000-0000-00000000000a/bill/none.jpg');
    raise exception 'FAIL: an attachment with no parent was allowed';
  exception when check_violation then
    raise notice 'PASS: the table refuses an attachment with no parent';
  end;
  begin
    insert into public.attachments (household_id, bill_id, uploaded_by, storage_path)
    values ('10000000-0000-0000-0000-00000000000a', '20000000-0000-0000-0000-00000000000a',
            '00000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-00000000000b/bill/x.jpg');
    raise exception 'FAIL: an attachment path outside its household was allowed';
  exception when check_violation then
    raise notice 'PASS: the table refuses a path outside the attachment''s household';
  end;
end $$;

set local role authenticated;

-- alice uploads a photo to her own household --------------------------------
do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  insert into storage.objects (bucket_id, name, owner_id)
  values ('attachments', '10000000-0000-0000-0000-00000000000a/maintenance/50000000-0000-0000-0000-00000000000a.jpg',
          '00000000-0000-0000-0000-00000000000a');
  raise notice 'PASS: a member uploads to their household''s folder';

  insert into public.attachments (household_id, maintenance_request_id, storage_path)
  values ('10000000-0000-0000-0000-00000000000a', '40000000-0000-0000-0000-00000000000a',
          '10000000-0000-0000-0000-00000000000a/maintenance/50000000-0000-0000-0000-00000000000a.jpg');
  insert into public.attachments (household_id, bill_id, storage_path)
  values ('10000000-0000-0000-0000-00000000000a', '20000000-0000-0000-0000-00000000000a',
          '10000000-0000-0000-0000-00000000000a/bill/50000000-0000-0000-0000-00000000000b.jpg');
  raise notice 'PASS: a member links photos to their request and bill';

  begin
    insert into storage.objects (bucket_id, name, owner_id)
    values ('attachments', '10000000-0000-0000-0000-00000000000a/other/x.jpg',
            '00000000-0000-0000-0000-00000000000a');
    raise exception 'FAIL: upload to an unknown kind folder was allowed';
  exception when insufficient_privilege then
    raise notice 'PASS: uploads outside maintenance/ and bill/ are refused';
  end;

  begin
    insert into public.attachments (household_id, maintenance_request_id, bill_id, storage_path)
    values ('10000000-0000-0000-0000-00000000000a', '40000000-0000-0000-0000-00000000000a',
            '20000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-00000000000a/bill/both.jpg');
    raise exception 'FAIL: an attachment with two parents was allowed';
  exception when check_violation or insufficient_privilege then
    raise notice 'PASS: an attachment needs exactly one parent';
  end;

  begin
    insert into public.attachments (household_id, maintenance_request_id, storage_path)
    values ('10000000-0000-0000-0000-00000000000a', null,
            '10000000-0000-0000-0000-00000000000a/bill/none.jpg');
    raise exception 'FAIL: an attachment with no parent was allowed';
  exception when check_violation or insufficient_privilege then
    raise notice 'PASS: an attachment with no parent is refused';
  end;

  begin
    insert into public.attachments (household_id, maintenance_request_id, storage_path)
    values ('10000000-0000-0000-0000-00000000000a', '40000000-0000-0000-0000-00000000000c',
            '10000000-0000-0000-0000-00000000000a/maintenance/cross.jpg');
    raise exception 'FAIL: linked a photo to another household''s request';
  exception when insufficient_privilege then
    raise notice 'PASS: the parent must be in the attachment''s household';
  end;
end $$;

-- bob, a housemate, can read and delete it -----------------------------------
do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  select count(*) into n from storage.objects where bucket_id = 'attachments';
  if n <> 1 then raise exception 'FAIL: housemate sees % objects, expected 1', n; end if;
  raise notice 'PASS: a housemate reads the household''s photos';

  select count(*) into n from public.attachments;
  if n <> 2 then raise exception 'FAIL: housemate sees % attachment rows, expected 2', n; end if;
  raise notice 'PASS: a housemate reads the household''s attachment rows';

  delete from public.attachments where bill_id is not null;
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: a housemate deleted someone else''s attachment row'; end if;
  raise notice 'PASS: only the uploader deletes an attachment row';
end $$;

-- carol, from another household, is refused everywhere -----------------------
do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000c","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000c', true);

  select count(*) into n from storage.objects where bucket_id = 'attachments';
  if n <> 0 then raise exception 'FAIL: non-member sees % objects', n; end if;
  raise notice 'PASS: a non-member cannot read another household''s photos';

  select count(*) into n from public.attachments;
  if n <> 0 then raise exception 'FAIL: non-member sees % attachment rows', n; end if;
  raise notice 'PASS: a non-member cannot read another household''s attachment rows';

  begin
    insert into storage.objects (bucket_id, name, owner_id)
    values ('attachments', '10000000-0000-0000-0000-00000000000a/maintenance/evil.jpg',
            '00000000-0000-0000-0000-00000000000c');
    raise exception 'FAIL: non-member uploaded to another household''s folder';
  exception when insufficient_privilege then
    raise notice 'PASS: a non-member cannot upload to another household (403)';
  end;

  begin
    insert into storage.objects (bucket_id, name, owner_id)
    values ('attachments', 'not-a-uuid/maintenance/evil.jpg', '00000000-0000-0000-0000-00000000000c');
    raise exception 'FAIL: upload to a non-household folder was allowed';
  exception when insufficient_privilege then
    raise notice 'PASS: a path that is not a household is refused';
  end;

  begin
    insert into public.attachments (household_id, maintenance_request_id, storage_path)
    values ('10000000-0000-0000-0000-00000000000a', '40000000-0000-0000-0000-00000000000a',
            '10000000-0000-0000-0000-00000000000a/maintenance/evil.jpg');
    raise exception 'FAIL: non-member added an attachment row';
  exception when insufficient_privilege then
    raise notice 'PASS: a non-member cannot add an attachment row';
  end;

  delete from storage.objects where bucket_id = 'attachments';
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: non-member deleted another household''s photo'; end if;
  raise notice 'PASS: a non-member cannot delete another household''s photos';
end $$;

-- alice deletes her own photo -----------------------------------------------
do $$
declare n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  delete from public.attachments where maintenance_request_id is not null;
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL: the uploader could not delete their attachment row'; end if;
  raise notice 'PASS: the uploader deletes their attachment row';
end $$;

do $$ begin raise notice 'All attachment checks passed.'; end $$;

rollback;
