alter table public.dictionary_entries
  add column if not exists example_id text not null default '';

notify pgrst, 'reload schema';
