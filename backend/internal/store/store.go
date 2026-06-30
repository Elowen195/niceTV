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
)

type Store interface {
	CreateUser(ctx context.Context, username string, email *string, passwordHash string) (models.User, error)
	GetUserByLogin(ctx context.Context, login string) (models.UserWithPassword, error)
	GetUserByID(ctx context.Context, id string) (models.User, error)
	UpdateUser(ctx context.Context, id string, username, email, avatarURL, bio *string) (models.User, error)

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
}
