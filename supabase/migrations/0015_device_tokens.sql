-- RM-0021: Firebase Cloud Messaging (FCM) registration tokens, one row per device.
--
-- The app saves its token here after sign-in and whenever FCM issues a new
-- one. The push Edge Function (RM-0022) reads the rows of the people it
-- notifies; it runs with the service role, so RLS does not limit it.
--
-- profiles.fcm_token from 0001 holds only one token per user. This table
-- replaces it because one user can sign in on a phone and a tablet.
--
-- Data contract for RM-0022. Send a data-only FCM message with these keys:
--   channel  bills | chores | shopping | maintenance  (required)
--   title    notification title                       (required)
--   body     notification text                        (optional)
--   id       uuid of the bill, chore or request       (optional, for RM-0023 deep links)

create table public.device_tokens (
  token text primary key check (length(token) between 1 and 4096),
  user_id uuid not null references auth.users (id) on delete cascade,
  platform text not null default 'android' check (platform in ('android')),
  updated_at timestamptz not null default now()
);

create index device_tokens_user_id_idx on public.device_tokens (user_id);

alter table public.device_tokens enable row level security;

-- A user can see and delete only their own tokens. Writes go through the
-- functions below, so no insert or update policy exists.
create policy "device_tokens: select own"
  on public.device_tokens for select to authenticated
  using (user_id = auth.uid());

create policy "device_tokens: delete own"
  on public.device_tokens for delete to authenticated
  using (user_id = auth.uid());

-- Saves the caller's token. If another account signed in on this device
-- before, the token moves to the caller. Without this, that account would
-- still get the caller's pushes.
create function public.register_device_token(p_token text, p_platform text default 'android')
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'not signed in' using errcode = '28000';
  end if;

  insert into public.device_tokens (token, user_id, platform, updated_at)
  values (p_token, auth.uid(), p_platform, now())
  on conflict (token) do update
    set user_id = excluded.user_id,
        platform = excluded.platform,
        updated_at = now();
end;
$$;

-- Removes the caller's token at sign-out, so the device stops getting pushes
-- for that account. A token that belongs to someone else is left alone.
create function public.unregister_device_token(p_token text)
returns void
language sql
security definer
set search_path = public
as $$
  delete from public.device_tokens where token = p_token and user_id = auth.uid();
$$;

revoke all on function public.register_device_token(text, text) from public, anon;
revoke all on function public.unregister_device_token(text) from public, anon;
grant execute on function public.register_device_token(text, text) to authenticated;
grant execute on function public.unregister_device_token(text) to authenticated;
