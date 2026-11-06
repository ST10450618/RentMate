-- RM-0049: proves migration 0014 (recurring bills).
--
-- Run as the postgres user against a database with every migration applied:
--   psql "postgresql://postgres:postgres@127.0.0.1:54322/postgres" \
--        -v ON_ERROR_STOP=1 -f supabase/tests/recurring_bills_test.sql
-- Everything runs in one transaction that is rolled back at the end. Each
-- check prints "PASS: ..." as a NOTICE; the first failure raises and stops.
--
-- Cast: alice (issuer) and bob share House A. carol is alone in House B.
-- Bill ids are kept in settings (test.*) so the checks can share them.

begin;

insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-0000-0000-0000000000a1', 'alice@test.local', '{"full_name":"Alice"}'),
  ('00000000-0000-0000-0000-0000000000b1', 'bob@test.local',   '{"full_name":"Bob"}'),
  ('00000000-0000-0000-0000-0000000000c1', 'carol@test.local', '{"full_name":"Carol"}');
insert into public.households (id, name, invite_code) values
  ('10000000-0000-0000-0000-0000000000a1', 'House A', 'RBAAAA'),
  ('10000000-0000-0000-0000-0000000000b1', 'House B', 'RBBBBB');
insert into public.household_members (household_id, user_id) values
  ('10000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000a1'),
  ('10000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000b1'),
  ('10000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000c1');

set local role authenticated;

-- Create bills ------------------------------------------------------------------
do $$
declare
  b public.bills;
  w public.bills;
  n int;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true),
          set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a1', true);

  b := public.create_bill('10000000-0000-0000-0000-0000000000a1', 'Rent', 1000.01, '2026-01-10T00:00:00Z', 'Percentage',
    '[{"user_id":"00000000-0000-0000-0000-0000000000a1","value":50},{"user_id":"00000000-0000-0000-0000-0000000000b1","value":50}]'::jsonb,
    'Monthly');
  perform set_config('test.monthly', b.id::text, true);
  if b.repeat <> 'Monthly' then raise exception 'FAIL: create_bill did not store Monthly'; end if;
  raise notice 'PASS: create_bill stores Monthly';

  w := public.create_bill('10000000-0000-0000-0000-0000000000a1', 'Cleaner', 120, '2026-01-10T00:00:00Z', 'Equal',
    '[{"user_id":"00000000-0000-0000-0000-0000000000a1","value":0},{"user_id":"00000000-0000-0000-0000-0000000000b1","value":0}]'::jsonb,
    'Weekly');
  perform set_config('test.weekly', w.id::text, true);

  -- Six named arguments still work and default to Never.
  b := public.create_bill(household_id_in => '10000000-0000-0000-0000-0000000000a1', title_in => 'Once', amount_in => 50,
    due_date_in => '2025-01-01T00:00:00Z', split_method_in => 'Equal',
    shares_in => '[{"user_id":"00000000-0000-0000-0000-0000000000a1","value":0}]'::jsonb);
  if b.repeat <> 'Never' then raise exception 'FAIL: default repeat is not Never'; end if;
  perform set_config('test.never', b.id::text, true);
  raise notice 'PASS: create_bill without repeat_in defaults to Never';

  begin
    perform public.create_bill('10000000-0000-0000-0000-0000000000a1', 'Bad', 10, now(), 'Equal',
      '[{"user_id":"00000000-0000-0000-0000-0000000000a1","value":0}]'::jsonb, 'Daily');
    raise exception 'FAIL: repeat Daily was accepted';
  exception when raise_exception then
    if sqlerrm like 'FAIL%' then raise; end if;
    raise notice 'PASS: create_bill rejects an unknown repeat value';
  end;

  -- Carol's own repeating bill in House B.
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c1","role":"authenticated"}', true),
          set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000c1', true);
  b := public.create_bill('10000000-0000-0000-0000-0000000000b1', 'Carol rent', 700, '2026-02-01T00:00:00Z', 'Equal',
    '[{"user_id":"00000000-0000-0000-0000-0000000000c1","value":0}]'::jsonb, 'Monthly');
  perform set_config('test.carol', b.id::text, true);
end $$;

-- Mark Bob's share of the rent paid, to prove the copy starts unpaid.
reset role;
update public.bill_shares set is_paid = true, paid_at = now()
 where bill_id = current_setting('test.monthly')::uuid and user_id = '00000000-0000-0000-0000-0000000000b1';

-- Generation ---------------------------------------------------------------------
do $$
declare
  m uuid := current_setting('test.monthly')::uuid;
  w uuid := current_setting('test.weekly')::uuid;
  nv uuid := current_setting('test.never')::uuid;
  made int;
  copy_id uuid;
  copy2_id uuid;
  r record;
