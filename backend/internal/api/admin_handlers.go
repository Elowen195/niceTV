package api

import (
	"net/http"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
)

type adminUserUpdateRequest struct {
	Role       *string `json:"role"`
	Status     *string `json:"status"`
	MutedUntil *string `json:"mutedUntil"`
	Reason     string  `json:"reason"`
}

type adminStatusRequest struct {
	Status string `json:"status"`
	Reason string `json:"reason"`
}

func (s *Server) handleAdminListUsers(w http.ResponseWriter, r *http.Request) {
	users, err := s.store.ListUsers(r.Context(), parseLimit(r, 50, 200))
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"users": users})
}

func (s *Server) handleAdminUpdateUser(w http.ResponseWriter, r *http.Request) {
	targetID, err := normalizeID(chi.URLParam(r, "userID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	var req adminUserUpdateRequest
	if !readJSON(w, r, &req) {
		return
	}
	actor := currentUser(r)
	reason := normalizeModerationReason(req.Reason)

	target, err := s.store.GetUserByID(r.Context(), targetID)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	if actor.Role == "moderator" && target.Role == "admin" {
		writeError(w, http.StatusForbidden, "forbidden", "moderator cannot modify admin accounts")
		return
	}

	updated := target
	if req.Role != nil {
		if actor.Role != "admin" {
			writeError(w, http.StatusForbidden, "forbidden", "only admin can change roles")
			return
		}
		role := normalizeUserRole(*req.Role)
		if role == "" {
			writeError(w, http.StatusBadRequest, "bad_request", "invalid role")
			return
		}
		updated, err = s.store.SetUserRole(r.Context(), actor.ID, targetID, role, reason)
		if err != nil {
			handleStoreError(w, err)
			return
		}
	}
	if req.Status != nil {
		if actor.Role != "admin" {
			writeError(w, http.StatusForbidden, "forbidden", "only admin can change account status")
			return
		}
		status := normalizeUserStatus(*req.Status)
		if status == "" {
			writeError(w, http.StatusBadRequest, "bad_request", "invalid status")
			return
		}
		if actor.ID == targetID && status == "banned" {
			writeError(w, http.StatusBadRequest, "bad_request", "admin cannot ban self")
			return
		}
		updated, err = s.store.SetUserStatus(r.Context(), actor.ID, targetID, status, reason)
		if err != nil {
			handleStoreError(w, err)
			return
		}
	}
	if req.MutedUntil != nil {
		mutedUntil, ok := parseAdminMutedUntil(w, *req.MutedUntil)
		if !ok {
			return
		}
		updated, err = s.store.SetUserMutedUntil(r.Context(), actor.ID, targetID, mutedUntil, reason)
		if err != nil {
			handleStoreError(w, err)
			return
		}
	}

	writeJSON(w, http.StatusOK, map[string]any{"user": updated})
}

func (s *Server) handleAdminListComments(w http.ResponseWriter, r *http.Request) {
	status := normalizeOptionalCommentStatus(r.URL.Query().Get("status"))
	if status == "__invalid__" {
		writeError(w, http.StatusBadRequest, "bad_request", "invalid comment status")
		return
	}
	comments, err := s.store.ListAdminComments(
		r.Context(),
		status,
		parseLimit(r, 50, 200),
		parseOffset(r),
	)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"comments": comments})
}

func (s *Server) handleAdminModerateComment(w http.ResponseWriter, r *http.Request) {
	commentID, err := normalizeID(chi.URLParam(r, "commentID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	var req adminStatusRequest
	if !readJSON(w, r, &req) {
		return
	}
	status := normalizeCommentStatus(req.Status)
	if status == "" {
		writeError(w, http.StatusBadRequest, "bad_request", "invalid comment status")
		return
	}
	comment, err := s.store.ModerateComment(r.Context(), currentUserID(r), commentID, status, normalizeModerationReason(req.Reason))
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"comment": comment})
}

func (s *Server) handleAdminListCollections(w http.ResponseWriter, r *http.Request) {
	visibility := normalizeOptionalCollectionVisibility(r.URL.Query().Get("visibility"))
	if visibility == "__invalid__" {
		writeError(w, http.StatusBadRequest, "bad_request", "invalid visibility")
		return
	}
	status := normalizeOptionalCollectionStatus(r.URL.Query().Get("status"))
	if status == "__invalid__" {
		writeError(w, http.StatusBadRequest, "bad_request", "invalid collection status")
		return
	}
	collections, err := s.store.ListAdminCollections(
		r.Context(),
		visibility,
		status,
		parseLimit(r, 50, 200),
		parseOffset(r),
	)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"collections": collections})
}

func (s *Server) handleAdminModerateCollection(w http.ResponseWriter, r *http.Request) {
	collectionID, err := normalizeID(chi.URLParam(r, "collectionID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	var req adminStatusRequest
	if !readJSON(w, r, &req) {
		return
	}
	status := normalizeCollectionStatus(req.Status)
	if status == "" {
		writeError(w, http.StatusBadRequest, "bad_request", "invalid collection status")
		return
	}
	collection, err := s.store.ModerateCollection(r.Context(), currentUserID(r), collectionID, status, normalizeModerationReason(req.Reason))
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"collection": collection})
}

func (s *Server) handleAdminStats(w http.ResponseWriter, r *http.Request) {
	stats, err := s.store.AdminStats(r.Context())
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, stats)
}

func (s *Server) handleAdminListModerationActions(w http.ResponseWriter, r *http.Request) {
	actions, err := s.store.ListModerationActions(r.Context(), parseLimit(r, 50, 200))
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"actions": actions})
}

func parseAdminMutedUntil(w http.ResponseWriter, raw string) (*time.Time, bool) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return nil, true
	}
	parsed, err := time.Parse(time.RFC3339, raw)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "invalid mutedUntil")
		return nil, false
	}
	utc := parsed.UTC()
	return &utc, true
}

func normalizeUserRole(value string) string {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "user", "moderator", "admin":
		return strings.ToLower(strings.TrimSpace(value))
	default:
		return ""
	}
}

func normalizeUserStatus(value string) string {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "active", "banned":
		return strings.ToLower(strings.TrimSpace(value))
	default:
		return ""
	}
}

func normalizeCommentStatus(value string) string {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "visible", "hidden", "deleted", "pending":
		return strings.ToLower(strings.TrimSpace(value))
	default:
		return ""
	}
}

func normalizeOptionalCommentStatus(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}
	normalized := normalizeCommentStatus(value)
	if normalized == "" {
		return "__invalid__"
	}
	return normalized
}

func normalizeCollectionStatus(value string) string {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "visible", "hidden", "deleted":
		return strings.ToLower(strings.TrimSpace(value))
	default:
		return ""
	}
}

func normalizeOptionalCollectionStatus(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}
	normalized := normalizeCollectionStatus(value)
	if normalized == "" {
		return "__invalid__"
	}
	return normalized
}

func normalizeOptionalCollectionVisibility(value string) string {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "":
		return ""
	case "public", "private", "unlisted":
		return strings.ToLower(strings.TrimSpace(value))
	default:
		return "__invalid__"
	}
}

func normalizeModerationReason(value string) string {
	value = strings.TrimSpace(value)
	if len([]rune(value)) > 300 {
		return string([]rune(value)[:300])
	}
	return value
}
