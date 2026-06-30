package api

import (
	"net"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"
)

type rateLimitRule struct {
	name   string
	limit  int
	window time.Duration
}

type rateBucket struct {
	count int
	reset time.Time
}

type rateLimiter struct {
	mu      sync.Mutex
	buckets map[string]rateBucket
	now     func() time.Time
}

func newRateLimiter() *rateLimiter {
	return &rateLimiter{
		buckets: make(map[string]rateBucket),
		now:     time.Now,
	}
}

func (s *Server) rateLimit(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodOptions || s.limiter == nil {
			next.ServeHTTP(w, r)
			return
		}
		rule := rateLimitRuleFor(r)
		if rule.limit <= 0 {
			next.ServeHTTP(w, r)
			return
		}
		key := clientIP(r) + ":" + rule.name
		allowed, retryAfter := s.limiter.allow(key, rule)
		if !allowed {
			w.Header().Set("Retry-After", strconv.Itoa(int(retryAfter.Seconds())+1))
			writeError(w, http.StatusTooManyRequests, "rate_limited", "too many requests")
			return
		}
		next.ServeHTTP(w, r)
	})
}

func (l *rateLimiter) allow(key string, rule rateLimitRule) (bool, time.Duration) {
	now := l.now()
	l.mu.Lock()
	defer l.mu.Unlock()

	if len(l.buckets) > 10000 {
		for bucketKey, bucket := range l.buckets {
			if now.After(bucket.reset) {
				delete(l.buckets, bucketKey)
			}
		}
	}

	bucket := l.buckets[key]
	if bucket.reset.IsZero() || now.After(bucket.reset) {
		l.buckets[key] = rateBucket{count: 1, reset: now.Add(rule.window)}
		return true, 0
	}
	if bucket.count >= rule.limit {
		return false, bucket.reset.Sub(now)
	}
	bucket.count++
	l.buckets[key] = bucket
	return true, 0
}

func rateLimitRuleFor(r *http.Request) rateLimitRule {
	path := r.URL.Path
	switch {
	case strings.HasPrefix(path, "/v1/auth/login"),
		strings.HasPrefix(path, "/v1/auth/register"),
		strings.HasPrefix(path, "/v1/auth/refresh"):
		return rateLimitRule{name: "auth", limit: 10, window: time.Minute}
	case r.Method == http.MethodPost ||
		r.Method == http.MethodPut ||
		r.Method == http.MethodPatch ||
		r.Method == http.MethodDelete:
		return rateLimitRule{name: "write", limit: 90, window: time.Minute}
	default:
		return rateLimitRule{name: "read", limit: 300, window: time.Minute}
	}
}

func clientIP(r *http.Request) string {
	forwardedFor := strings.TrimSpace(r.Header.Get("X-Forwarded-For"))
	if forwardedFor != "" {
		ip := strings.TrimSpace(strings.Split(forwardedFor, ",")[0])
		if parsed := net.ParseIP(ip); parsed != nil {
			return parsed.String()
		}
	}
	realIP := strings.TrimSpace(r.Header.Get("X-Real-IP"))
	if parsed := net.ParseIP(realIP); parsed != nil {
		return parsed.String()
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err == nil {
		return host
	}
	return r.RemoteAddr
}
