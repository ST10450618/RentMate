-- RM-0010: proves migration 0008 (creditor-only settle-up, split validation).
--
-- Run it the same way as rls_test.sql, against a database with every
-- migration applied:
--   psql "postgresql://postgres:postgres@127.0.0.1:54322/postgres" \
--        -v ON_ERROR_STOP=1 -f supabase/tests/settlements_test.sql
--
-- One transaction, rolled back at the end. Each check prints "PASS: ..." as a
-- NOTICE; the first failing check raises an exception and stops the script.

begin;

-- alice, bob and carol share House A; dave is not in any household.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-0000-0000-00000000000a', 'alice@test.local', '{"full_name":"Alice"}'),
  ('00000000-0000-0000-0000-00000000000b', 'bob@test.local',   '{"full_name":"Bob"}'),
  ('00000000-0000-0000-0000-00000000000c', 'carol@test.local', '{"full_name":"Carol"}'),
  ('00000000-0000-0000-0000-00000000000d', 'dave@test.local',  '{"full_name":"Dave"}');

insert into public.households (id, name, invite_code) values
  ('10000000-0000-0000-0000-00000000000a', 'House A', 'TSTAAA');

insert into public.household_members (household_id, user_id) values
  ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'),
  ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b'),
  ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000c');

set local role authenticated;

-- Split modes -----------------------------------------------------------------
do $$
declare b public.bills; got numeric[];
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  b := public.create_bill('10000000-0000-0000-0000-00000000000a', 'Equal bill', 100, now() + interval '5 days', 'Equal',
    '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":0},{"user_id":"00000000-0000-0000-0000-00000000000b","value":0},{"user_id":"00000000-0000-0000-0000-00000000000c","value":0}]'::jsonb);
  select array_agg(amount_owed order by amount_owed) into got from public.bill_shares where bill_id = b.id;
  if got <> array[33.33, 33.33, 33.34]::numeric[] or b.split_method <> 'Equal' then
    raise exception 'FAIL: equal split gave % (method %)', got, b.split_method;
  end if;
  raise notice 'PASS: equal split, last share absorbs the remainder (33.33, 33.33, 33.34)';
end $$;

do $$
declare b public.bills; got numeric[]; tot numeric;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  b := public.create_bill('10000000-0000-0000-0000-00000000000a', 'Pct bill', 200, now() + interval '5 days', 'Percentage',
    '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":50},{"user_id":"00000000-0000-0000-0000-00000000000b","value":30},{"user_id":"00000000-0000-0000-0000-00000000000c","value":20}]'::jsonb);
  select array_agg(amount_owed order by amount_owed) into got from public.bill_shares where bill_id = b.id;
  if got <> array[40, 60, 100]::numeric[] or b.split_method <> 'Percentage' then
    raise exception 'FAIL: percentage split gave %', got;
  end if;
  raise notice 'PASS: percentage split 50/30/20 of R200 gives 100/60/40';

  b := public.create_bill('10000000-0000-0000-0000-00000000000a', 'Pct rounding', 100, now() + interval '5 days', 'Percentage',
    '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":33.33},{"user_id":"00000000-0000-0000-0000-00000000000b","value":33.33},{"user_id":"00000000-0000-0000-0000-00000000000c","value":33.33}]'::jsonb);
  select sum(amount_owed) into tot from public.bill_shares where bill_id = b.id;
  if tot <> 100.00 then
    raise exception 'FAIL: percentage shares add up to % instead of 100.00', tot;
  end if;
  raise notice 'PASS: percentages within 0.01 of 100 are accepted and shares still add up to the bill';
end $$;

do $$
declare b public.bills; got numeric[];
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  b := public.create_bill('10000000-0000-0000-0000-00000000000a', 'Exact bill', 200, now() + interval '5 days', 'ExactAmount',
    '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":100.00},{"user_id":"00000000-0000-0000-0000-00000000000b","value":60.50},{"user_id":"00000000-0000-0000-0000-00000000000c","value":39.50}]'::jsonb);
  select array_agg(amount_owed order by amount_owed) into got from public.bill_shares where bill_id = b.id;
  if got <> array[39.50, 60.50, 100.00]::numeric[] or b.split_method <> 'ExactAmount' then
    raise exception 'FAIL: exact split gave %', got;
  end if;
  raise notice 'PASS: exact split stores the amounts given';
