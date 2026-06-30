package api

import "testing"

func TestNormalizeUsername(t *testing.T) {
	got, err := normalizeUsername(" furi_01 ")
	if err != nil {
		t.Fatalf("normalize username: %v", err)
	}
	if got != "furi_01" {
		t.Fatalf("unexpected username: %q", got)
	}
	if _, err := normalizeUsername("ab"); err == nil {
		t.Fatal("short username should fail")
	}
	if _, err := normalizeUsername("bad-name"); err == nil {
		t.Fatal("username with dash should fail")
	}
}

func TestNormalizeURL(t *testing.T) {
	got, err := normalizeURL("https://supjav.com/438815.html")
	if err != nil {
		t.Fatalf("normalize url: %v", err)
	}
	if got != "https://supjav.com/438815.html" {
		t.Fatalf("unexpected url: %q", got)
	}
	if _, err := normalizeURL("file:///tmp/video.mp4"); err == nil {
		t.Fatal("non-http url should fail")
	}
	if _, err := normalizeURL("not-a-url"); err == nil {
		t.Fatal("invalid url should fail")
	}
}

func TestNormalizeCommentBody(t *testing.T) {
	got, err := normalizeCommentBody(" hello ")
	if err != nil {
		t.Fatalf("normalize comment body: %v", err)
	}
	if got != "hello" {
		t.Fatalf("unexpected body: %q", got)
	}
	if _, err := normalizeCommentBody("   "); err == nil {
		t.Fatal("blank comment should fail")
	}
}
