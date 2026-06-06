create table if not exists public.dictionary_entries (
  id uuid primary key,
  word text not null,
  language text not null,
  translation text not null default '',
  description text not null default '',
  created_at bigint not null
);

grant all on table public.dictionary_entries to anon, authenticated, service_role;

notify pgrst, 'reload schema';
