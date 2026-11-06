-- RM-0027: photo attachments for maintenance requests and bills.
--
-- Photos live in the private Storage bucket "attachments" under
--   <household_id>/<kind>/<uuid>.jpg      (kind: maintenance | bill)
-- A signed-in user can read, upload and delete objects only under a
-- household they belong to. Everyone else is refused: Storage answers 403
-- because the RLS check on storage.objects fails.
--
-- public.attachments links each stored photo to exactly one maintenance
-- request or bill in the same household, so the app can list a request's
-- photos and build signed URLs for them.

-- 1. Bucket -----------------------------------------------------------------
-- Private (no public URLs), JPEG only, 1 MiB per object. The app compresses
-- photos below 1 MB before uploading.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('attachments', 'attachments', false, 1048576, array['image/jpeg'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- True when the first folder of an object path is a household the caller
-- belongs to. Compares as text so a path whose first segment is not a uuid
-- is refused instead of raising a cast error.
create function public.is_attachment_path_member(object_name text)
returns boolean
language sql
security definer
stable
set search_path = public
as $$
  select exists (
    select 1 from public.household_members
    where household_id::text = (storage.foldername(object_name))[1]
      and user_id = auth.uid()
  );
$$;

revoke all on function public.is_attachment_path_member(text) from public, anon;
grant execute on function public.is_attachment_path_member(text) to authenticated;

-- 2. storage.objects policies -----------------------------------------------
create policy "attachments: members read"
  on storage.objects for select to authenticated
  using (bucket_id = 'attachments' and public.is_attachment_path_member(name));

create policy "attachments: members upload"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'attachments'
    and public.is_attachment_path_member(name)
    and (storage.foldername(name))[2] in ('maintenance', 'bill')
  );

create policy "attachments: members delete"
  on storage.objects for delete to authenticated
  using (bucket_id = 'attachments' and public.is_attachment_path_member(name));

-- No update policy: photos are never overwritten (uploads use upsert = false).

-- 3. attachments table ------------------------------------------------------
create table public.attachments (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  maintenance_request_id uuid references public.maintenance_requests (id) on delete cascade,
  bill_id uuid references public.bills (id) on delete cascade,
  storage_path text not null unique,
  uploaded_by uuid not null default auth.uid() references public.profiles (id),
  created_at timestamptz not null default now(),
  constraint attachments_one_parent check (num_nonnulls(maintenance_request_id, bill_id) = 1),
  constraint attachments_path_in_household check (storage_path like household_id::text || '/%')
);

create index attachments_maintenance_request_id_idx
  on public.attachments (maintenance_request_id) where maintenance_request_id is not null;
create index attachments_bill_id_idx
  on public.attachments (bill_id) where bill_id is not null;

alter table public.attachments enable row level security;

create policy "attachments: members read" on public.attachments
  for select using (public.is_household_member(household_id));

-- The parent must belong to the same household as the attachment.
create policy "attachments: members add their own" on public.attachments
  for insert with check (
    public.is_household_member(household_id)
    and uploaded_by = auth.uid()
    and (
      (maintenance_request_id is not null and exists (
        select 1 from public.maintenance_requests m
        where m.id = maintenance_request_id and m.household_id = attachments.household_id))
      or
      (bill_id is not null and exists (
        select 1 from public.bills b
        where b.id = bill_id and b.household_id = attachments.household_id))
    )
  );

create policy "attachments: uploader deletes" on public.attachments
  for delete using (uploaded_by = auth.uid());

-- No update policy: an attachment row is never edited.