end $$;

-- Split validation ------------------------------------------------------------
do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    perform public.create_bill('10000000-0000-0000-0000-00000000000a', 'X', 200, now(), 'Percentage', '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":50},{"user_id":"00000000-0000-0000-0000-00000000000b","value":30},{"user_id":"00000000-0000-0000-0000-00000000000c","value":10}]'::jsonb);
    raise exception 'FAIL: percentages summing to 90 were accepted';
  exception when raise_exception or insufficient_privilege then
    if sqlerrm like 'FAIL:%' then raise; end if;
    if sqlerrm not like 'Percentages must add up to 100%' then
      raise exception 'FAIL: wrong error for percentages that do not add up to 100 are rejected: %', sqlerrm;
    end if;
    raise notice 'PASS: percentages that do not add up to 100 are rejected';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    perform public.create_bill('10000000-0000-0000-0000-00000000000a', 'X', 200, now(), 'Percentage', '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":60},{"user_id":"00000000-0000-0000-0000-00000000000b","value":50}]'::jsonb);
    raise exception 'FAIL: percentages summing to 110 were accepted';
  exception when raise_exception or insufficient_privilege then
    if sqlerrm like 'FAIL:%' then raise; end if;
    if sqlerrm not like 'Percentages must add up to 100%' then
      raise exception 'FAIL: wrong error for percentages over 100 are rejected: %', sqlerrm;
    end if;
    raise notice 'PASS: percentages over 100 are rejected';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    perform public.create_bill('10000000-0000-0000-0000-00000000000a', 'X', 200, now(), 'ExactAmount', '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":100},{"user_id":"00000000-0000-0000-0000-00000000000b","value":60},{"user_id":"00000000-0000-0000-0000-00000000000c","value":30}]'::jsonb);
    raise exception 'FAIL: exact amounts summing to 190 were accepted';
  exception when raise_exception or insufficient_privilege then
    if sqlerrm like 'FAIL:%' then raise; end if;
    if sqlerrm not like 'Amounts must add up to the bill total%' then
      raise exception 'FAIL: wrong error for exact amounts that do not add up to the bill are rejected: %', sqlerrm;
    end if;
    raise notice 'PASS: exact amounts that do not add up to the bill are rejected';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    perform public.create_bill('10000000-0000-0000-0000-00000000000a', 'X', 200, now(), 'ExactAmount', '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":100},{"user_id":"00000000-0000-0000-0000-00000000000b","value":60},{"user_id":"00000000-0000-0000-0000-00000000000c","value":60}]'::jsonb);
    raise exception 'FAIL: exact amounts summing to 220 were accepted';
  exception when raise_exception or insufficient_privilege then
    if sqlerrm like 'FAIL:%' then raise; end if;
    if sqlerrm not like 'Amounts must add up to the bill total%' then
      raise exception 'FAIL: wrong error for exact amounts above the bill total are rejected: %', sqlerrm;
    end if;
    raise notice 'PASS: exact amounts above the bill total are rejected';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    perform public.create_bill('10000000-0000-0000-0000-00000000000a', 'X', 100, now(), 'Equal', '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":0},{"user_id":"00000000-0000-0000-0000-00000000000d","value":0}]'::jsonb);
    raise exception 'FAIL: a non-member was added to a split';
  exception when raise_exception or insufficient_privilege then
    if sqlerrm like 'FAIL:%' then raise; end if;
    if sqlerrm not like 'Everyone in the split must be a member%' then
      raise exception 'FAIL: wrong error for a non-member in the split is rejected: %', sqlerrm;
    end if;
    raise notice 'PASS: a non-member in the split is rejected';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    perform public.create_bill('10000000-0000-0000-0000-00000000000a', 'X', 100, now(), 'Equal', '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":0},{"user_id":"00000000-0000-0000-0000-00000000000a","value":0}]'::jsonb);
    raise exception 'FAIL: duplicate users were accepted';
  exception when raise_exception or insufficient_privilege then
    if sqlerrm like 'FAIL:%' then raise; end if;
    if sqlerrm not like 'Each person can only appear once%' then
      raise exception 'FAIL: wrong error for the same person twice in a split is rejected: %', sqlerrm;
    end if;
    raise notice 'PASS: the same person twice in a split is rejected';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    perform public.create_bill('10000000-0000-0000-0000-00000000000a', 'X', 100, now(), 'Equal', '[]'::jsonb);
    raise exception 'FAIL: an empty split was accepted';
  exception when raise_exception or insufficient_privilege then
    if sqlerrm like 'FAIL:%' then raise; end if;
    if sqlerrm not like 'At least one share is required' then
      raise exception 'FAIL: wrong error for an empty split is rejected: %', sqlerrm;
    end if;
    raise notice 'PASS: an empty split is rejected';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    perform public.create_bill('10000000-0000-0000-0000-00000000000a', 'X', 100, now(), 'Weighted', '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":0}]'::jsonb);
    raise exception 'FAIL: unknown split method was accepted';
  exception when raise_exception or insufficient_privilege then
    if sqlerrm like 'FAIL:%' then raise; end if;
    if sqlerrm not like 'Split method must be%' then
      raise exception 'FAIL: wrong error for an unknown split method is rejected: %', sqlerrm;
    end if;
    raise notice 'PASS: an unknown split method is rejected';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    perform public.create_bill('10000000-0000-0000-0000-00000000000a', 'X', 0, now(), 'Equal', '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":0}]'::jsonb);
    raise exception 'FAIL: zero amount was accepted';
  exception when raise_exception or insufficient_privilege then
    if sqlerrm like 'FAIL:%' then raise; end if;
    if sqlerrm not like 'Amount must be greater than 0' then
      raise exception 'FAIL: wrong error for a zero amount is rejected: %', sqlerrm;
    end if;
    raise notice 'PASS: a zero amount is rejected';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    insert into public.bills (household_id, title, amount, due_date, issued_by_user_id)
    values ('10000000-0000-0000-0000-00000000000a', 'Direct', 10, now(), '00000000-0000-0000-0000-00000000000a');
    raise exception 'FAIL: direct insert into bills bypassed create_bill';
  exception when insufficient_privilege then
    raise notice 'PASS: bills cannot be inserted directly, only through create_bill';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000d","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000d', true);
  begin
    perform public.create_bill('10000000-0000-0000-0000-00000000000a', 'Outsider', 10, now(), 'Equal', '[{"user_id":"00000000-0000-0000-0000-00000000000d","value":0}]'::jsonb);
    raise exception 'FAIL: outsider created a bill in House A';
  exception when insufficient_privilege then
    raise notice 'PASS: a non-member cannot create a bill in the household';
  end;
