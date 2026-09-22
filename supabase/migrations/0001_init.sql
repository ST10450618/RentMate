-- Ports the EF Core model from RentMate.Api/RentMate.Api/Models onto Postgres.
-- auth.users is Supabase's own table; public.profiles holds the app fields
-- that used to live on RentMate.Api's User entity.

create extension if not exists "pgcrypto";

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  email text not null,
  display_name text not null,
  show_leaderboard boolean not null default true,
  preferred_language text not null default 'en' check (preferred_language in ('en', 'af', 'xh')),
  notify_bills boolean not null default true,
  notify_chores boolean not null default true,
  notify_shopping_list boolean not null default true,
  notify_maintenance boolean not null default true,
  biometric_enabled boolean not null default false,
  fcm_token text,
  created_at timestamptz not null default now()
);

-- Creates a profile row on first Google sign-in.
create function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, email, display_name)
  values (
    new.id,
    new.email,
    coalesce(new.raw_user_meta_data ->> 'full_name', new.email)
  );
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute procedure public.handle_new_user();

create table public.households (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  invite_code text not null unique,
  landlord_name text,
  landlord_email text,
  created_at timestamptz not null default now()
);

create table public.household_members (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  joined_at timestamptz not null default now(),
  unique (household_id, user_id)
);

-- US-6, US-7, US-11
create table public.bills (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  title text not null,
  amount numeric(10, 2) not null check (amount > 0),
  due_date timestamptz not null,
  split_method text not null default 'Equal' check (split_method in ('Equal', 'Percentage', 'ExactAmount')),
  issued_by_user_id uuid not null references public.profiles (id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.bill_shares (
  id uuid primary key default gen_random_uuid(),
  bill_id uuid not null references public.bills (id) on delete cascade,
  user_id uuid not null references public.profiles (id),
  amount_owed numeric(10, 2) not null check (amount_owed >= 0),
  is_paid boolean not null default false,
  paid_at timestamptz,
  client_recorded_at timestamptz
);

-- US-9, US-12
create table public.chores (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  title text not null,
  recurrence text not null default 'Weekly' check (recurrence in ('Once', 'Weekly', 'Monthly')),
  point_value int not null default 1,
  rotation_order uuid[] not null default '{}',
  current_rotation_index int not null default 0,
  created_at timestamptz not null default now()
);

create table public.chore_completions (
  id uuid primary key default gen_random_uuid(),
  chore_id uuid not null references public.chores (id) on delete cascade,
  completed_by_user_id uuid not null references public.profiles (id),
  completed_at timestamptz not null default now(),
  offline_timestamp timestamptz,
  points_awarded int not null
);

-- US-13
create table public.shopping_items (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  name text not null,
  added_by_user_id uuid not null references public.profiles (id),
  is_purchased boolean not null default false,
  purchased_by_user_id uuid references public.profiles (id),
  purchased_at timestamptz,
  client_recorded_at timestamptz,
  converted_to_bill_id uuid references public.bills (id),
  created_at timestamptz not null default now()
);

-- US-10
create table public.maintenance_requests (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  raised_by_user_id uuid not null references public.profiles (id),
  title text not null,
  description text not null,
  photo_url text,
  category text not null check (category in ('Plumbing', 'Electrical', 'Structural', 'Appliance', 'Other')),
  urgency text not null check (urgency in ('Low', 'Medium', 'High')),
  status text not null default 'Open' check (status in ('Open', 'Sent', 'Acknowledged', 'Resolved')),
  created_at timestamptz not null default now(),
  sent_to_landlord_at timestamptz,
  acknowledged_at timestamptz,
  resolved_at timestamptz
);

-- US-11
create table public.settlements (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  from_user_id uuid not null references public.profiles (id),
  to_user_id uuid not null references public.profiles (id),
  amount numeric(10, 2) not null check (amount > 0),
  settled_at timestamptz not null default now()
);

-- Row Level Security. Every table checks household membership, since the
-- app talks straight to PostgREST with the user's own JWT.
create function public.is_household_member(target_household_id uuid)
returns boolean
language sql
security definer
stable
as $$
  select exists (
    select 1 from public.household_members
    where household_id = target_household_id and user_id = auth.uid()
  );
$$;

alter table public.profiles enable row level security;
alter table public.households enable row level security;
alter table public.household_members enable row level security;
alter table public.bills enable row level security;
alter table public.bill_shares enable row level security;
alter table public.chores enable row level security;
alter table public.chore_completions enable row level security;
alter table public.shopping_items enable row level security;
alter table public.maintenance_requests enable row level security;
alter table public.settlements enable row level security;

create policy "read own profile" on public.profiles
  for select using (id = auth.uid());
create policy "update own profile" on public.profiles
  for update using (id = auth.uid());

create policy "read own memberships" on public.household_members
  for select using (user_id = auth.uid());
create policy "join a household" on public.household_members
  for insert with check (user_id = auth.uid());

create policy "read households you belong to" on public.households
  for select using (public.is_household_member(id));
create policy "create a household" on public.households
  for insert with check (true);
create policy "update your household" on public.households
  for update using (public.is_household_member(id));

create policy "bills: members only" on public.bills
  for all using (public.is_household_member(household_id));

create policy "bill_shares: members only" on public.bill_shares
  for all using (public.is_household_member((select household_id from public.bills where id = bill_id)));

create policy "chores: members only" on public.chores
  for all using (public.is_household_member(household_id));

create policy "chore_completions: members only" on public.chore_completions
  for all using (public.is_household_member((select household_id from public.chores where id = chore_id)));

create policy "shopping_items: members only" on public.shopping_items
  for all using (public.is_household_member(household_id));

create policy "maintenance_requests: members only" on public.maintenance_requests
  for all using (public.is_household_member(household_id));

create policy "settlements: members only" on public.settlements
  for all using (public.is_household_member(household_id));