begin
  -- The day before the next due date: nothing.
  made := public.generate_recurring_bills('2026-01-16');
  if made <> 0 then raise exception 'FAIL: made % copies before any due date', made; end if;
  raise notice 'PASS: nothing is made before the next due date';

  -- Weekly bill is due again on 17 Jan.
  made := public.generate_recurring_bills('2026-01-17');
  if made <> 1 then raise exception 'FAIL: expected 1 weekly copy, made %', made; end if;
  select id into copy_id from public.bills where repeat_parent_id = w;
  if (select due_date from public.bills where id = copy_id) <> '2026-01-17T00:00:00Z'::timestamptz then
    raise exception 'FAIL: weekly copy has the wrong due date';
  end if;
  raise notice 'PASS: Weekly bill repeats 7 days later';

  -- Monthly bill is due again on 10 Feb. Weekly bills also catch up one cycle.
  made := public.generate_recurring_bills('2026-02-10');
  select id into copy_id from public.bills where repeat_parent_id = m;
  if copy_id is null then raise exception 'FAIL: no monthly copy on its due date'; end if;
  if (select count(*) from public.bills where repeat_parent_id = m) <> 1 then
    raise exception 'FAIL: more than one monthly copy';
  end if;
  select * into r from public.bills where id = copy_id;
  if r.due_date <> '2026-02-10T00:00:00Z'::timestamptz or r.amount <> 1000.01 or r.title <> 'Rent'
     or r.household_id <> '10000000-0000-0000-0000-0000000000a1'
     or r.issued_by_user_id <> '00000000-0000-0000-0000-0000000000a1'
     or r.split_method <> 'Percentage' or r.repeat <> 'Monthly' or r.repeat_stopped then
    raise exception 'FAIL: monthly copy fields are wrong';
  end if;
  raise notice 'PASS: Monthly bill is copied on its next due date with the same fields';

  if (select count(*) from public.bill_shares where bill_id = copy_id) <> 2
     or exists (
       select 1 from public.bill_shares c
       join public.bill_shares o on o.bill_id = m and o.user_id = c.user_id
       where c.bill_id = copy_id and (c.amount_owed <> o.amount_owed or c.is_paid or c.paid_at is not null)
     ) then
    raise exception 'FAIL: copied shares differ or are not unpaid';
  end if;
  if (select amount_owed from public.bill_shares where bill_id = copy_id
      and user_id = '00000000-0000-0000-0000-0000000000b1') <> 500.00 then
    raise exception 'FAIL: copied share amount is wrong';
  end if;
  raise notice 'PASS: copy has the same shares, all unpaid (even though Bob had paid the original)';

  -- Exactly once.
  made := public.generate_recurring_bills('2026-02-10');
  if (select count(*) from public.bills where repeat_parent_id = m) <> 1 then
    raise exception 'FAIL: a second run duplicated the monthly copy';
  end if;
  raise notice 'PASS: running twice makes no duplicate';

  if public.generate_recurring_bills('2026-02-12') is null or
     (select count(*) from public.bills where repeat_parent_id = m) <> 1 then
    raise exception 'FAIL: a later run on the same cycle duplicated';
  end if;

  -- The copy is now the latest, so the next cycle follows from it.
  made := public.generate_recurring_bills('2026-03-09');
  if exists (select 1 from public.bills where repeat_parent_id = copy_id) then
    raise exception 'FAIL: next cycle made too early';
  end if;
  perform public.generate_recurring_bills('2026-03-10');
  select id into copy2_id from public.bills where repeat_parent_id = copy_id;
  if copy2_id is null or (select due_date from public.bills where id = copy2_id) <> '2026-03-10T00:00:00Z'::timestamptz then
    raise exception 'FAIL: next cycle did not follow from the copy';
  end if;
  if (select count(*) from public.bills where repeat_parent_id = m) <> 1 then
    raise exception 'FAIL: the original made a second copy';
  end if;
  raise notice 'PASS: the next cycle follows from the latest copy only';

  -- Never bills never repeat, however old.
  if exists (select 1 from public.bills where repeat_parent_id = nv) then
    raise exception 'FAIL: a Never bill repeated';
  end if;
  raise notice 'PASS: Never bills never repeat';

  -- Other household: Carol's bill (due 1 Feb, next 1 Mar) got a copy only in House B.
  if (select count(*) from public.bills where household_id = '10000000-0000-0000-0000-0000000000b1') <> 2 then
    raise exception 'FAIL: House B bill count is wrong after the March run';
  end if;
  if exists (
    select 1 from public.bill_shares s join public.bills b on b.id = s.bill_id
    where b.household_id = '10000000-0000-0000-0000-0000000000b1'
      and s.user_id <> '00000000-0000-0000-0000-0000000000c1'
  ) or exists (
    select 1 from public.bills where repeat_parent_id = current_setting('test.carol')::uuid
      and household_id <> '10000000-0000-0000-0000-0000000000b1'
  ) then
    raise exception 'FAIL: House B copy leaked into another household';
  end if;
  raise notice 'PASS: each household copies only its own bills and shares';