end $$;

-- Settle-up -------------------------------------------------------------------
do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  -- bill 1 (due first): R300 equal -> bob owes 100, carol owes 100
  perform public.create_bill('10000000-0000-0000-0000-00000000000a', 'Settle 1', 300, now() + interval '1 day', 'Equal',
    '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":0},{"user_id":"00000000-0000-0000-0000-00000000000b","value":0},{"user_id":"00000000-0000-0000-0000-00000000000c","value":0}]'::jsonb);
  -- bill 2 (due later): R150 exact -> bob owes 50
  perform public.create_bill('10000000-0000-0000-0000-00000000000a', 'Settle 2', 150, now() + interval '30 days', 'ExactAmount',
    '[{"user_id":"00000000-0000-0000-0000-00000000000a","value":100},{"user_id":"00000000-0000-0000-0000-00000000000b","value":50}]'::jsonb);
  raise notice 'PASS: alice issued two bills for the settle-up checks';
end $$;

do $$
declare s public.settlements;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', true);
  -- bob is a debtor on both bills; he cannot confirm his own payment
  begin
    perform public.confirm_settlement('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b', 100);
    raise exception 'FAIL: debtor confirmed a payment from himself';
  exception when raise_exception then
    if sqlerrm like 'FAIL:%' then raise; end if;
    raise notice 'PASS: debtor cannot confirm his own payment (from_user = caller rejected)';
  end;

  -- the only settlement bob can record is one where he is the creditor
  s := public.confirm_settlement('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a', 100);
  if s.to_user_id <> '00000000-0000-0000-0000-00000000000b' or s.from_user_id <> '00000000-0000-0000-0000-00000000000a' then
    raise exception 'FAIL: settlement not recorded with the caller as creditor';
  end if;
  if (select bs.is_paid from public.bill_shares bs join public.bills b on b.id = bs.bill_id where b.title = 'Settle 1' and bs.user_id = '00000000-0000-0000-0000-00000000000b') or (select bs.is_paid from public.bill_shares bs join public.bills b on b.id = bs.bill_id where b.title = 'Settle 2' and bs.user_id = '00000000-0000-0000-0000-00000000000b') then
    raise exception 'FAIL: bob''s own shares were marked paid by his confirmation';
  end if;
  raise notice 'PASS: debtor confirming only records a payment TO himself and leaves his own debts unpaid';
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000d","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000d', true);
  begin
    perform public.confirm_settlement('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b', 100);
    raise exception 'FAIL: outsider confirmed a settlement';
  exception when insufficient_privilege then
    raise notice 'PASS: a non-member cannot confirm a settlement';
  end;
