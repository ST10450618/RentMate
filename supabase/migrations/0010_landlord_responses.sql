-- RM-0009: only the person who logged a maintenance request, or the person who
-- sent it to the landlord, may change its status.
--
-- Before this, "maintenance_requests: members only" allowed every housemate to
-- update every column of every request directly. Status now changes only
-- through send_to_landlord and advance_maintenance_status, which check the
-- caller.

alter table public.maintenance_requests
  add column sent_by_user_id uuid references public.profiles (id);

drop policy "maintenance_requests: members only" on public.maintenance_requests;

create policy "maintenance_requests: members read" on public.maintenance_requests
  for select using (public.is_household_member(household_id));

create policy "maintenance_requests: members log their own" on public.maintenance_requests
  for insert with check (
    public.is_household_member(household_id) and raised_by_user_id = auth.uid()
  );

create policy "maintenance_requests: creator deletes" on public.maintenance_requests
  for delete using (raised_by_user_id = auth.uid());

-- No update policy: direct updates are refused for everyone.

-- US-10: batches every open request into one message, marking each sent and
-- recording who sent it, so that person can record the landlord's response.
create or replace function public.send_to_landlord(household_id_in uuid)
returns int
language plpgsql
security definer
set search_path = public
as $$
declare
  updated_count int;
begin
  if not public.is_household_member(household_id_in) then
    raise exception 'Not a member of this household';
  end if;

  update public.maintenance_requests
  set status = 'Sent', sent_to_landlord_at = now(), sent_by_user_id = auth.uid()
  where household_id = household_id_in and status = 'Open';

  get diagnostics updated_count = row_count;
  return updated_count;
end;
$$;

-- Moves one request a single step: Open -> Sent -> Acknowledged -> Resolved.
-- Open -> Sent: only the person who logged it.
-- Recording the landlord's response: the sender, or the person who logged it.
create function public.advance_maintenance_status(request_id_in uuid)
returns public.maintenance_requests
language plpgsql
security definer
set search_path = public
as $$
declare
  r public.maintenance_requests;
  caller uuid := auth.uid();
begin
  select * into r from public.maintenance_requests where id = request_id_in for update;
  if not found or not public.is_household_member(r.household_id) then
    raise exception 'Maintenance request not found';
  end if;

  if r.status = 'Open' then
    if caller is distinct from r.raised_by_user_id then
      raise exception 'Only the person who logged this request can send it'
        using errcode = 'insufficient_privilege';
    end if;
    update public.maintenance_requests
    set status = 'Sent', sent_to_landlord_at = now(), sent_by_user_id = caller
    where id = r.id returning * into r;
  elsif r.status in ('Sent', 'Acknowledged') then
    if caller is distinct from r.sent_by_user_id and caller is distinct from r.raised_by_user_id then
      raise exception 'Only the person who sent this request can record the landlord''s response'
        using errcode = 'insufficient_privilege';
    end if;
    if r.status = 'Sent' then
      update public.maintenance_requests
      set status = 'Acknowledged', acknowledged_at = now()
      where id = r.id returning * into r;
    else
      update public.maintenance_requests
      set status = 'Resolved', resolved_at = now()
      where id = r.id returning * into r;
    end if;
  else
    raise exception 'This request is already resolved';
  end if;

  return r;
end;
$$;

revoke all on function public.advance_maintenance_status(uuid) from public;
grant execute on function public.advance_maintenance_status(uuid) to authenticated;
