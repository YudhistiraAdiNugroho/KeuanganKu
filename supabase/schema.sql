-- ============================================================
-- KeuanganKu — Schema Supabase
-- Jalankan di: Supabase Dashboard > SQL Editor > New query > Run
-- ============================================================

-- ---------- TABEL ----------
create table if not exists accounts (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  type text not null default 'cash',           -- cash | bank | ewallet | other
  balance numeric not null default 0,
  icon text not null default '💰',
  created_at timestamptz not null default now()
);

create table if not exists categories (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  type text not null default 'expense',        -- expense | income
  icon text not null default '📦',
  created_at timestamptz not null default now()
);

create table if not exists transactions (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  account_id bigint not null references accounts(id) on delete restrict,
  to_account_id bigint references accounts(id) on delete restrict,
  category_id bigint references categories(id) on delete set null,
  type text not null check (type in ('income','expense','transfer')),
  amount numeric not null check (amount > 0),
  date date not null,
  note text,
  receipt_url text,
  created_at timestamptz not null default now(),
  check (type != 'transfer' or (to_account_id is not null and to_account_id != account_id))
);

create table if not exists budgets (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  category_id bigint not null references categories(id) on delete cascade,
  amount numeric not null check (amount > 0),
  month text not null,                          -- yyyy-MM
  year int not null,
  created_at timestamptz not null default now(),
  unique (user_id, category_id, month)
);

create table if not exists recurring_transactions (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  account_id bigint not null references accounts(id) on delete restrict,
  category_id bigint references categories(id) on delete set null,
  type text not null check (type in ('income','expense')),
  amount numeric not null check (amount > 0),
  note text,
  frequency text not null default 'monthly' check (frequency in ('daily','weekly','monthly','yearly')),
  next_date date not null,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists saving_goals (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  target_amount numeric not null check (target_amount > 0),
  current_amount numeric not null default 0 check (current_amount >= 0),
  icon text not null default '🐷',
  created_at timestamptz not null default now()
);

-- Index untuk query cepat
create index if not exists idx_tx_user_date on transactions(user_id, date desc);
create index if not exists idx_tx_account on transactions(account_id);
create index if not exists idx_budgets_user_month on budgets(user_id, month);
create index if not exists idx_recurring_user_next on recurring_transactions(user_id, next_date);

-- ---------- ROW LEVEL SECURITY ----------
alter table accounts enable row level security;
alter table categories enable row level security;
alter table transactions enable row level security;
alter table budgets enable row level security;
alter table recurring_transactions enable row level security;
alter table saving_goals enable row level security;

-- Kebijakan: user hanya bisa akses baris miliknya sendiri
create policy "own_rows" on accounts
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own_rows" on categories
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own_rows" on transactions
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own_rows" on budgets
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own_rows" on recurring_transactions
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own_rows" on saving_goals
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- ---------- STORAGE: bucket privat untuk foto struk ----------
insert into storage.buckets (id, name, public)
values ('receipts', 'receipts', false)
on conflict (id) do nothing;

-- User hanya bisa akses file di folder miliknya: receipts/{user_id}/...
create policy "own_receipts_read" on storage.objects
  for select using (
    bucket_id = 'receipts'
    and (storage.foldername(name))[1] = auth.uid()::text
  );
create policy "own_receipts_write" on storage.objects
  for insert with check (
    bucket_id = 'receipts'
    and (storage.foldername(name))[1] = auth.uid()::text
  );
create policy "own_receipts_update" on storage.objects
  for update using (
    bucket_id = 'receipts'
    and (storage.foldername(name))[1] = auth.uid()::text
  );
create policy "own_receipts_delete" on storage.objects
  for delete using (
    bucket_id = 'receipts'
    and (storage.foldername(name))[1] = auth.uid()::text
  );
