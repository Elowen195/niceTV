package store

import (
	"context"
	"errors"
	"time"

	"nicetv/backend/internal/models"
)

var (
	ErrNotFound     = errors.New("not found")
	ErrConflict     = errors.New("conflict")
	ErrUnauthorized = errors.New("unauthorized")
	ErrForbidden    = errors.New("forbidden")
)

type Store interface {
	CreateUser(ctx context.Context, username string, email *string, passwordHash string) (models.User, error)
	GetUserByLogin(ctx context.Context, login string) (models.UserWithPassword, error)
	GetUserByID(ctx context.Context, id string) (models.User, error)
	UpdateUser(ctx context.Context, id string, username, email, avatarURL, bio *string) (models.User, error)
	ListUsers(ctx context.Context, limit int) ([]models.User, error)
	SetUserRole(ctx context.Context, actorID, targetID, role, reason string) (models.User, error)
	SetUserStatus(ctx context.Context, actorID, targetID, status, reason string) (models.User, error)
	SetUserMutedUntil(ctx context.Context, actorID, targetID string, mutedUntil *time.Time, reason string) (models.User, error)

	CreateRefreshToken(ctx context.Context, userID, tokenHash, deviceName string, expiresAt time.Time) (models.RefreshToken, error)
	FindRefreshToken(ctx context.Context, tokenHash string) (models.RefreshToken, error)
	RevokeRefreshToken(ctx context.Context, tokenHash string) error

	UpsertVideoRef(ctx context.Context, source, sourceURL, title string, coverURL, maker *string) (models.VideoRef, error)

	ListFavorites(ctx context.Context, userID string, limit int) ([]models.Favorite, error)
	UpsertFavorite(ctx context.Context, userID, videoRefID string, snapshot models.FavoriteSnapshot, updatedAt time.Time) (models.Favorite, error)
	DeleteFavorite(ctx context.Context, userID, videoRefID string, updatedAt time.Time) (models.Favorite, error)
	SyncFavorites(ctx context.Context, userID string, since *time.Time, changes []models.FavoriteSyncChange) (models.FavoriteSyncResult, error)

	ListComments(ctx context.Context, videoRefID string, limit int, cursor *time.Time) ([]models.Comment, error)
	CreateComment(ctx context.Context, userID, videoRefID string, parentID *string, body string) (models.Comment, error)
	UpdateComment(ctx context.Context, userID, commentID, body string) (models.Comment, error)
	DeleteComment(ctx context.Context, userID, commentID string) error
	LikeComment(ctx context.Context, userID, commentID string) (models.Comment, error)
	UnlikeComment(ctx context.Context, userID, commentID string) (models.Comment, error)
	ModerateComment(ctx context.Context, actorID, commentID, status, reason string) (models.Comment, error)

	CreateCollection(ctx context.Context, ownerID, title, description string, coverURL *string, visibility string) (models.Collection, error)
	UpdateCollection(ctx context.Context, ownerID, collectionID string, title, description, coverURL, visibility *string) (models.Collection, error)
	DeleteCollection(ctx context.Context, ownerID, collectionID string) error
	ListMyCollections(ctx context.Context, ownerID string, limit int) ([]models.Collection, error)
	ListPublicCollections(ctx context.Context, limit int) ([]models.Collection, error)
	GetCollection(ctx context.Context, viewerID *string, idOrSlug string) (models.CollectionDetail, error)
	AddCollectionItem(ctx context.Context, ownerID, collectionID, videoRefID, note string, position *int) (models.CollectionItem, error)
	RemoveCollectionItem(ctx context.Context, ownerID, collectionID, itemID string) error
	CopyCollection(ctx context.Context, ownerID, idOrSlug string) (models.Collection, error)
	ModerateCollection(ctx context.Context, actorID, collectionID, status, reason string) (models.Collection, error)

	ListModerationActions(ctx context.Context, limit int) ([]models.ModerationAction, error)
}
