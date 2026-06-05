alter table public.todos
  add column if not exists has_timer boolean not null default false;

notify pgrst, 'reload schema';
