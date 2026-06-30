package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"nicetv/backend/internal/api"
	"nicetv/backend/internal/config"
	"nicetv/backend/internal/store"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		slog.Error("load config", "error", err)
		os.Exit(1)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	pg, err := store.OpenPostgres(ctx, cfg.DatabaseURL)
	if err != nil {
		slog.Error("connect postgres", "error", err)
		os.Exit(1)
	}
	defer pg.Close()

	router := api.NewServer(cfg, pg).Routes()
	srv := &http.Server{
		Addr:              cfg.Addr,
		Handler:           router,
		ReadHeaderTimeout: 5 * time.Second,
	}

	go func() {
		slog.Info("nicetv api listening", "addr", cfg.Addr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("http server", "error", err)
			stop()
		}
	}()

	<-ctx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		slog.Error("shutdown", "error", err)
	}
}