end $$;

do $$
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  begin
    perform public.confirm_settlement('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000d', 100);
    raise exception 'FAIL: settlement accepted from a non-member';
  exception when raise_exception then
    if sqlerrm like 'FAIL:%' then raise; end if;
    raise notice 'PASS: settlement from a non-member is rejected';
  end;
  begin
    perform public.confirm_settlement('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b', 0);
    raise exception 'FAIL: zero settlement accepted';
  exception when raise_exception then
    if sqlerrm like 'FAIL:%' then raise; end if;
    raise notice 'PASS: settlement amount must be greater than 0';
  end;
end $$;

do $$
declare s public.settlements; p1 boolean; p2 boolean; pc boolean;
begin
  perform set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true),
                 set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', true);
  s := public.confirm_settlement('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b', 100);
  if s.to_user_id <> '00000000-0000-0000-0000-00000000000a' or s.from_user_id <> '00000000-0000-0000-0000-00000000000b' or s.amount <> 100 then
    raise exception 'FAIL: settlement not recorded bob -> alice';
  end if;
  raise notice 'PASS: creditor confirmation records from = payer, to = creditor';

  select is_paid into p1 from public.bill_shares bs join public.bills b on b.id = bs.bill_id
    where b.title = 'Settle 1' and bs.user_id = '00000000-0000-0000-0000-00000000000b';
  select is_paid into p2 from public.bill_shares bs join public.bills b on b.id = bs.bill_id
    where b.title = 'Settle 2' and bs.user_id = '00000000-0000-0000-0000-00000000000b';
  select is_paid into pc from public.bill_shares bs join public.bills b on b.id = bs.bill_id
    where b.title = 'Settle 1' and bs.user_id = '00000000-0000-0000-0000-00000000000c';
  if not p1 then raise exception 'FAIL: the oldest share was not marked paid'; end if;
  if p2 then raise exception 'FAIL: a share beyond the settled amount was marked paid'; end if;
  if pc then raise exception 'FAIL: another member''s share was marked paid'; end if;
  raise notice 'PASS: confirmation marks the payer''s oldest share paid, and only up to the amount';
end $$;

do $$ begin raise notice 'All settlement and split checks passed.'; end $$;

rollback;
