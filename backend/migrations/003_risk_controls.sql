alter table users
    add column if not exists status text not null default 'active',
    add column if not exists muted_until timestamptz,
    add column if not exists banned_at timestamptz,
    add column if not exists ban_reason text;

alter table users
    drop constraint if exists users_status_check,
    add constraint users_status_check check (status in ('active', 'banned'));

create index if not exists users_status_idx on users(status);
create index if not exists users_muted_until_idx on users(muted_until) where muted_until is not null;

alter table collections
    add column if not exists status text not null default 'visible',
    add column if not exists hidden_at timestamptz;

alter table collections
    drop constraint if exists collections_status_check,
    add constraint collections_status_check check (status in ('visible', 'hidden', 'deleted'));

drop index if exists collections_public_updated_idx;
create index if not exists collections_public_updated_idx
    on collections(updated_at desc)
    where visibility = 'public' and status = 'visible';

create index if not exists collections_owner_status_updated_idx
    on collections(owner_id, status, updated_at desc);

create table if not exists moderation_actions (
    id uuid primary key default gen_random_uuid(),
    actor_id uuid not null references users(id) on delete cascade,
    target_type text not null check (target_type in ('user', 'comment', 'collection')),
    target_id uuid not null,
    action text not null,
    reason text not null default '',
    created_at timestamptz not null default now()
);

create index if not exists moderation_actions_created_idx on moderation_actions(created_at desc);
create index if not exists moderation_actions_target_idx on moderation_actions(target_type, target_id, created_at desc);
