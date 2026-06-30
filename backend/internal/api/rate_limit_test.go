package api

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestRateLimitAuthRequests(t *testing.T) {
	s := &Server{limiter: newRateLimiter()}
	next := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	})
	handler := s.rateLimit(next)

	for i := 0; i < 10; i++ {
		req := httptest.NewRequest(http.MethodPost, "/v1/auth/login", nil)
		req.RemoteAddr = "203.0.113.10:12345"
		rec := httptest.NewRecorder()
		handler.ServeHTTP(rec, req)
		if rec.Code != http.StatusNoContent {
			t.Fatalf("request %d status = %d, want %d", i+1, rec.Code, http.StatusNoContent)
		}
	}

	req := httptest.NewRequest(http.MethodPost, "/v1/auth/login", nil)
	req.RemoteAddr = "203.0.113.10:12345"
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusTooManyRequests {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusTooManyRequests)
	}
	if rec.Header().Get("Retry-After") == "" {
		t.Fatal("Retry-After header is empty")
	}
}

func TestRateLimitUsesForwardedIP(t *testing.T) {
	limiter := newRateLimiter()
	s := &Server{limiter: limiter}
	next := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	})
	handler := s.rateLimit(next)

	for i := 0; i < 10; i++ {
		req := httptest.NewRequest(http.MethodPost, "/v1/auth/login", nil)
		req.RemoteAddr = "127.0.0.1:12345"
		req.Header.Set("X-Forwarded-For", "198.51.100.20")
		rec := httptest.NewRecorder()
		handler.ServeHTTP(rec, req)
		if rec.Code != http.StatusNoContent {
			t.Fatalf("forwarded request %d status = %d", i+1, rec.Code)
		}
	}

	req := httptest.NewRequest(http.MethodPost, "/v1/auth/login", nil)
	req.RemoteAddr = "127.0.0.1:12345"
	req.Header.Set("X-Forwarded-For", "198.51.100.21")
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusNoContent {
		t.Fatalf("different forwarded IP status = %d, want %d", rec.Code, http.StatusNoContent)
	}
}

func TestRateLimitWindowResets(t *testing.T) {
	limiter := newRateLimiter()
	now := time.Date(2026, 7, 1, 0, 0, 0, 0, time.UTC)
	limiter.now = func() time.Time { return now }

	rule := rateLimitRule{name: "test", limit: 1, window: time.Minute}
	if allowed, _ := limiter.allow("key", rule); !allowed {
		t.Fatal("first request should be allowed")
	}
	if allowed, _ := limiter.allow("key", rule); allowed {
		t.Fatal("second request should be denied")
	}

	now = now.Add(time.Minute + time.Second)
	if allowed, _ := limiter.allow("key", rule); !allowed {
		t.Fatal("request after reset should be allowed")
	}
}
