create table if not exists public.people_backup (
  id text not null,
  generation bigint not null,
  part integer not null,
  parts integer not null,
  persons integer not null default 0,
  photos integer not null default 0,
  faces integer not null default 0,
  data text not null,
  primary key (id, generation, part)
);

comment on table public.people_backup is 'Encrypted backup of the Livora people index. data holds base64 chunks of an AES-GCM encrypted archive. No photos are stored.';

grant all on table public.people_backup to anon, authenticated, service_role;

notify pgrst, 'reload schema';
