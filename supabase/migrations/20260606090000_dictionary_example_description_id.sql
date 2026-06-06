alter table public.dictionary_entries
  add column if not exists description_id text not null default '',
  add column if not exists example text not null default '';

notify pgrst, 'reload schema';
