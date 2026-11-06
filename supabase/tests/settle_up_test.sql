-- RM-0030: proves migration 0012 (deterministic settle_up).
--
-- The cases are generated from supabase/tests/fixtures/settle_up_fixtures.json,
-- which is the source of truth shared with the Kotlin DebtSimplifierTest.
-- Keep the two in step if a fixture changes.
--
-- Run it the same way as rls_test.sql, against a database with every
-- migration applied:
--   psql "postgresql://postgres:postgres@127.0.0.1:54322/postgres" \
--        -v ON_ERROR_STOP=1 -f supabase/tests/settle_up_test.sql
--
-- One transaction, rolled back at the end. Each check prints "PASS: ..." as a
-- NOTICE; the first failing check raises an exception and stops the script.

begin;

insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-0000-0000-000000000061', 'a@test.local', '{"full_name":"A"}'),
  ('00000000-0000-0000-0000-000000000062', 'b@test.local', '{"full_name":"B"}'),
  ('00000000-0000-0000-0000-000000000063', 'c@test.local', '{"full_name":"C"}'),
  ('00000000-0000-0000-0000-000000000064', 'd@test.local', '{"full_name":"D"}'),
  ('00000000-0000-0000-0000-000000000065', 'e@test.local', '{"full_name":"E"}'),
  ('00000000-0000-0000-0000-000000000066', 'f@test.local', '{"full_name":"F"}');

insert into public.households (id, name, invite_code) values ('10000000-0000-0000-0000-000000000001', 'simple pair', 'SU0001');
insert into public.household_members (household_id, user_id) values
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000061'),
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000062'),
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000063'),
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000064'),
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000065'),
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000066');
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000001', 'fx', 10.00, now(), '00000000-0000-0000-0000-000000000061') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000062', 10.00 from b;

insert into public.households (id, name, invite_code) values ('10000000-0000-0000-0000-000000000002', 'three way', 'SU0002');
insert into public.household_members (household_id, user_id) values
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000061'),
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000062'),
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000063'),
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000064'),
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000065'),
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000066');
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000002', 'fx', 30.00, now(), '00000000-0000-0000-0000-000000000061') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000062', 30.00 from b;
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000002', 'fx', 10.00, now(), '00000000-0000-0000-0000-000000000062') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000063', 10.00 from b;

insert into public.households (id, name, invite_code) values ('10000000-0000-0000-0000-000000000003', 'ties break on user id', 'SU0003');
insert into public.household_members (household_id, user_id) values
  ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000061'),
  ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000062'),
  ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000063'),
  ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000064'),
  ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000065'),
  ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000066');
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000003', 'fx', 10.00, now(), '00000000-0000-0000-0000-000000000062') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000063', 10.00 from b;
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000003', 'fx', 10.00, now(), '00000000-0000-0000-0000-000000000061') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000064', 10.00 from b;

insert into public.households (id, name, invite_code) values ('10000000-0000-0000-0000-000000000004', 'already settled', 'SU0004');
insert into public.household_members (household_id, user_id) values
  ('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000061'),
  ('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000062'),
  ('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000063'),
  ('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000064'),
  ('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000065'),
  ('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000066');
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000004', 'fx', 5.00, now(), '00000000-0000-0000-0000-000000000061') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000062', 5.00 from b;
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000004', 'fx', 5.00, now(), '00000000-0000-0000-0000-000000000062') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000061', 5.00 from b;

insert into public.households (id, name, invite_code) values ('10000000-0000-0000-0000-000000000005', 'no unpaid shares', 'SU0005');
insert into public.household_members (household_id, user_id) values
  ('10000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000061'),
  ('10000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000062'),
  ('10000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000063'),
  ('10000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000064'),
  ('10000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000065'),
  ('10000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000066');

insert into public.households (id, name, invite_code) values ('10000000-0000-0000-0000-000000000006', 'rounding cents', 'SU0006');
insert into public.household_members (household_id, user_id) values
  ('10000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000061'),
  ('10000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000062'),
  ('10000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000063'),
  ('10000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000064'),
  ('10000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000065'),
  ('10000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000066');
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000006', 'fx', 33.33, now(), '00000000-0000-0000-0000-000000000061') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000062', 33.33 from b;
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000006', 'fx', 33.33, now(), '00000000-0000-0000-0000-000000000061') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000063', 33.33 from b;
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000006', 'fx', 33.34, now(), '00000000-0000-0000-0000-000000000061') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000064', 33.34 from b;
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000006', 'fx', 0.01, now(), '00000000-0000-0000-0000-000000000061') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000065', 0.01 from b;

