alter table public.dictionary_entries
  alter column id set default gen_random_uuid();

notify pgrst, 'reload schema';
