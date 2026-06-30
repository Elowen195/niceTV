package store

import (
	"context"
	"encoding/json"
	"errors"
	"strings"
	"time"

	"nicetv/backend/internal/models"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Postgres struct {
	pool *pgxpool.Pool
}

func OpenPostgres(ctx context.Context, databaseURL string) (*Postgres, error) {
	pool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		return nil, err
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, err
	}
	return &Postgres{pool: pool}, nil
}

func (p *Postgres) Close() {
	p.pool.Close()
}

func (p *Postgres) CreateUser(ctx context.Context, username string, email *string, passwordHash string) (models.User, error) {
	row := p.pool.QueryRow(ctx, `
		insert into users (username, email, password_hash)
		values ($1, $2, $3)
		returning id, username, email, avatar_url, bio, role, created_at, updated_at
	`, username, email, passwordHash)
	user, err := scanUser(row)
	if err != nil {
		return models.User{}, mapError(err)
	}
	return user, nil
}

func (p *Postgres) GetUserByLogin(ctx context.Context, login string) (models.UserWithPassword, error) {
	row := p.pool.QueryRow(ctx, `
		select id, username, email, avatar_url, bio, role, created_at, updated_at, password_hash
		from users
		where lower(username) = lower($1) or lower(coalesce(email, '')) = lower($1)
	`, login)
	var user models.UserWithPassword
	err := row.Scan(
		&user.ID,
		&user.Username,
		&user.Email,
		&user.AvatarURL,
		&user.Bio,
		&user.Role,
		&user.CreatedAt,
		&user.UpdatedAt,
		&user.PasswordHash,
	)
	if err != nil {
		return models.UserWithPassword{}, mapError(err)
	}
	return user, nil
}

func (p *Postgres) GetUserByID(ctx context.Context, id string) (models.User, error) {
	row := p.pool.QueryRow(ctx, `
		select id, username, email, avatar_url, bio, role, created_at, updated_at
		from users
		where id = $1
	`, id)
	user, err := scanUser(row)
	if err != nil {
		return models.User{}, mapError(err)
	}
	return user, nil
}

func (p *Postgres) UpdateUser(ctx context.Context, id string, username, email, avatarURL, bio *string) (models.User, error) {
	row := p.pool.QueryRow(ctx, `
		update users
		set username = coalesce(nullif($2, ''), username),
		    email = case when $3::text is null then email when $3 = '' then null else $3 end,
		    avatar_url = case when $4::text is null then avatar_url when $4 = '' then null else $4 end,
		    bio = case when $5::text is null then bio when $5 = '' then null else $5 end,
		    updated_at = now()
		where id = $1
		returning id, username, email, avatar_url, bio, role, created_at, updated_at
	`, id, username, email, avatarURL, bio)
	user, err := scanUser(row)
	if err != nil {
		return models.User{}, mapError(err)
	}
	return user, nil
}

func (p *Postgres) CreateRefreshToken(ctx context.Context, userID, tokenHash, deviceName string, expiresAt time.Time) (models.RefreshToken, error) {
	row := p.pool.QueryRow(ctx, `
		insert into refresh_tokens (user_id, token_hash, device_name, expires_at)
		values ($1, $2, nullif($3, ''), $4)
		returning id, user_id, token_hash, expires_at, revoked_at, created_at
	`, userID, tokenHash, deviceName, expiresAt)
	token, err := scanRefreshToken(row)
	if err != nil {
		return models.RefreshToken{}, mapError(err)
	}
	return token, nil
}

func (p *Postgres) FindRefreshToken(ctx context.Context, tokenHash string) (models.RefreshToken, error) {
	row := p.pool.QueryRow(ctx, `
		select id, user_id, token_hash, expires_at, revoked_at, created_at
		from refresh_tokens
		where token_hash = $1
	`, tokenHash)
	token, err := scanRefreshToken(row)
	if err != nil {
		return models.RefreshToken{}, mapError(err)
	}
	if token.RevokedAt != nil || time.Now().UTC().After(token.ExpiresAt) {
		return models.RefreshToken{}, ErrUnauthorized
	}
	return token, nil
}

