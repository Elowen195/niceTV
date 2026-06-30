package api

import (
	"net/http"
	"strings"

	"github.com/go-chi/chi/v5"
)

type collectionRequest struct {
	Title       string  `json:"title"`
	Description string  `json:"description"`
	CoverURL    *string `json:"coverUrl"`
	Visibility  string  `json:"visibility"`
}

type addCollectionItemRequest struct {
	VideoRefID string  `json:"videoRefId"`
	Source     string  `json:"source"`
	SourceURL  string  `json:"sourceUrl"`
	Title      string  `json:"title"`
	CoverURL   *string `json:"coverUrl"`
	Maker      *string `json:"maker"`
	Note       string  `json:"note"`
	Position   *int    `json:"position"`
}

func (s *Server) handleCreateCollection(w http.ResponseWriter, r *http.Request) {
	var req collectionRequest
	if !readJSON(w, r, &req) {
		return
	}
	title, description, coverURL, visibility, ok := sanitizeCollectionRequest(w, req)
	if !ok {
		return
	}
	collection, err := s.store.CreateCollection(r.Context(), currentUserID(r), title, description, coverURL, visibility)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{"collection": collection})
}

func (s *Server) handleUpdateCollection(w http.ResponseWriter, r *http.Request) {
	collectionID, err := normalizeID(chi.URLParam(r, "collectionID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	var req collectionRequest
	if !readJSON(w, r, &req) {
		return
	}
	title, description, coverURL, visibility, ok := sanitizeCollectionRequest(w, req)
	if !ok {
		return
	}
	collection, err := s.store.UpdateCollection(r.Context(), currentUserID(r), collectionID, &title, &description, coverURL, &visibility)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"collection": collection})
}

func (s *Server) handleDeleteCollection(w http.ResponseWriter, r *http.Request) {
	collectionID, err := normalizeID(chi.URLParam(r, "collectionID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	if err := s.store.DeleteCollection(r.Context(), currentUserID(r), collectionID); err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusNoContent, nil)
}

func (s *Server) handleListMyCollections(w http.ResponseWriter, r *http.Request) {
	collections, err := s.store.ListMyCollections(r.Context(), currentUserID(r), parseLimit(r, 50, 100))
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"collections": collections})
}

func (s *Server) handleListPublicCollections(w http.ResponseWriter, r *http.Request) {
	collections, err := s.store.ListPublicCollections(r.Context(), parseLimit(r, 30, 100))
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"collections": collections})
}

func (s *Server) handleGetCollection(w http.ResponseWriter, r *http.Request) {
	detail, err := s.store.GetCollection(r.Context(), nil, chi.URLParam(r, "idOrSlug"))
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, detail)
}

func (s *Server) handleGetMyCollection(w http.ResponseWriter, r *http.Request) {
	viewerID := currentUserID(r)
	detail, err := s.store.GetCollection(r.Context(), &viewerID, chi.URLParam(r, "idOrSlug"))
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, detail)
}

func (s *Server) handleAddCollectionItem(w http.ResponseWriter, r *http.Request) {
	collectionID, err := normalizeID(chi.URLParam(r, "collectionID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	var req addCollectionItemRequest
	if !readJSON(w, r, &req) {
		return
	}
	videoRefID := strings.TrimSpace(req.VideoRefID)
	if videoRefID == "" {
		sourceURL, err := normalizeURL(req.SourceURL)
		if err != nil {
			writeError(w, http.StatusBadRequest, "bad_request", err.Error())
			return
		}
		coverURL, err := normalizeOptionalText(req.CoverURL, 2048)
		if err != nil {
			writeError(w, http.StatusBadRequest, "bad_request", "coverUrl is too long")
			return
		}
		maker, err := normalizeOptionalText(req.Maker, 120)
		if err != nil {
			writeError(w, http.StatusBadRequest, "bad_request", "maker is too long")
			return
		}
		video, err := s.store.UpsertVideoRef(r.Context(), normalizeSource(req.Source), sourceURL, strings.TrimSpace(req.Title), coverURL, maker)
		if err != nil {
			handleStoreError(w, err)
			return
		}
		videoRefID = video.ID
	} else if _, err := normalizeID(videoRefID); err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "invalid videoRefId")
		return
	}
	note, err := normalizeOptionalText(&req.Note, 500)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "note is too long")
		return
	}
	noteValue := ""
	if note != nil {
		noteValue = *note
	}
	item, err := s.store.AddCollectionItem(r.Context(), currentUserID(r), collectionID, videoRefID, noteValue, req.Position)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"item": item})
}

func (s *Server) handleRemoveCollectionItem(w http.ResponseWriter, r *http.Request) {
	collectionID, err := normalizeID(chi.URLParam(r, "collectionID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	itemID, err := normalizeID(chi.URLParam(r, "itemID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	if err := s.store.RemoveCollectionItem(r.Context(), currentUserID(r), collectionID, itemID); err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusNoContent, nil)
}

func (s *Server) handleCopyCollection(w http.ResponseWriter, r *http.Request) {
	collection, err := s.store.CopyCollection(r.Context(), currentUserID(r), chi.URLParam(r, "idOrSlug"))
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{"collection": collection})
}

func sanitizeCollectionRequest(w http.ResponseWriter, req collectionRequest) (string, string, *string, string, bool) {
	title := strings.TrimSpace(req.Title)
	if title == "" || len([]rune(title)) > 120 {
		writeError(w, http.StatusBadRequest, "bad_request", "title must be 1-120 characters")
		return "", "", nil, "", false
	}
	description := strings.TrimSpace(req.Description)
	if len([]rune(description)) > 500 {
		writeError(w, http.StatusBadRequest, "bad_request", "description is too long")
		return "", "", nil, "", false
	}
	coverURL, err := normalizeOptionalText(req.CoverURL, 2048)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "coverUrl is too long")
		return "", "", nil, "", false
	}
	visibility := normalizeCollectionVisibility(req.Visibility)
	if visibility == "" {
		writeError(w, http.StatusBadRequest, "bad_request", "invalid visibility")
		return "", "", nil, "", false
	}
	return title, description, coverURL, visibility, true
}

func normalizeCollectionVisibility(value string) string {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "", "private":
		return "private"
	case "unlisted":
		return "unlisted"
	case "public":
		return "public"
	default:
		return ""
	}
}