insert into public.households (id, name, invite_code) values ('10000000-0000-0000-0000-000000000007', 'many small debts', 'SU0007');
insert into public.household_members (household_id, user_id) values
  ('10000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000061'),
  ('10000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000062'),
  ('10000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000063'),
  ('10000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000064'),
  ('10000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000065'),
  ('10000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000066');
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000007', 'fx', 1.50, now(), '00000000-0000-0000-0000-000000000061') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000062', 1.50 from b;
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000007', 'fx', 2.50, now(), '00000000-0000-0000-0000-000000000061') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000063', 2.50 from b;
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000007', 'fx', 3.50, now(), '00000000-0000-0000-0000-000000000065') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000064', 3.50 from b;
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000007', 'fx', 0.50, now(), '00000000-0000-0000-0000-000000000065') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000066', 0.50 from b;
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000007', 'fx', 1.00, now(), '00000000-0000-0000-0000-000000000065') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000061', 1.00 from b;
with b as (insert into public.bills (household_id, title, amount, due_date, issued_by_user_id) values ('10000000-0000-0000-0000-000000000007', 'fx', 0.25, now(), '00000000-0000-0000-0000-000000000066') returning id)
insert into public.bill_shares (bill_id, user_id, amount_owed) select id, '00000000-0000-0000-0000-000000000063', 0.25 from b;

set local role authenticated;

do $$
declare got text;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000061","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000061', true);
  select coalesce(string_agg(from_user_id || '>' || to_user_id || '>' || amount, ',' order by ord), '') into got
  from (select s.*, row_number() over () as ord from public.settle_up('10000000-0000-0000-0000-000000000001') s) s;
  if got <> '00000000-0000-0000-0000-000000000062>00000000-0000-0000-0000-000000000061>10.00' then
    raise exception 'FAIL: simple pair gave % instead of %', got, '00000000-0000-0000-0000-000000000062>00000000-0000-0000-0000-000000000061>10.00';
  end if;
  raise notice 'PASS: settle_up simple pair (1 transfers)';
end $$;

do $$
declare got text;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000061","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000061', true);
  select coalesce(string_agg(from_user_id || '>' || to_user_id || '>' || amount, ',' order by ord), '') into got
  from (select s.*, row_number() over () as ord from public.settle_up('10000000-0000-0000-0000-000000000002') s) s;
  if got <> '00000000-0000-0000-0000-000000000062>00000000-0000-0000-0000-000000000061>20.00,00000000-0000-0000-0000-000000000063>00000000-0000-0000-0000-000000000061>10.00' then
    raise exception 'FAIL: three way gave % instead of %', got, '00000000-0000-0000-0000-000000000062>00000000-0000-0000-0000-000000000061>20.00,00000000-0000-0000-0000-000000000063>00000000-0000-0000-0000-000000000061>10.00';
  end if;
  raise notice 'PASS: settle_up three way (2 transfers)';
end $$;

do $$
declare got text;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000061","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000061', true);
  select coalesce(string_agg(from_user_id || '>' || to_user_id || '>' || amount, ',' order by ord), '') into got
  from (select s.*, row_number() over () as ord from public.settle_up('10000000-0000-0000-0000-000000000003') s) s;
  if got <> '00000000-0000-0000-0000-000000000063>00000000-0000-0000-0000-000000000061>10.00,00000000-0000-0000-0000-000000000064>00000000-0000-0000-0000-000000000062>10.00' then
    raise exception 'FAIL: ties break on user id gave % instead of %', got, '00000000-0000-0000-0000-000000000063>00000000-0000-0000-0000-000000000061>10.00,00000000-0000-0000-0000-000000000064>00000000-0000-0000-0000-000000000062>10.00';
  end if;
  raise notice 'PASS: settle_up ties break on user id (2 transfers)';
