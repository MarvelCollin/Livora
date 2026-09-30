alter table public.dictionary_entries
  add column if not exists correct_count integer not null default 0,
  add column if not exists wrong_count integer not null default 0;

notify pgrst, 'reload schema';
