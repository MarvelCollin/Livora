alter table public.dictionary_entries
  add column if not exists synonyms text not null default '';

notify pgrst, 'reload schema';
