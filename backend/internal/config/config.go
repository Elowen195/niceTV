package config

import (
	"fmt"
	"os"
	"time"
)

type Config struct {
	Addr            string
	DatabaseURL     string
	JWTSecret       string
	AccessTokenTTL  time.Duration
	RefreshTokenTTL time.Duration
	CORSOrigin      string
}

func Load() (Config, error) {
	cfg := Config{
		Addr:            env("ADDR", ":8080"),
		DatabaseURL:     os.Getenv("DATABASE_URL"),
		JWTSecret:       os.Getenv("JWT_SECRET"),
		AccessTokenTTL:  durationEnv("ACCESS_TOKEN_TTL", 15*time.Minute),
		RefreshTokenTTL: durationEnv("REFRESH_TOKEN_TTL", 30*24*time.Hour),
		CORSOrigin:      env("CORS_ORIGIN", "*"),
	}
	if cfg.DatabaseURL == "" {
		return Config{}, fmt.Errorf("DATABASE_URL is required")
	}
	if cfg.JWTSecret == "" || cfg.JWTSecret == "change-me-before-deploy" {
		if os.Getenv("APP_ENV") == "production" {
			return Config{}, fmt.Errorf("JWT_SECRET must be set in production")
		}
		if cfg.JWTSecret == "" {
			cfg.JWTSecret = "dev-only-secret"
		}
	}
	return cfg, nil
}

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func durationEnv(key string, fallback time.Duration) time.Duration {
	raw := os.Getenv(key)
	if raw == "" {
		return fallback
	}
	value, err := time.ParseDuration(raw)
	if err != nil {
		return fallback
	}
	return value
}