end $$;

-- Stop rule and privileges ---------------------------------------------------------
set local role authenticated;

do $$
declare
  m uuid := current_setting('test.monthly')::uuid;
  carol uuid := current_setting('test.carol')::uuid;
  before_a int;
  before_b int;
begin
  -- Bob is in House A but did not issue the bill.
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000b1","role":"authenticated"}', true),
          set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000b1', true);
  begin
    perform public.stop_bill_repeat(m);
    raise exception 'FAIL: a non-issuer stopped the repeat';
  exception when insufficient_privilege then
    raise notice 'PASS: a housemate who is not the issuer cannot stop a repeat';
  end;
  begin
    perform public.stop_bill_repeat('99999999-0000-0000-0000-000000000000');
    raise exception 'FAIL: stopping a missing bill did not fail';
  exception when insufficient_privilege then
    raise notice 'PASS: stopping a missing bill gives the same error';
  end;

  -- Direct column writes are blocked, for the issuer too.
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true),
          set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a1', true);
  begin
    update public.bills set repeat_stopped = true where id = m;
    raise exception 'FAIL: direct update of repeat_stopped was accepted';
  exception when insufficient_privilege then
    raise notice 'PASS: repeat_stopped cannot be set directly';
  end;
  begin
    update public.bills set repeat_parent_id = null where id = m;
    raise exception 'FAIL: direct update of repeat_parent_id was accepted';
  exception when insufficient_privilege then
    raise notice 'PASS: repeat_parent_id cannot be set directly';
  end;
  begin
    update public.bills set repeat = 'Weekly' where id = current_setting('test.never')::uuid;
    raise exception 'FAIL: direct update of repeat was accepted';
  exception when insufficient_privilege then
    raise notice 'PASS: repeat cannot be set directly';
  end;
  update public.bills set title = 'Rent (edited)' where id = m;
  if not found then raise exception 'FAIL: the issuer can no longer edit the title'; end if;
  raise notice 'PASS: the issuer can still edit ordinary bill fields';

  -- Alice cannot stop Carol's bill in another household.
  begin
    perform public.stop_bill_repeat(carol);
    raise exception 'FAIL: alice stopped carol''s repeat';
  exception when insufficient_privilege then
    raise notice 'PASS: another household''s bill cannot be stopped';
  end;

  -- Nobody but the scheduler can run the generator.
  begin
    perform public.generate_recurring_bills();
    raise exception 'FAIL: authenticated ran generate_recurring_bills';
  exception when insufficient_privilege then
    raise notice 'PASS: authenticated cannot run generate_recurring_bills';
  end;

  -- The issuer stops it. The chain is Rent -> Feb -> Mar; stopping the first stops all.
  select count(*) into before_a from public.bills where household_id = '10000000-0000-0000-0000-0000000000a1';
  perform public.stop_bill_repeat(m);
end $$;

reset role;

do $$
declare
  a_before int;
  made int;
begin
  if exists (
    select 1 from public.bills b
    where b.household_id = '10000000-0000-0000-0000-0000000000a1' and b.title like 'Rent%' and not b.repeat_stopped
  ) then
    raise exception 'FAIL: some bills in the chain are still repeating';
  end if;

  select count(*) into a_before from public.bills where household_id = '10000000-0000-0000-0000-0000000000a1' and title like 'Rent%';
  perform public.generate_recurring_bills('2027-01-01');
  if (select count(*) from public.bills where household_id = '10000000-0000-0000-0000-0000000000a1' and title like 'Rent%') <> a_before then
    raise exception 'FAIL: a stopped bill still made a copy';
  end if;
  raise notice 'PASS: the issuer stops the repeat and no further copy is made';

  -- Stopping Rent did not touch the cleaner (weekly) or Carol's bill.
  if (select repeat_stopped from public.bills where id = current_setting('test.weekly')::uuid)
     or (select repeat_stopped from public.bills where id = current_setting('test.carol')::uuid) then
    raise exception 'FAIL: stopping one bill stopped another';
  end if;
  if (select count(*) from public.bills where repeat_parent_id = current_setting('test.carol')::uuid) <> 1 then
    raise exception 'FAIL: House B chain changed';
  end if;
  raise notice 'PASS: other bills and other households keep repeating';
end $$;

rollback;
