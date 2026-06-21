package main

import (
	"context"
	"encoding/json"
	"errors"
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
	slog.SetDefault(logger)
	if len(os.Args) < 2 {
		writeUsage()
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

	maintenanceService := service.NewMaintenanceService(
		pool,
		sqlc.New(pool),
		storage.NewStorageService(cfg),
	)

	var result any
	switch os.Args[1] {
	case "cleanup-upload-sessions":
		result, err = runCleanup(ctx, maintenanceService, os.Args[2:])
	case "backfill-asset-changes":
		result, err = runAssetChangesBackfill(ctx, maintenanceService, os.Args[2:])
	default:
		writeUsage()
		os.Exit(2)
	}
	if err != nil {
		logger.Error("maintenance command failed", slog.String("command", os.Args[1]), slog.Any("error", err))
		os.Exit(1)
	}
	if err := json.NewEncoder(os.Stdout).Encode(result); err != nil {
		logger.Error("failed to write result", slog.Any("error", err))
		os.Exit(1)
	}
}

func runCleanup(
	ctx context.Context,
	maintenanceService *service.MaintenanceService,
	args []string,
) (any, error) {
	flags := flag.NewFlagSet("cleanup-upload-sessions", flag.ContinueOnError)
	dryRun := flags.Bool("dry-run", false, "list objects without deleting them")
	olderThan := flags.Duration("older-than", 24*time.Hour, "minimum time since session expiry")
	limit := flags.Int("limit", 100, "maximum sessions to scan")
	if err := flags.Parse(args); err != nil {
		return nil, err
	}
	return maintenanceService.CleanupExpiredUploadSessionsWithOptions(ctx, service.CleanupUploadSessionsParams{
		DryRun: *dryRun, OlderThan: *olderThan, Limit: *limit,
	})
}

func runAssetChangesBackfill(
	ctx context.Context,
	maintenanceService *service.MaintenanceService,
	args []string,
) (any, error) {
	flags := flag.NewFlagSet("backfill-asset-changes", flag.ContinueOnError)
	dryRun := flags.Bool("dry-run", false, "report assets without inserting changes")
	live := flags.Bool("live", false, "insert missing initial asset changes")
	if err := flags.Parse(args); err != nil {
		return nil, err
	}
	if *dryRun == *live {
		return nil, errors.New("specify exactly one of --dry-run or --live")
	}
	return maintenanceService.BackfillAssetChanges(ctx, *dryRun)
}

func writeUsage() {
	fmt.Fprintln(os.Stderr, "usage:")
	fmt.Fprintln(os.Stderr, "  go run ./cmd/maintenance cleanup-upload-sessions [--dry-run] [--older-than=24h] [--limit=100]")
	fmt.Fprintln(os.Stderr, "  go run ./cmd/maintenance backfill-asset-changes --dry-run")
	fmt.Fprintln(os.Stderr, "  go run ./cmd/maintenance backfill-asset-changes --live")
}
