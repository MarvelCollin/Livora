alter table public.dictionary_entries
  add column if not exists language text not null default 'en';

alter table public.dictionary_entries
  alter column language set default 'en';

notify pgrst, 'reload schema';
