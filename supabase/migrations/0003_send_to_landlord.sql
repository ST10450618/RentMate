-- US-10: batches every open request into one message, marking each sent.
create function public.send_to_landlord(household_id_in uuid)
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
  set status = 'Sent', sent_to_landlord_at = now()
  where household_id = household_id_in and status = 'Open';

  get diagnostics updated_count = row_count;
  return updated_count;
end;
$$;