end $$;

do $$
declare got text;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000061","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000061', true);
  select coalesce(string_agg(from_user_id || '>' || to_user_id || '>' || amount, ',' order by ord), '') into got
  from (select s.*, row_number() over () as ord from public.settle_up('10000000-0000-0000-0000-000000000004') s) s;
  if got <> '' then
    raise exception 'FAIL: already settled gave % instead of %', got, '';
  end if;
  raise notice 'PASS: settle_up already settled (0 transfers)';
end $$;

do $$
declare got text;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000061","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000061', true);
  select coalesce(string_agg(from_user_id || '>' || to_user_id || '>' || amount, ',' order by ord), '') into got
  from (select s.*, row_number() over () as ord from public.settle_up('10000000-0000-0000-0000-000000000005') s) s;
  if got <> '' then
    raise exception 'FAIL: no unpaid shares gave % instead of %', got, '';
  end if;
  raise notice 'PASS: settle_up no unpaid shares (0 transfers)';
end $$;

do $$
declare got text;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000061","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000061', true);
  select coalesce(string_agg(from_user_id || '>' || to_user_id || '>' || amount, ',' order by ord), '') into got
  from (select s.*, row_number() over () as ord from public.settle_up('10000000-0000-0000-0000-000000000006') s) s;
  if got <> '00000000-0000-0000-0000-000000000064>00000000-0000-0000-0000-000000000061>33.34,00000000-0000-0000-0000-000000000062>00000000-0000-0000-0000-000000000061>33.33,00000000-0000-0000-0000-000000000063>00000000-0000-0000-0000-000000000061>33.33,00000000-0000-0000-0000-000000000065>00000000-0000-0000-0000-000000000061>0.01' then
    raise exception 'FAIL: rounding cents gave % instead of %', got, '00000000-0000-0000-0000-000000000064>00000000-0000-0000-0000-000000000061>33.34,00000000-0000-0000-0000-000000000062>00000000-0000-0000-0000-000000000061>33.33,00000000-0000-0000-0000-000000000063>00000000-0000-0000-0000-000000000061>33.33,00000000-0000-0000-0000-000000000065>00000000-0000-0000-0000-000000000061>0.01';
  end if;
  raise notice 'PASS: settle_up rounding cents (4 transfers)';
end $$;

do $$
declare got text;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000061","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000061', true);
  select coalesce(string_agg(from_user_id || '>' || to_user_id || '>' || amount, ',' order by ord), '') into got
  from (select s.*, row_number() over () as ord from public.settle_up('10000000-0000-0000-0000-000000000007') s) s;
  if got <> '00000000-0000-0000-0000-000000000064>00000000-0000-0000-0000-000000000065>3.50,00000000-0000-0000-0000-000000000063>00000000-0000-0000-0000-000000000065>1.50,00000000-0000-0000-0000-000000000063>00000000-0000-0000-0000-000000000061>1.25,00000000-0000-0000-0000-000000000062>00000000-0000-0000-0000-000000000061>1.50,00000000-0000-0000-0000-000000000066>00000000-0000-0000-0000-000000000061>0.25' then
    raise exception 'FAIL: many small debts gave % instead of %', got, '00000000-0000-0000-0000-000000000064>00000000-0000-0000-0000-000000000065>3.50,00000000-0000-0000-0000-000000000063>00000000-0000-0000-0000-000000000065>1.50,00000000-0000-0000-0000-000000000063>00000000-0000-0000-0000-000000000061>1.25,00000000-0000-0000-0000-000000000062>00000000-0000-0000-0000-000000000061>1.50,00000000-0000-0000-0000-000000000066>00000000-0000-0000-0000-000000000061>0.25';
  end if;
  raise notice 'PASS: settle_up many small debts (5 transfers)';
end $$;

-- A non-member is still refused.
do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000ff","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000ff', true);
  begin
    perform * from public.settle_up('10000000-0000-0000-0000-000000000001');
    raise exception 'FAIL: non-member was allowed to call settle_up';
  exception when others then
    if sqlerrm not like 'Not a member%' then raise; end if;
  end;
  raise notice 'PASS: settle_up still refuses a non-member';
end $$;

rollback;
