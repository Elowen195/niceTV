package api

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"nicetv/backend/internal/models"
)

func TestRiskGuardBlocksMutedCommunityWrites(t *testing.T) {
	s := &Server{limiter: newRateLimiter()}
	next := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	})
	handler := s.riskGuard(next)

	mutedUntil := time.Now().UTC().Add(time.Hour)
	req := httptest.NewRequest(http.MethodPost, "/v1/video-refs/video-id/comments", nil)
	req = withRequestUser(req, models.User{ID: "user-1", Role: "user", Status: "active", MutedUntil: &mutedUntil, CreatedAt: time.Now().UTC().Add(-time.Hour)})
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusForbidden {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusForbidden)
	}
}

func TestRiskGuardUsesStricterNewAccountCommentLimit(t *testing.T) {
	s := &Server{limiter: newRateLimiter()}
	next := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	})
	handler := s.riskGuard(next)
	user := models.User{ID: "user-1", Role: "user", Status: "active", CreatedAt: time.Now().UTC()}

	for i := 0; i < 2; i++ {
		req := httptest.NewRequest(http.MethodPost, "/v1/video-refs/video-id/comments", nil)
		req = withRequestUser(req, user)
		rec := httptest.NewRecorder()
		handler.ServeHTTP(rec, req)
		if rec.Code != http.StatusNoContent {
			t.Fatalf("request %d status = %d, want %d", i+1, rec.Code, http.StatusNoContent)
		}
	}

	req := httptest.NewRequest(http.MethodPost, "/v1/video-refs/video-id/comments", nil)
	req = withRequestUser(req, user)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusTooManyRequests {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusTooManyRequests)
	}
}

func TestAdminRequiredAllowsOnlyModeratorOrAdmin(t *testing.T) {
	s := &Server{}
	next := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	})
	handler := s.adminRequired(next)

	req := httptest.NewRequest(http.MethodGet, "/v1/admin/users", nil)
	req = withRequestUser(req, models.User{ID: "user-1", Role: "user", Status: "active"})
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusForbidden {
		t.Fatalf("user status = %d, want %d", rec.Code, http.StatusForbidden)
	}

	req = httptest.NewRequest(http.MethodGet, "/v1/admin/users", nil)
	req = withRequestUser(req, models.User{ID: "mod-1", Role: "moderator", Status: "active"})
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusNoContent {
		t.Fatalf("moderator status = %d, want %d", rec.Code, http.StatusNoContent)
	}
}

func withRequestUser(req *http.Request, user models.User) *http.Request {
	ctx := context.WithValue(req.Context(), userIDContextKey, user.ID)
	ctx = context.WithValue(ctx, userContextKey, user)
	return req.WithContext(ctx)
}
