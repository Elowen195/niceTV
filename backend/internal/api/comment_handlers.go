package api

import (
	"net/http"

	"github.com/go-chi/chi/v5"
)

type createCommentRequest struct {
	ParentID *string `json:"parentId"`
	Body     string  `json:"body"`
}

type updateCommentRequest struct {
	Body string `json:"body"`
}

func (s *Server) handleListComments(w http.ResponseWriter, r *http.Request) {
	videoRefID, err := normalizeID(chi.URLParam(r, "videoRefID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	cursor, err := parseOptionalTime(r.URL.Query().Get("cursor"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "invalid cursor")
		return
	}
	comments, err := s.store.ListComments(r.Context(), videoRefID, parseLimit(r, 30, 100), cursor)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"comments": comments})
}

func (s *Server) handleCreateComment(w http.ResponseWriter, r *http.Request) {
	videoRefID, err := normalizeID(chi.URLParam(r, "videoRefID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	var req createCommentRequest
	if !readJSON(w, r, &req) {
		return
	}
	var parentID *string
	if req.ParentID != nil {
		value, err := normalizeID(*req.ParentID)
		if err != nil {
			writeError(w, http.StatusBadRequest, "bad_request", "invalid parentId")
			return
		}
		parentID = &value
	}
	body, err := normalizeCommentBody(req.Body)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	comment, err := s.store.CreateComment(r.Context(), currentUserID(r), videoRefID, parentID, body)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{"comment": comment})
}

func (s *Server) handleUpdateComment(w http.ResponseWriter, r *http.Request) {
	commentID, err := normalizeID(chi.URLParam(r, "commentID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	var req updateCommentRequest
	if !readJSON(w, r, &req) {
		return
	}
	body, err := normalizeCommentBody(req.Body)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	comment, err := s.store.UpdateComment(r.Context(), currentUserID(r), commentID, body)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"comment": comment})
}

func (s *Server) handleDeleteComment(w http.ResponseWriter, r *http.Request) {
	commentID, err := normalizeID(chi.URLParam(r, "commentID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	if err := s.store.DeleteComment(r.Context(), currentUserID(r), commentID); err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusNoContent, nil)
}

func (s *Server) handleLikeComment(w http.ResponseWriter, r *http.Request) {
	commentID, err := normalizeID(chi.URLParam(r, "commentID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	comment, err := s.store.LikeComment(r.Context(), currentUserID(r), commentID)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"comment": comment})
}

func (s *Server) handleUnlikeComment(w http.ResponseWriter, r *http.Request) {
	commentID, err := normalizeID(chi.URLParam(r, "commentID"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	comment, err := s.store.UnlikeComment(r.Context(), currentUserID(r), commentID)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"comment": comment})
}
