package api

import (
	"net/http"
	"strings"

	"nicetv/backend/internal/models"
)

type upsertVideoRefRequest struct {
	Source    string  `json:"source"`
	SourceURL string  `json:"sourceUrl"`
	Title     string  `json:"title"`
	CoverURL  *string `json:"coverUrl"`
	Maker     *string `json:"maker"`
}

func (s *Server) handleUpsertVideoRef(w http.ResponseWriter, r *http.Request) {
	var req upsertVideoRefRequest
	if !readJSON(w, r, &req) {
		return
	}
	sourceURL, err := normalizeURL(req.SourceURL)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	title := strings.TrimSpace(req.Title)
	if len([]rune(title)) > 500 {
		writeError(w, http.StatusBadRequest, "bad_request", "title is too long")
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
	video, err := s.store.UpsertVideoRef(r.Context(), normalizeSource(req.Source), sourceURL, title, coverURL, maker)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"videoRef": video})
}

func sanitizeSnapshot(snapshot models.FavoriteSnapshot) (models.FavoriteSnapshot, error) {
	snapshot.Title = strings.TrimSpace(snapshot.Title)
	if len([]rune(snapshot.Title)) > 500 {
		return models.FavoriteSnapshot{}, errBadSnapshot("title is too long")
	}
	if snapshot.CoverURL != nil {
		value := strings.TrimSpace(*snapshot.CoverURL)
		if len(value) > 2048 {
			return models.FavoriteSnapshot{}, errBadSnapshot("coverUrl is too long")
		}
		snapshot.CoverURL = &value
	}
	if snapshot.Maker != nil {
		value := strings.TrimSpace(*snapshot.Maker)
		if len([]rune(value)) > 120 {
			return models.FavoriteSnapshot{}, errBadSnapshot("maker is too long")
		}
		snapshot.Maker = &value
	}
	tags := make([]string, 0, len(snapshot.Tags))
	seen := make(map[string]struct{}, len(snapshot.Tags))
	for _, tag := range snapshot.Tags {
		tag = strings.TrimSpace(tag)
		if tag == "" {
			continue
		}
		if len([]rune(tag)) > 50 {
			return models.FavoriteSnapshot{}, errBadSnapshot("tag is too long")
		}
		if _, ok := seen[tag]; ok {
			continue
		}
		seen[tag] = struct{}{}
		tags = append(tags, tag)
		if len(tags) >= 30 {
			break
		}
	}
	snapshot.Tags = tags
	return snapshot, nil
}

type badSnapshotError string

func (e badSnapshotError) Error() string {
	return string(e)
}

func errBadSnapshot(message string) error {
	return badSnapshotError(message)
}
