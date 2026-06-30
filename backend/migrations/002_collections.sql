create table if not exists collections (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references users(id) on delete cascade,
    title text not null,
    description text not null default '',
    cover_url text,
    visibility text not null default 'private' check (visibility in ('private', 'unlisted', 'public')),
    slug text not null unique,
    item_count int not null default 0,
    like_count int not null default 0,
    save_count int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists collections_owner_updated_idx on collections(owner_id, updated_at desc);
create index if not exists collections_public_updated_idx on collections(updated_at desc) where visibility = 'public';

create table if not exists collection_items (
    id uuid primary key default gen_random_uuid(),
    collection_id uuid not null references collections(id) on delete cascade,
    video_ref_id uuid not null references video_refs(id) on delete cascade,
    note text not null default '',
    position int not null default 0,
    created_at timestamptz not null default now(),
    unique(collection_id, video_ref_id)
);

create index if not exists collection_items_collection_position_idx on collection_items(collection_id, position asc, created_at asc);

create table if not exists collection_likes (
    user_id uuid not null references users(id) on delete cascade,
    collection_id uuid not null references collections(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key(user_id, collection_id)
);

create table if not exists collection_saves (
    user_id uuid not null references users(id) on delete cascade,
    collection_id uuid not null references collections(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key(user_id, collection_id)
);
