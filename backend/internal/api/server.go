package api

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"nicetv/backend/internal/auth"
	"nicetv/backend/internal/config"
	"nicetv/backend/internal/store"

	"github.com/go-chi/chi/v5"
)

type Server struct {
	cfg     config.Config
	store   store.Store
	limiter *rateLimiter
}

func NewServer(cfg config.Config, store store.Store) *Server {
	return &Server{cfg: cfg, store: store, limiter: newRateLimiter()}
}

func (s *Server) Routes() http.Handler {
	r := chi.NewRouter()
	r.Use(s.cors)
	r.Use(s.rateLimit)
	r.Use(recoverer)

	r.Get("/healthz", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]any{
			"ok":   true,
			"time": time.Now().UTC(),
		})
	})

	r.Route("/v1", func(r chi.Router) {
		r.Post("/auth/register", s.handleRegister)
		r.Post("/auth/login", s.handleLogin)
		r.Post("/auth/refresh", s.handleRefresh)
		r.Post("/auth/logout", s.handleLogout)

		r.Post("/video-refs", s.handleUpsertVideoRef)
		r.Get("/video-refs/{videoRefID}/comments", s.handleListComments)
		r.Get("/collections/public", s.handleListPublicCollections)
		r.Get("/collections/{idOrSlug}", s.handleGetCollection)

		r.Group(func(r chi.Router) {
			r.Use(s.authRequired)

			r.Get("/me", s.handleMe)
			r.Patch("/me", s.handleUpdateMe)

			r.Get("/favorites", s.handleListFavorites)
			r.Put("/favorites/{videoRefID}", s.handleUpsertFavorite)
			r.Delete("/favorites/{videoRefID}", s.handleDeleteFavorite)
			r.Post("/favorites/sync", s.handleSyncFavorites)

			r.Post("/video-refs/{videoRefID}/comments", s.handleCreateComment)
			r.Patch("/comments/{commentID}", s.handleUpdateComment)
			r.Delete("/comments/{commentID}", s.handleDeleteComment)
			r.Put("/comments/{commentID}/like", s.handleLikeComment)
			r.Delete("/comments/{commentID}/like", s.handleUnlikeComment)

			r.Get("/collections/mine", s.handleListMyCollections)
			r.Get("/collections/mine/{idOrSlug}", s.handleGetMyCollection)
			r.Post("/collections", s.handleCreateCollection)
			r.Patch("/collections/{collectionID}", s.handleUpdateCollection)
			r.Delete("/collections/{collectionID}", s.handleDeleteCollection)
			r.Post("/collections/{collectionID}/items", s.handleAddCollectionItem)
			r.Delete("/collections/{collectionID}/items/{itemID}", s.handleRemoveCollectionItem)
			r.Post("/collections/{idOrSlug}/copy", s.handleCopyCollection)
		})
	})

	return r
}

type contextKey string

const userIDContextKey contextKey = "userID"

func (s *Server) authRequired(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authz := strings.TrimSpace(r.Header.Get("Authorization"))
		parts := strings.Fields(authz)
		if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
			writeError(w, http.StatusUnauthorized, "unauthorized", "missing bearer token")
			return
		}
		claims, err := auth.ParseAccessToken(parts[1], s.cfg.JWTSecret)
		if err != nil {
			writeError(w, http.StatusUnauthorized, "unauthorized", "invalid or expired token")
			return
		}
		ctx := context.WithValue(r.Context(), userIDContextKey, claims.UserID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func currentUserID(r *http.Request) string {
	userID, _ := r.Context().Value(userIDContextKey).(string)
	return userID
}

func (s *Server) cors(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := s.cfg.CORSOrigin
		if origin == "" {
			origin = "*"
		}
		w.Header().Set("Access-Control-Allow-Origin", origin)
		w.Header().Set("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Authorization,Content-Type")
		w.Header().Set("Access-Control-Max-Age", "86400")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func recoverer(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if recovered := recover(); recovered != nil {
				slog.Error("panic recovered", "error", recovered)
				writeError(w, http.StatusInternalServerError, "internal_error", "internal server error")
			}
		}()
		next.ServeHTTP(w, r)
	})
}

func readJSON(w http.ResponseWriter, r *http.Request, dst any) bool {
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
	defer r.Body.Close()
	if err := json.NewDecoder(r.Body).Decode(dst); err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "invalid json body")
		return false
	}
	return true
}

func readOptionalJSON(w http.ResponseWriter, r *http.Request, dst any) bool {
	if r.Body == nil || r.ContentLength == 0 {
		return true
	}
	return readJSON(w, r, dst)
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if payload == nil || status == http.StatusNoContent {
		return
	}
	if err := json.NewEncoder(w).Encode(payload); err != nil {
		slog.Error("write json response", "error", err)
	}
}

func writeError(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, map[string]any{
		"error": map[string]string{
			"code":    code,
			"message": message,
		},
	})
}

func handleStoreError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, store.ErrNotFound):
		writeError(w, http.StatusNotFound, "not_found", "resource not found")
	case errors.Is(err, store.ErrConflict):
		writeError(w, http.StatusConflict, "conflict", "resource already exists")
	case errors.Is(err, store.ErrUnauthorized):
		writeError(w, http.StatusUnauthorized, "unauthorized", "unauthorized")
	default:
		slog.Error("store error", "error", err)
		writeError(w, http.StatusInternalServerError, "internal_error", "internal server error")
	}
}

func parseLimit(r *http.Request, fallback, max int) int {
	raw := r.URL.Query().Get("limit")
	if raw == "" {
		return fallback
	}
	limit, err := strconv.Atoi(raw)
	if err != nil || limit <= 0 {
		return fallback
	}
	if limit > max {
		return max
	}
	return limit
}

func parseOptionalTime(raw string) (*time.Time, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return nil, nil
	}
	t, err := time.Parse(time.RFC3339, raw)
	if err != nil {
		return nil, err
	}
	utc := t.UTC()
	return &utc, nil
}
