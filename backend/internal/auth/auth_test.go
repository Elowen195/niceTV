package auth

import (
	"testing"
	"time"
)

func TestPasswordHashAndCheck(t *testing.T) {
	hash, err := HashPassword("secret123")
	if err != nil {
		t.Fatalf("hash password: %v", err)
	}
	if hash == "secret123" {
		t.Fatal("password hash should not equal plaintext")
	}
	if !CheckPassword(hash, "secret123") {
		t.Fatal("expected password to match hash")
	}
	if CheckPassword(hash, "wrong-password") {
		t.Fatal("wrong password should not match hash")
	}
}

func TestIssueAndParseAccessToken(t *testing.T) {
	token, expiresAt, err := IssueAccessToken("user-1", "test-secret", time.Minute)
	if err != nil {
		t.Fatalf("issue token: %v", err)
	}
	if token == "" {
		t.Fatal("token should not be empty")
	}
	if time.Until(expiresAt) <= 0 {
		t.Fatal("token expiry should be in the future")
	}

	claims, err := ParseAccessToken(token, "test-secret")
	if err != nil {
		t.Fatalf("parse token: %v", err)
	}
	if claims.UserID != "user-1" {
		t.Fatalf("unexpected user id: %s", claims.UserID)
	}
	if _, err := ParseAccessToken(token, "other-secret"); err == nil {
		t.Fatal("token should fail with wrong secret")
	}
}

func TestRefreshTokenHash(t *testing.T) {
	token, hash, err := NewRefreshToken()
	if err != nil {
		t.Fatalf("new refresh token: %v", err)
	}
	if token == "" || hash == "" {
		t.Fatal("token and hash should not be empty")
	}
	if token == hash {
		t.Fatal("refresh token hash should not expose raw token")
	}
	if HashRefreshToken(token) != hash {
		t.Fatal("refresh token hash should be stable")
	}
}
