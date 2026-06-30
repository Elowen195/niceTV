package api

import (
	"net/http"
	"time"

	"nicetv/backend/internal/models"

	"github.com/go-chi/chi/v5"
)

type favoriteUpsertRequest struct {
	Snapshot  *models.FavoriteSnapshot `json:"snapshot"`
	Title     string                   `json:"title"`
	CoverURL  *string                  `json:"coverUrl"`
	Maker     *string                  `json:"maker"`
	Tags      []string                 `json:"tags"`
	UpdatedAt *time.Time               `json:"updatedAt"`
}

type favoriteDeleteRequest struct {
	UpdatedAt *time.Time `json:"updatedAt"`
}

type favoriteSyncRequest struct {
	Since   *time.Time                  `json:"since"`
	Changes []models.FavoriteSyncChange `json:"changes"`
}

func (s *Server) handleListFavorites(w http.ResponseWriter, r *http.Request) {
	limit := parseLimit(r, 30, 100)
	favorites, err := s.store.ListFavorites(r.Context(), currentUserID(r), limit)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"favorites": favorites})
}

func (s *Server) handleUpsertFavorite(w http.ResponseWriter, r *http.Request) {
	videoRefID, err := normalizeID(chi.URLParam(r, "videoRefID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	var req favoriteUpsertRequest
	if !readJSON(w, r, &req) {
		return
	}
	snapshot, err := favoriteSnapshotFromRequest(req)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	updatedAt := time.Time{}
	if req.UpdatedAt != nil {
		updatedAt = req.UpdatedAt.UTC()
	}
	favorite, err := s.store.UpsertFavorite(r.Context(), currentUserID(r), videoRefID, snapshot, updatedAt)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"favorite": favorite})
}

func (s *Server) handleDeleteFavorite(w http.ResponseWriter, r *http.Request) {
	videoRefID, err := normalizeID(chi.URLParam(r, "videoRefID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	var req favoriteDeleteRequest
	if !readOptionalJSON(w, r, &req) {
		return
	}
	updatedAt := time.Time{}
	if req.UpdatedAt != nil {
		updatedAt = req.UpdatedAt.UTC()
	}
	favorite, err := s.store.DeleteFavorite(r.Context(), currentUserID(r), videoRefID, updatedAt)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"favorite": favorite})
}

func (s *Server) handleSyncFavorites(w http.ResponseWriter, r *http.Request) {
	var req favoriteSyncRequest
	if !readJSON(w, r, &req) {
		return
	}
	if len(req.Changes) > 200 {
		writeError(w, http.StatusBadRequest, "bad_request", "too many changes")
		return
	}
	for i := range req.Changes {
		sourceURL, err := normalizeURL(req.Changes[i].SourceURL)
		if err != nil {
			writeError(w, http.StatusBadRequest, "bad_request", "invalid change sourceUrl")
			return
		}
		req.Changes[i].SourceURL = sourceURL
		req.Changes[i].Source = normalizeSource(req.Changes[i].Source)
		req.Changes[i].Snapshot, err = sanitizeSnapshot(req.Changes[i].Snapshot)
		if err != nil {
			writeError(w, http.StatusBadRequest, "bad_request", err.Error())
			return
		}
		switch req.Changes[i].Op {
		case "", "upsert", "add", "delete", "remove":
		default:
			writeError(w, http.StatusBadRequest, "bad_request", "invalid favorite op")
			return
		}
	}
	result, err := s.store.SyncFavorites(r.Context(), currentUserID(r), req.Since, req.Changes)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, result)
}

func favoriteSnapshotFromRequest(req favoriteUpsertRequest) (models.FavoriteSnapshot, error) {
	if req.Snapshot != nil {
		return sanitizeSnapshot(*req.Snapshot)
	}
	return sanitizeSnapshot(models.FavoriteSnapshot{
		Title:    req.Title,
		CoverURL: req.CoverURL,
		Maker:    req.Maker,
		Tags:     req.Tags,
	})
}