func (p *Postgres) RevokeRefreshToken(ctx context.Context, tokenHash string) error {
	_, err := p.pool.Exec(ctx, `
		update refresh_tokens set revoked_at = coalesce(revoked_at, now())
		where token_hash = $1
	`, tokenHash)
	return mapError(err)
}

func (p *Postgres) UpsertVideoRef(ctx context.Context, source, sourceURL, title string, coverURL, maker *string) (models.VideoRef, error) {
	if source == "" {
		source = "supjav"
	}
	row := p.pool.QueryRow(ctx, `
		insert into video_refs (source, source_url, title, cover_url, maker)
		values ($1, $2, $3, $4, $5)
		on conflict (source_url) do update
		set source = excluded.source,
		    title = coalesce(nullif(excluded.title, ''), video_refs.title),
		    cover_url = coalesce(excluded.cover_url, video_refs.cover_url),
		    maker = coalesce(excluded.maker, video_refs.maker),
		    updated_at = now()
		returning id, source, source_url, title, cover_url, maker, created_at, updated_at
	`, source, sourceURL, title, coverURL, maker)
	video, err := scanVideoRef(row)
	if err != nil {
		return models.VideoRef{}, mapError(err)
	}
	return video, nil
}

func (p *Postgres) ListFavorites(ctx context.Context, userID string, limit int) ([]models.Favorite, error) {
	if limit <= 0 || limit > 100 {
		limit = 30
	}
	rows, err := p.pool.Query(ctx, favoriteSelectSQL+`
		where f.user_id = $1 and f.deleted_at is null
		order by f.updated_at desc
		limit $2
	`, userID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanFavorites(rows)
}

func (p *Postgres) UpsertFavorite(ctx context.Context, userID, videoRefID string, snapshot models.FavoriteSnapshot, updatedAt time.Time) (models.Favorite, error) {
	if updatedAt.IsZero() {
		updatedAt = time.Now().UTC()
	}
	existing, err := p.favoriteByUserVideo(ctx, userID, videoRefID)
	if err == nil && existing.UpdatedAt.After(updatedAt) {
		return existing, nil
	} else if err != nil && !errors.Is(err, ErrNotFound) {
		return models.Favorite{}, err
	}

	tags, err := json.Marshal(snapshot.Tags)
	if err != nil {
		return models.Favorite{}, err
	}
	row := p.pool.QueryRow(ctx, `
		insert into favorites (
			user_id, video_ref_id, title_snapshot, cover_snapshot,
			maker_snapshot, tags_snapshot, created_at, updated_at, deleted_at
		)
		values ($1, $2, $3, $4, $5, $6, $7, $7, null)
		on conflict (user_id, video_ref_id) do update
		set title_snapshot = excluded.title_snapshot,
		    cover_snapshot = excluded.cover_snapshot,
		    maker_snapshot = excluded.maker_snapshot,
		    tags_snapshot = excluded.tags_snapshot,
		    updated_at = excluded.updated_at,
		    deleted_at = null
		returning id
	`, userID, videoRefID, snapshot.Title, snapshot.CoverURL, snapshot.Maker, tags, updatedAt)
	var id string
	if err := row.Scan(&id); err != nil {
		return models.Favorite{}, mapError(err)
	}
	return p.favoriteByID(ctx, id)
}

func (p *Postgres) DeleteFavorite(ctx context.Context, userID, videoRefID string, updatedAt time.Time) (models.Favorite, error) {
	if updatedAt.IsZero() {
		updatedAt = time.Now().UTC()
	}
	existing, err := p.favoriteByUserVideo(ctx, userID, videoRefID)
	if err != nil {
		return models.Favorite{}, err
	}
	if existing.UpdatedAt.After(updatedAt) {
		return existing, nil
	}
	row := p.pool.QueryRow(ctx, `
		update favorites
		set updated_at = $3, deleted_at = $3
		where user_id = $1 and video_ref_id = $2
		returning id
	`, userID, videoRefID, updatedAt)
	var id string
	if err := row.Scan(&id); err != nil {
		return models.Favorite{}, mapError(err)
	}
	return p.favoriteByID(ctx, id)
}

func (p *Postgres) SyncFavorites(ctx context.Context, userID string, since *time.Time, changes []models.FavoriteSyncChange) (models.FavoriteSyncResult, error) {
	for _, change := range changes {
		source := change.Source
		if source == "" {
			source = "supjav"
		}
		video, err := p.UpsertVideoRef(ctx, source, change.SourceURL, change.Snapshot.Title, change.Snapshot.CoverURL, change.Snapshot.Maker)
		if err != nil {
			return models.FavoriteSyncResult{}, err
		}
		switch change.Op {
		case "upsert", "add", "":
			if _, err := p.UpsertFavorite(ctx, userID, video.ID, change.Snapshot, change.UpdatedAt); err != nil {
				return models.FavoriteSyncResult{}, err
			}
		case "delete", "remove":
			if _, err := p.DeleteFavorite(ctx, userID, video.ID, change.UpdatedAt); err != nil && !errors.Is(err, ErrNotFound) {
				return models.FavoriteSyncResult{}, err
			}
		}
	}

	var rows pgx.Rows
	var err error
	if since == nil {
		rows, err = p.pool.Query(ctx, favoriteSelectSQL+`
			where f.user_id = $1
			order by f.updated_at asc
			limit 500
		`, userID)
	} else {
		rows, err = p.pool.Query(ctx, favoriteSelectSQL+`
			where f.user_id = $1 and f.updated_at > $2
			order by f.updated_at asc
			limit 500
		`, userID, *since)
	}
	if err != nil {
		return models.FavoriteSyncResult{}, err
	}
	defer rows.Close()
	favorites, err := scanFavorites(rows)
	if err != nil {
		return models.FavoriteSyncResult{}, err
	}
	return models.FavoriteSyncResult{
		ServerTime: time.Now().UTC(),
		Changes:    favorites,
	}, nil
}

func (p *Postgres) ListComments(ctx context.Context, videoRefID string, limit int, cursor *time.Time) ([]models.Comment, error) {
	if limit <= 0 || limit > 100 {
		limit = 30
	}
	var rows pgx.Rows
	var err error
	if cursor == nil {
		rows, err = p.pool.Query(ctx, commentSelectSQL+`
			where c.video_ref_id = $1 and c.status = 'visible'
			order by c.created_at desc
			limit $2
		`, videoRefID, limit)
	} else {
		rows, err = p.pool.Query(ctx, commentSelectSQL+`
			where c.video_ref_id = $1 and c.status = 'visible' and c.created_at < $2
			order by c.created_at desc
			limit $3
		`, videoRefID, *cursor, limit)
	}
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanComments(rows)
}

func (p *Postgres) CreateComment(ctx context.Context, userID, videoRefID string, parentID *string, body string) (models.Comment, error) {
	row := p.pool.QueryRow(ctx, `
		insert into comments (user_id, video_ref_id, parent_id, body)
		values ($1, $2, $3, $4)
		returning id
	`, userID, videoRefID, parentID, body)
	var id string
	if err := row.Scan(&id); err != nil {
		return models.Comment{}, mapError(err)
	}
	return p.commentByID(ctx, id)
}

func (p *Postgres) UpdateComment(ctx context.Context, userID, commentID, body string) (models.Comment, error) {
	row := p.pool.QueryRow(ctx, `
		update comments
		set body = $3, updated_at = now()
		where id = $1 and user_id = $2 and status = 'visible'
		returning id
	`, commentID, userID, body)
	var id string
	if err := row.Scan(&id); err != nil {
		return models.Comment{}, mapError(err)
	}
	return p.commentByID(ctx, id)
}

func (p *Postgres) DeleteComment(ctx context.Context, userID, commentID string) error {
	tag, err := p.pool.Exec(ctx, `
		update comments
		set status = 'deleted', deleted_at = now(), updated_at = now()
		where id = $1 and user_id = $2 and status <> 'deleted'
	`, commentID, userID)
	if err != nil {
		return mapError(err)
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

func (p *Postgres) LikeComment(ctx context.Context, userID, commentID string) (models.Comment, error) {
	tx, err := p.pool.Begin(ctx)
	if err != nil {
		return models.Comment{}, err
	}
	defer tx.Rollback(ctx)

	tag, err := tx.Exec(ctx, `
		insert into comment_reactions (user_id, comment_id, reaction)
		values ($1, $2, 'like')
		on conflict do nothing
	`, userID, commentID)
	if err != nil {
		return models.Comment{}, mapError(err)
	}
	if tag.RowsAffected() > 0 {
		if _, err := tx.Exec(ctx, `update comments set like_count = like_count + 1 where id = $1`, commentID); err != nil {
			return models.Comment{}, err
		}
	}
	if err := tx.Commit(ctx); err != nil {
		return models.Comment{}, err
	}
	return p.commentByID(ctx, commentID)
}

func (p *Postgres) UnlikeComment(ctx context.Context, userID, commentID string) (models.Comment, error) {
	tx, err := p.pool.Begin(ctx)
	if err != nil {
		return models.Comment{}, err
	}
	defer tx.Rollback(ctx)

	tag, err := tx.Exec(ctx, `
		delete from comment_reactions
		where user_id = $1 and comment_id = $2
	`, userID, commentID)
	if err != nil {
		return models.Comment{}, mapError(err)
	}
	if tag.RowsAffected() > 0 {
		if _, err := tx.Exec(ctx, `update comments set like_count = greatest(like_count - 1, 0) where id = $1`, commentID); err != nil {
			return models.Comment{}, err
		}
	}
	if err := tx.Commit(ctx); err != nil {
		return models.Comment{}, err
	}
	return p.commentByID(ctx, commentID)
}

func (p *Postgres) CreateCollection(ctx context.Context, ownerID, title, description string, coverURL *string, visibility string) (models.Collection, error) {
	if visibility == "" {
		visibility = "private"
	}
	for attempts := 0; attempts < 3; attempts++ {
		slug := newCollectionSlug()
		row := p.pool.QueryRow(ctx, `
			insert into collections (owner_id, title, description, cover_url, visibility, slug)
			values ($1, $2, $3, $4, $5, $6)
			returning id
		`, ownerID, title, description, coverURL, visibility, slug)
		var id string
		if err := row.Scan(&id); err != nil {
			mapped := mapError(err)
			if errors.Is(mapped, ErrConflict) {
				continue
			}
			return models.Collection{}, mapped
		}
		return p.collectionByID(ctx, id)
	}
	return models.Collection{}, ErrConflict
}

func (p *Postgres) UpdateCollection(ctx context.Context, ownerID, collectionID string, title, description, coverURL, visibility *string) (models.Collection, error) {
	row := p.pool.QueryRow(ctx, `
		update collections
		set title = coalesce(nullif($3, ''), title),
		    description = case when $4::text is null then description else $4 end,
		    cover_url = case when $5::text is null then cover_url when $5 = '' then null else $5 end,
		    visibility = coalesce(nullif($6, ''), visibility),
		    updated_at = now()
		where id = $1 and owner_id = $2
		returning id
	`, collectionID, ownerID, title, description, coverURL, visibility)
	var id string
	if err := row.Scan(&id); err != nil {
		return models.Collection{}, mapError(err)
	}
	return p.collectionByID(ctx, id)
}

func (p *Postgres) DeleteCollection(ctx context.Context, ownerID, collectionID string) error {
	tag, err := p.pool.Exec(ctx, `
		delete from collections
		where id = $1 and owner_id = $2
	`, collectionID, ownerID)
	if err != nil {
		return mapError(err)
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

func (p *Postgres) ListMyCollections(ctx context.Context, ownerID string, limit int) ([]models.Collection, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	rows, err := p.pool.Query(ctx, collectionSelectSQL+`
		where c.owner_id = $1
		order by c.updated_at desc
		limit $2
	`, ownerID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanCollections(rows)
}

func (p *Postgres) ListPublicCollections(ctx context.Context, limit int) ([]models.Collection, error) {
	if limit <= 0 || limit > 100 {
		limit = 30
	}
	rows, err := p.pool.Query(ctx, collectionSelectSQL+`
		where c.visibility = 'public'
		order by c.updated_at desc
		limit $1
	`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanCollections(rows)
}

func (p *Postgres) GetCollection(ctx context.Context, viewerID *string, idOrSlug string) (models.CollectionDetail, error) {
	collection, err := p.collectionByIDOrSlug(ctx, viewerID, idOrSlug)
	if err != nil {
		return models.CollectionDetail{}, err
	}
	rows, err := p.pool.Query(ctx, collectionItemSelectSQL+`
		where ci.collection_id = $1
		order by ci.position asc, ci.created_at asc
	`, collection.ID)
	if err != nil {
		return models.CollectionDetail{}, err
	}
	defer rows.Close()
	items, err := scanCollectionItems(rows)
	if err != nil {
		return models.CollectionDetail{}, err
	}
	return models.CollectionDetail{Collection: collection, Items: items}, nil
}

func (p *Postgres) AddCollectionItem(ctx context.Context, ownerID, collectionID, videoRefID, note string, position *int) (models.CollectionItem, error) {
	tx, err := p.pool.Begin(ctx)
	if err != nil {
		return models.CollectionItem{}, err
	}
	defer tx.Rollback(ctx)

	var exists bool
	if err := tx.QueryRow(ctx, `select exists(select 1 from collections where id = $1 and owner_id = $2)`, collectionID, ownerID).Scan(&exists); err != nil {
		return models.CollectionItem{}, mapError(err)
	}
	if !exists {
		return models.CollectionItem{}, ErrNotFound
	}

	nextPosition := 0
	if position != nil {
		nextPosition = *position
	} else if err := tx.QueryRow(ctx, `select coalesce(max(position), -1) + 1 from collection_items where collection_id = $1`, collectionID).Scan(&nextPosition); err != nil {
		return models.CollectionItem{}, mapError(err)
	}

	row := tx.QueryRow(ctx, `
		insert into collection_items (collection_id, video_ref_id, note, position)
		values ($1, $2, $3, $4)
		on conflict (collection_id, video_ref_id) do update
		set note = excluded.note,
		    position = excluded.position
		returning id, (xmax = 0) as inserted
	`, collectionID, videoRefID, note, nextPosition)
	var id string
	var inserted bool
	if err := row.Scan(&id, &inserted); err != nil {
		return models.CollectionItem{}, mapError(err)
	}
	if inserted {
		if _, err := tx.Exec(ctx, `update collections set item_count = item_count + 1, updated_at = now() where id = $1`, collectionID); err != nil {
			return models.CollectionItem{}, mapError(err)
		}
	} else if _, err := tx.Exec(ctx, `update collections set updated_at = now() where id = $1`, collectionID); err != nil {
		return models.CollectionItem{}, mapError(err)
	}
	if err := tx.Commit(ctx); err != nil {
		return models.CollectionItem{}, mapError(err)
	}
	return p.collectionItemByID(ctx, id)
}

func (p *Postgres) RemoveCollectionItem(ctx context.Context, ownerID, collectionID, itemID string) error {
	tx, err := p.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	tag, err := tx.Exec(ctx, `
		delete from collection_items ci
		using collections c
		where ci.id = $1 and ci.collection_id = $2 and c.id = ci.collection_id and c.owner_id = $3
	`, itemID, collectionID, ownerID)
	if err != nil {
		return mapError(err)
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	if _, err := tx.Exec(ctx, `
		update collections
		set item_count = greatest(item_count - 1, 0), updated_at = now()
		where id = $1 and owner_id = $2
	`, collectionID, ownerID); err != nil {
		return mapError(err)
	}
	return mapError(tx.Commit(ctx))
}

func (p *Postgres) CopyCollection(ctx context.Context, ownerID, idOrSlug string) (models.Collection, error) {
	source, err := p.collectionByIDOrSlug(ctx, &ownerID, idOrSlug)
	if err != nil {
		return models.Collection{}, err
	}

	for attempts := 0; attempts < 3; attempts++ {
		tx, err := p.pool.Begin(ctx)
		if err != nil {
			return models.Collection{}, err
		}

		var newID string
		row := tx.QueryRow(ctx, `
			insert into collections (owner_id, title, description, cover_url, visibility, slug)
			values ($1, $2, $3, $4, 'private', $5)
			returning id
		`, ownerID, source.Title+" 的副本", source.Description, source.CoverURL, newCollectionSlug())
		if err := row.Scan(&newID); err != nil {
			_ = tx.Rollback(ctx)
			mapped := mapError(err)
			if errors.Is(mapped, ErrConflict) {
				continue
			}
			return models.Collection{}, mapped
		}
		tag, err := tx.Exec(ctx, `
			insert into collection_items (collection_id, video_ref_id, note, position)
			select $1, video_ref_id, note, position
			from collection_items
			where collection_id = $2
			order by position asc, created_at asc
		`, newID, source.ID)
		if err != nil {
			_ = tx.Rollback(ctx)
			return models.Collection{}, mapError(err)
		}
		if _, err := tx.Exec(ctx, `update collections set item_count = $2 where id = $1`, newID, tag.RowsAffected()); err != nil {
			_ = tx.Rollback(ctx)
			return models.Collection{}, mapError(err)
		}
		if err := tx.Commit(ctx); err != nil {
			return models.Collection{}, mapError(err)
		}
		return p.collectionByID(ctx, newID)
	}
	return models.Collection{}, ErrConflict
}

type scanner interface {
	Scan(dest ...any) error
}

func scanUser(row scanner) (models.User, error) {
	var user models.User
	err := row.Scan(&user.ID, &user.Username, &user.Email, &user.AvatarURL, &user.Bio, &user.Role, &user.CreatedAt, &user.UpdatedAt)
	return user, err
}

func scanRefreshToken(row scanner) (models.RefreshToken, error) {
	var token models.RefreshToken
	err := row.Scan(&token.ID, &token.UserID, &token.TokenHash, &token.ExpiresAt, &token.RevokedAt, &token.CreatedAt)
	return token, err
}

func scanVideoRef(row scanner) (models.VideoRef, error) {
	var video models.VideoRef
	err := row.Scan(&video.ID, &video.Source, &video.SourceURL, &video.Title, &video.CoverURL, &video.Maker, &video.CreatedAt, &video.UpdatedAt)
	return video, err
}

const favoriteSelectSQL = `
	select
		f.id, f.user_id, f.title_snapshot, f.cover_snapshot, f.maker_snapshot,
		f.tags_snapshot, f.created_at, f.updated_at, f.deleted_at,
		v.id, v.source, v.source_url, v.title, v.cover_url, v.maker, v.created_at, v.updated_at
	from favorites f
	join video_refs v on v.id = f.video_ref_id
`

func (p *Postgres) favoriteByID(ctx context.Context, id string) (models.Favorite, error) {
	row := p.pool.QueryRow(ctx, favoriteSelectSQL+` where f.id = $1`, id)
	return scanFavorite(row)
}

func (p *Postgres) favoriteByUserVideo(ctx context.Context, userID, videoRefID string) (models.Favorite, error) {
	row := p.pool.QueryRow(ctx, favoriteSelectSQL+` where f.user_id = $1 and f.video_ref_id = $2`, userID, videoRefID)
	return scanFavorite(row)
}

func scanFavorite(row scanner) (models.Favorite, error) {
	var fav models.Favorite
	var video models.VideoRef
	var rawTags []byte
	err := row.Scan(
		&fav.ID,
		&fav.UserID,
		&fav.TitleSnapshot,
		&fav.CoverSnapshot,
		&fav.MakerSnapshot,
		&rawTags,
		&fav.CreatedAt,
		&fav.UpdatedAt,
		&fav.DeletedAt,
		&video.ID,
		&video.Source,
		&video.SourceURL,
		&video.Title,
		&video.CoverURL,
		&video.Maker,
		&video.CreatedAt,
		&video.UpdatedAt,
	)
	if err != nil {
		return models.Favorite{}, mapError(err)
	}
	_ = json.Unmarshal(rawTags, &fav.TagsSnapshot)
	fav.VideoRef = video
	return fav, nil
}

func scanFavorites(rows pgx.Rows) ([]models.Favorite, error) {
	items := make([]models.Favorite, 0)
	for rows.Next() {
		item, err := scanFavorite(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

const commentSelectSQL = `
	select c.id, c.user_id, u.username, c.video_ref_id, c.parent_id, c.body,
	       c.status, c.like_count, c.created_at, c.updated_at, c.deleted_at
	from comments c
	join users u on u.id = c.user_id
`

func (p *Postgres) commentByID(ctx context.Context, id string) (models.Comment, error) {
	row := p.pool.QueryRow(ctx, commentSelectSQL+` where c.id = $1`, id)
	return scanComment(row)
}

func scanComment(row scanner) (models.Comment, error) {
	var comment models.Comment
	err := row.Scan(
		&comment.ID,
		&comment.UserID,
		&comment.Username,
		&comment.VideoRefID,
		&comment.ParentID,
		&comment.Body,
		&comment.Status,
		&comment.LikeCount,
		&comment.CreatedAt,
		&comment.UpdatedAt,
		&comment.DeletedAt,
	)
	if err != nil {
		return models.Comment{}, mapError(err)
	}
	return comment, nil
}

func scanComments(rows pgx.Rows) ([]models.Comment, error) {
	items := make([]models.Comment, 0)
	for rows.Next() {
		item, err := scanComment(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

const collectionSelectSQL = `
	select c.id, c.owner_id, u.username, c.title, c.description, c.cover_url,
	       c.visibility, c.slug, c.item_count, c.like_count, c.save_count,
	       c.created_at, c.updated_at
	from collections c
	join users u on u.id = c.owner_id
`

func (p *Postgres) collectionByID(ctx context.Context, id string) (models.Collection, error) {
	row := p.pool.QueryRow(ctx, collectionSelectSQL+` where c.id = $1`, id)
	return scanCollection(row)
}

func (p *Postgres) collectionByIDOrSlug(ctx context.Context, viewerID *string, idOrSlug string) (models.Collection, error) {
	row := p.pool.QueryRow(ctx, collectionSelectSQL+`
		where (c.id::text = $1 or c.slug = $1)
		  and (c.visibility <> 'private' or ($2::uuid is not null and c.owner_id = $2))
	`, idOrSlug, viewerID)
	return scanCollection(row)
}

func scanCollection(row scanner) (models.Collection, error) {
	var collection models.Collection
	err := row.Scan(
		&collection.ID,
		&collection.OwnerID,
		&collection.OwnerUsername,
		&collection.Title,
		&collection.Description,
		&collection.CoverURL,
		&collection.Visibility,
		&collection.Slug,
		&collection.ItemCount,
		&collection.LikeCount,
		&collection.SaveCount,
		&collection.CreatedAt,
		&collection.UpdatedAt,
	)
	if err != nil {
		return models.Collection{}, mapError(err)
	}
	return collection, nil
}

func scanCollections(rows pgx.Rows) ([]models.Collection, error) {
	items := make([]models.Collection, 0)
	for rows.Next() {
		item, err := scanCollection(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

const collectionItemSelectSQL = `
	select
		ci.id, ci.collection_id, ci.note, ci.position, ci.created_at,
		v.id, v.source, v.source_url, v.title, v.cover_url, v.maker, v.created_at, v.updated_at
	from collection_items ci
	join video_refs v on v.id = ci.video_ref_id
`

func (p *Postgres) collectionItemByID(ctx context.Context, id string) (models.CollectionItem, error) {
	row := p.pool.QueryRow(ctx, collectionItemSelectSQL+` where ci.id = $1`, id)
	return scanCollectionItem(row)
}

func scanCollectionItem(row scanner) (models.CollectionItem, error) {
	var item models.CollectionItem
	var video models.VideoRef
	err := row.Scan(
		&item.ID,
		&item.CollectionID,
		&item.Note,
		&item.Position,
		&item.CreatedAt,
		&video.ID,
		&video.Source,
		&video.SourceURL,
		&video.Title,
		&video.CoverURL,
		&video.Maker,
		&video.CreatedAt,
		&video.UpdatedAt,
	)
	if err != nil {
		return models.CollectionItem{}, mapError(err)
	}
	item.VideoRef = video
	return item, nil
}

func scanCollectionItems(rows pgx.Rows) ([]models.CollectionItem, error) {
	items := make([]models.CollectionItem, 0)
	for rows.Next() {
		item, err := scanCollectionItem(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

func newCollectionSlug() string {
	raw := strings.ReplaceAll(uuid.NewString(), "-", "")
	return raw[:12]
}

func mapError(err error) error {
	if err == nil {
		return nil
	}
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrNotFound
	}
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) {
		switch pgErr.Code {
		case "23505":
			return ErrConflict
		case "23503":
			return ErrNotFound
		}
	}
	return err
}
