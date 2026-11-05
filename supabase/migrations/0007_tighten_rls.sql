-- RM-0006: tighten RLS after the Part 2 feedback.
--
-- 1. Joining a household. The "join a household" policy let any signed-in
--    user insert a household_members row for any household id, skipping the
--    invite code. Joining now only happens through join_household(code) and
--    create_household(name), which are SECURITY DEFINER and bypass RLS.
-- 2. Bills. "for all" let any member edit or delete anyone's bill. Members
--    can still read every bill in their household and add their own, but only
--    the issuer can change or delete one.
-- 3. Bill shares. "for all" let any member flip is_paid on any share, or
--    rewrite amount_owed. Shares are written once by the bill's issuer when
--    the bill is created; after that, marking a share paid goes through
--    mark_share_paid(share_id), and settle-up goes through confirm_settlement.
--    Shares are removed only by the bill's on delete cascade.

-- 1. household_members ------------------------------------------------------
drop policy if exists "join a household" on public.household_members;

-- 2. bills ------------------------------------------------------------------
drop policy if exists "bills: members only" on public.bills;

create policy "bills: members read" on public.bills
  for select using (public.is_household_member(household_id));

create policy "bills: members add their own" on public.bills
  for insert with check (
    public.is_household_member(household_id)
    and issued_by_user_id = auth.uid()
  );

create policy "bills: issuer updates" on public.bills
  for update
  using (issued_by_user_id = auth.uid())
  with check (
    issued_by_user_id = auth.uid()
    and public.is_household_member(household_id)
  );

create policy "bills: issuer deletes" on public.bills
  for delete using (issued_by_user_id = auth.uid());

-- 3. bill_shares ------------------------------------------------------------
drop policy if exists "bill_shares: members only" on public.bill_shares;

create policy "bill_shares: members read" on public.bill_shares
  for select using (
    public.is_household_member((select household_id from public.bills where id = bill_id))
  );

-- Only the bill's issuer writes its shares, and only for household members.
create policy "bill_shares: issuer adds" on public.bill_shares
  for insert with check (
    exists (
      select 1
      from public.bills b
      join public.household_members hm
        on hm.household_id = b.household_id and hm.user_id = bill_shares.user_id
      where b.id = bill_shares.bill_id
        and b.issued_by_user_id = auth.uid()
    )
  );

-- No update or delete policy: with RLS on, direct updates and deletes match
-- no rows. Marking a share paid goes through this function instead.
--
-- Only the bill's issuer (the person who is owed) can mark a share paid. The
-- debtor cannot confirm their own payment.
create function public.mark_share_paid(share_id_in uuid)
returns public.bill_shares
language plpgsql
security definer
set search_path = public
as $$
declare
  target public.bill_shares;
  issuer uuid;
begin
  select * into target from public.bill_shares where id = share_id_in;
  select issued_by_user_id into issuer from public.bills where id = target.bill_id;

  -- Same error for "missing" and "not yours", so ids can't be probed.
  if target.id is null or auth.uid() is null
     or issuer <> auth.uid() then
    raise exception 'Share not found or not yours to mark paid'
      using errcode = '42501';
  end if;

  if target.is_paid then
    return target;
  end if;

  update public.bill_shares
     set is_paid = true, paid_at = now()
   where id = share_id_in
  returning * into target;

  return target;
end;
$$;

revoke all on function public.mark_share_paid(uuid) from public, anon;
grant execute on function public.mark_share_paid(uuid) to authenticated;
