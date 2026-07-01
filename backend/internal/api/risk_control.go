package api

import (
	"net/http"
	"strconv"
	"strings"
	"time"

	"nicetv/backend/internal/models"
)

func (s *Server) riskGuard(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodOptions || r.Method == http.MethodGet {
			next.ServeHTTP(w, r)
			return
		}

		user := currentUser(r)
		if isCommunityWrite(r) && isUserMuted(user) {
			writeError(w, http.StatusForbidden, "account_muted", "account is muted")
			return
		}

		rule := userRiskRuleFor(r, user)
		if rule.limit > 0 && s.limiter != nil {
			key := "user:" + user.ID + ":" + rule.name
			allowed, retryAfter := s.limiter.allow(key, rule)
			if !allowed {
				w.Header().Set("Retry-After", strconv.Itoa(int(retryAfter.Seconds())+1))
				writeError(w, http.StatusTooManyRequests, "rate_limited", "too many requests")
				return
			}
		}

		next.ServeHTTP(w, r)
	})
}

func (s *Server) adminRequired(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		user := currentUser(r)
		if user.Role != "admin" && user.Role != "moderator" {
			writeError(w, http.StatusForbidden, "admin_required", "admin permission required")
			return
		}
		next.ServeHTTP(w, r)
	})
}

func userRiskRuleFor(r *http.Request, user models.User) rateLimitRule {
	path := r.URL.Path
	newAccount := time.Since(user.CreatedAt) < 10*time.Minute

	switch {
	case r.Method == http.MethodPost && strings.Contains(path, "/comments"):
		if newAccount {
			return rateLimitRule{name: "new_comment", limit: 2, window: time.Minute}
		}
		return rateLimitRule{name: "comment", limit: 5, window: time.Minute}
	case strings.Contains(path, "/comments/") && strings.HasSuffix(path, "/like") && (r.Method == http.MethodPut || r.Method == http.MethodDelete):
		return rateLimitRule{name: "comment_reaction", limit: 60, window: time.Minute}
	case strings.Contains(path, "/comments/") && (r.Method == http.MethodPatch || r.Method == http.MethodDelete):
		return rateLimitRule{name: "comment_edit", limit: 20, window: time.Minute}
	case r.Method == http.MethodPost && path == "/v1/collections":
		if newAccount {
			return rateLimitRule{name: "new_collection_create", limit: 2, window: time.Hour}
		}
		return rateLimitRule{name: "collection_create", limit: 10, window: 24 * time.Hour}
	case r.Method == http.MethodPost && strings.HasPrefix(path, "/v1/collections/") && strings.HasSuffix(path, "/copy"):
		return rateLimitRule{name: "collection_copy", limit: 10, window: 24 * time.Hour}
	case strings.HasPrefix(path, "/v1/collections/") && strings.Contains(path, "/items"):
		return rateLimitRule{name: "collection_items", limit: 60, window: time.Minute}
	case strings.HasPrefix(path, "/v1/collections/") && (r.Method == http.MethodPatch || r.Method == http.MethodDelete):
		return rateLimitRule{name: "collection_edit", limit: 30, window: time.Minute}
	case strings.HasPrefix(path, "/v1/favorites"):
		return rateLimitRule{name: "favorites", limit: 60, window: time.Minute}
	default:
		return rateLimitRule{name: "auth_write", limit: 120, window: time.Minute}
	}
}

func isCommunityWrite(r *http.Request) bool {
	path := r.URL.Path
	return strings.Contains(path, "/comments") ||
		strings.HasPrefix(path, "/v1/collections")
}

func isUserMuted(user models.User) bool {
	return user.MutedUntil != nil && time.Now().UTC().Before(user.MutedUntil.UTC())
}

func isUserBanned(user models.User) bool {
	return user.Status == "banned" || user.BannedAt != nil
}
