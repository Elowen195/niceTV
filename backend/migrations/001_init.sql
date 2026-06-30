create extension if not exists pgcrypto;

create table if not exists users (
    id uuid primary key default gen_random_uuid(),
    username varchar(40) not null,
    email varchar(255),
    password_hash text not null,
    avatar_url text,
    bio text,
    role text not null default 'user' check (role in ('user', 'moderator', 'admin')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists users_username_lower_idx on users (lower(username));
create unique index if not exists users_email_lower_idx on users (lower(email)) where email is not null;

create table if not exists refresh_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    token_hash text not null unique,
    device_name text,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists refresh_tokens_user_id_idx on refresh_tokens(user_id);

create table if not exists video_refs (
    id uuid primary key default gen_random_uuid(),
    source text not null default 'supjav',
    source_url text not null unique,
    title text not null default '',
    cover_url text,
    maker text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists favorites (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    video_ref_id uuid not null references video_refs(id) on delete cascade,
    title_snapshot text not null default '',
    cover_snapshot text,
    maker_snapshot text,
    tags_snapshot jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique(user_id, video_ref_id)
);

create index if not exists favorites_user_updated_idx on favorites(user_id, updated_at desc);
create index if not exists favorites_user_alive_idx on favorites(user_id, updated_at desc) where deleted_at is null;

create table if not exists comments (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    video_ref_id uuid not null references video_refs(id) on delete cascade,
    parent_id uuid references comments(id) on delete set null,
    body text not null,
    status text not null default 'visible' check (status in ('visible', 'hidden', 'deleted', 'pending')),
    like_count int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index if not exists comments_video_created_idx on comments(video_ref_id, created_at desc) where status = 'visible';
create index if not exists comments_user_idx on comments(user_id, created_at desc);

create table if not exists comment_reactions (
    user_id uuid not null references users(id) on delete cascade,
    comment_id uuid not null references comments(id) on delete cascade,
    reaction text not null default 'like' check (reaction = 'like'),
    created_at timestamptz not null default now(),
    primary key(user_id, comment_id)
);

