package api

import (
	"fmt"
	"net/url"
	"regexp"
	"strings"

	"github.com/google/uuid"
)

var usernamePattern = regexp.MustCompile(`^[A-Za-z0-9_]{3,40}$`)

func normalizeUsername(username string) (string, error) {
	username = strings.TrimSpace(username)
	if !usernamePattern.MatchString(username) {
		return "", fmt.Errorf("username must be 3-40 letters, numbers, or underscores")
	}
	return username, nil
}

func normalizeLogin(login string) (string, error) {
	login = strings.TrimSpace(login)
	if login == "" || len(login) > 255 {
		return "", fmt.Errorf("login is required")
	}
	return login, nil
}

func normalizePassword(password string) error {
	if len(password) < 6 || len(password) > 128 {
		return fmt.Errorf("password must be 6-128 characters")
	}
	return nil
}

func normalizeEmail(email *string) (*string, error) {
	if email == nil {
		return nil, nil
	}
	value := strings.ToLower(strings.TrimSpace(*email))
	if value == "" {
		return nil, nil
	}
	if len(value) > 255 || !strings.Contains(value, "@") {
		return nil, fmt.Errorf("invalid email")
	}
	return &value, nil
}

func normalizeOptionalText(value *string, max int) (*string, error) {
	if value == nil {
		return nil, nil
	}
	trimmed := strings.TrimSpace(*value)
	if len(trimmed) > max {
		return nil, fmt.Errorf("text too long")
	}
	return &trimmed, nil
}

func normalizeSource(source string) string {
	source = strings.ToLower(strings.TrimSpace(source))
	if source == "" {
		return "supjav"
	}
	return source
}

func normalizeURL(raw string) (string, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" || len(raw) > 2048 {
		return "", fmt.Errorf("url is required")
	}
	parsed, err := url.ParseRequestURI(raw)
	if err != nil || parsed.Scheme == "" || parsed.Host == "" {
		return "", fmt.Errorf("invalid url")
	}
	if parsed.Scheme != "http" && parsed.Scheme != "https" {
		return "", fmt.Errorf("invalid url scheme")
	}
	return raw, nil
}

func normalizeID(id string) (string, error) {
	id = strings.TrimSpace(id)
	if _, err := uuid.Parse(id); err != nil {
		return "", fmt.Errorf("invalid id")
	}
	return id, nil
}

func normalizeCommentBody(body string) (string, error) {
	body = strings.TrimSpace(body)
	if body == "" || len([]rune(body)) > 1000 {
		return "", fmt.Errorf("comment body must be 1-1000 characters")
	}
	return body, nil
}
