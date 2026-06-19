package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"photo-map-app/backend/internal/config"
	"photo-map-app/backend/internal/db"
	"photo-map-app/backend/internal/db/sqlc"
	"photo-map-app/backend/internal/service"
	"photo-map-app/backend/internal/storage"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stderr, nil))
	if len(os.Args) < 2 || os.Args[1] != "cleanup-upload-sessions" {
		fmt.Fprintln(os.Stderr, "usage: go run ./cmd/maintenance cleanup-upload-sessions [--dry-run] [--older-than=24h] [--limit=100]")
		os.Exit(2)
	}

	flags := flag.NewFlagSet("cleanup-upload-sessions", flag.ExitOnError)
	dryRun := flags.Bool("dry-run", false, "list objects without deleting them")
	olderThan := flags.Duration("older-than", 24*time.Hour, "minimum time since session expiry")
	limit := flags.Int("limit", 100, "maximum sessions to scan")
	if err := flags.Parse(os.Args[2:]); err != nil {
		logger.Error("failed to parse flags", slog.Any("error", err))
		os.Exit(2)
	}

	cfg, err := config.Load()
	if err != nil {
		logger.Error("failed to load config", slog.Any("error", err))
		os.Exit(1)
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	pool, err := db.NewPool(ctx, cfg.DatabaseURL)
	if err != nil {
		logger.Error("failed to connect database", slog.Any("error", err))
		os.Exit(1)
	}
	defer pool.Close()

	queries := sqlc.New(pool)
	maintenanceService := service.NewMaintenanceService(pool, queries, storage.NewStorageService(cfg))
	result, err := maintenanceService.CleanupExpiredUploadSessionsWithOptions(ctx, service.CleanupUploadSessionsParams{
		DryRun: *dryRun, OlderThan: *olderThan, Limit: *limit,
	})
	if err != nil {
		logger.Error("cleanup failed", slog.Any("error", err))
		os.Exit(1)
	}
	if err := json.NewEncoder(os.Stdout).Encode(result); err != nil {
		logger.Error("failed to write result", slog.Any("error", err))
		os.Exit(1)
	}
}
