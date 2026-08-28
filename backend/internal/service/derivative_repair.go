package service

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/jackc/pgx/v5"

	"photo-map-app/backend/internal/db/sqlc"
	"photo-map-app/backend/internal/model"
)

const (
	defaultDerivativeRepairLimit = 100
	maxDerivativeRepairLimit     = 1000
	defaultDerivativeParallel    = 1
	maxDerivativeParallel        = 32
	maxPreviewObjectBytes        = 25 * 1024 * 1024
	webpContentType              = "image/webp"
	derivativeRepairLockName     = "asset_derivative_repair_v2"
)

const listDerivativeRepairCandidatesSQL = `
SELECT a.id::text, a.user_id::text, a.thumbnail_key, a.preview_key, a.orientation
FROM assets a
WHERE a.media_type = 'image'
  AND a.orientation BETWEEN 2 AND 8
  AND a.derivative_version < 2
  AND ($1::uuid IS NULL OR a.user_id = $1::uuid)
  AND ($2::uuid IS NULL OR a.id > $2::uuid)
  AND NOT EXISTS (
    SELECT 1 FROM asset_derivative_repairs repair WHERE repair.asset_id = a.id
  )
ORDER BY a.id
LIMIT $3`

type RebuildDerivativesParams struct {
	DryRun   bool
	Auto     bool
	Limit    int
	Parallel int
	UserID   *string
}

type derivativeRepairCandidate struct {
	AssetID      string
	UserID       string
	ThumbnailKey *string
	PreviewKey   *string
	Orientation  int16
}

type derivativeRepairOutcome struct {
	Stage string
	Err   error
}

func (service *MaintenanceService) RebuildDerivatives(
	ctx context.Context,
	params RebuildDerivativesParams,
) (model.RebuildDerivativesResult, error) {
	result := model.RebuildDerivativesResult{
		DryRun: params.DryRun,
		Auto:   params.Auto,
		Errors: []model.DerivativeRepairError{},
	}
	if params.Auto && params.DryRun {
		return result, errors.New("auto mode requires --live")
	}
	limit, err := normalizeDerivativeRepairLimit(params.Limit)
	if err != nil {
		return result, err
	}
	parallel, err := normalizeDerivativeParallel(params.Parallel)
	if err != nil {
		return result, err
	}
	result.Parallel = parallel

	releaseLock := func() {}
	if !params.DryRun {
		releaseLock, err = service.acquireDerivativeRepairLock(ctx)
		if err != nil {
			return result, err
		}
		defer releaseLock()
	}

	var afterAssetID *string
	for {
		candidates, err := service.listDerivativeRepairCandidates(ctx, params.UserID, afterAssetID, limit)
		if err != nil {
			return result, err
		}
		if len(candidates) == 0 {
			break
		}
		result.Batches++
		result.Candidates += len(candidates)
		if params.DryRun {
			result.CandidateAssets = make([]model.DerivativeRepairCandidate, 0, len(candidates))
			for _, candidate := range candidates {
				result.CandidateAssets = append(result.CandidateAssets, model.DerivativeRepairCandidate{
					AssetID: candidate.AssetID, Orientation: candidate.Orientation,
					ThumbnailKey: stringValue(candidate.ThumbnailKey), PreviewKey: stringValue(candidate.PreviewKey),
				})
			}
			break
		}

		outcomes := service.repairDerivativesInParallel(ctx, candidates, parallel, result.Batches)
		for index, candidate := range candidates {
			result.Processed++
			outcome := outcomes[index]
			if outcome.Err != nil {
				result.Errors = append(result.Errors, model.DerivativeRepairError{
					AssetID: candidate.AssetID, Stage: outcome.Stage, Message: outcome.Err.Error(),
				})
				continue
			}
			result.Repaired++
		}
		if !params.Auto {
			break
		}
		lastAssetID := candidates[len(candidates)-1].AssetID
		afterAssetID = &lastAssetID
		if err := ctx.Err(); err != nil {
			return result, err
		}
	}
	return result, nil
}

func (service *MaintenanceService) repairDerivativesInParallel(
	ctx context.Context,
	candidates []derivativeRepairCandidate,
	parallel int,
	batch int,
) []derivativeRepairOutcome {
	outcomes := make([]derivativeRepairOutcome, len(candidates))
	jobs := make(chan int, len(candidates))
	for index := range candidates {
		jobs <- index
	}
	close(jobs)

	workerCount := min(parallel, len(candidates))
	var processedCount atomic.Int64
	var repairedCount atomic.Int64
	var failedCount atomic.Int64
	var workers sync.WaitGroup
	workers.Add(workerCount)
	for range workerCount {
		go func() {
			defer workers.Done()
			for index := range jobs {
				candidate := candidates[index]
				stage, err := service.repairDerivative(ctx, candidate)
				outcomes[index] = derivativeRepairOutcome{Stage: stage, Err: err}
				processed := processedCount.Add(1)
				if err != nil {
					failed := failedCount.Add(1)
					slog.ErrorContext(
						ctx,
						"derivative repair failed",
						slog.String("asset_id", candidate.AssetID),
						slog.String("stage", stage),
						slog.Int("batch", batch),
						slog.Int64("processed", processed),
						slog.Int("total", len(candidates)),
						slog.Int64("repaired", repairedCount.Load()),
						slog.Int64("failed", failed),
						slog.Any("error", err),
					)
					continue
				}
				repaired := repairedCount.Add(1)
				slog.InfoContext(
					ctx,
					"derivative repair completed",
					slog.String("asset_id", candidate.AssetID),
					slog.Int("orientation", int(candidate.Orientation)),
					slog.Int("batch", batch),
					slog.Int64("processed", processed),
					slog.Int("total", len(candidates)),
					slog.Int64("repaired", repaired),
					slog.Int64("failed", failedCount.Load()),
				)
			}
		}()
	}
	workers.Wait()
	return outcomes
}

func normalizeDerivativeRepairLimit(limit int) (int, error) {
	if limit == 0 {
		return defaultDerivativeRepairLimit, nil
	}
	if limit < 1 || limit > maxDerivativeRepairLimit {
		return 0, fmt.Errorf("limit must be between 1 and %d", maxDerivativeRepairLimit)
	}
	return limit, nil
}

func normalizeDerivativeParallel(parallel int) (int, error) {
	if parallel == 0 {
		return defaultDerivativeParallel, nil
	}
	if parallel < 1 || parallel > maxDerivativeParallel {
		return 0, fmt.Errorf("parallel must be between 1 and %d", maxDerivativeParallel)
	}
	return parallel, nil
}

func (service *MaintenanceService) acquireDerivativeRepairLock(ctx context.Context) (func(), error) {
	connection, err := service.pool.Acquire(ctx)
	if err != nil {
		return nil, err
	}
	var acquired bool
	err = connection.QueryRow(ctx, "SELECT pg_try_advisory_lock(hashtext($1))", derivativeRepairLockName).Scan(&acquired)
	if err != nil {
		connection.Release()
		return nil, err
	}
	if !acquired {
		connection.Release()
		return nil, errors.New("another derivative repair command is already running")
	}
	return func() {
		unlockContext, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		_, unlockErr := connection.Exec(unlockContext, "SELECT pg_advisory_unlock(hashtext($1))", derivativeRepairLockName)
		cancel()
		if unlockErr != nil {
			_ = connection.Hijack().Close(context.Background())
			return
		}
		connection.Release()
	}, nil
}

func (service *MaintenanceService) listDerivativeRepairCandidates(
	ctx context.Context,
	userID *string,
	afterAssetID *string,
	limit int,
) ([]derivativeRepairCandidate, error) {
	rows, err := service.pool.Query(ctx, listDerivativeRepairCandidatesSQL, userID, afterAssetID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	candidates := make([]derivativeRepairCandidate, 0, limit)
	for rows.Next() {
		var candidate derivativeRepairCandidate
		if err := rows.Scan(
			&candidate.AssetID,
			&candidate.UserID,
			&candidate.ThumbnailKey,
			&candidate.PreviewKey,
			&candidate.Orientation,
		); err != nil {
			return nil, err
		}
		candidates = append(candidates, candidate)
	}
	return candidates, rows.Err()
}

func (service *MaintenanceService) repairDerivative(
	ctx context.Context,
	candidate derivativeRepairCandidate,
) (string, error) {
	thumbnailKey, err := requiredDerivativeKey(candidate.ThumbnailKey, "thumbnail")
	if err != nil {
		return "validate_keys", err
	}
	previewKey, err := requiredDerivativeKey(candidate.PreviewKey, "preview")
	if err != nil {
		return "validate_keys", err
	}
	repairedThumbnailKey, err := versionDerivativeKey(thumbnailKey, thumbnailMaxDimension)
	if err != nil {
		return "validate_keys", err
	}
	repairedPreviewKey, err := versionDerivativeKey(previewKey, previewMaxDimension)
	if err != nil {
		return "validate_keys", err
	}

	source, err := service.storageService.ReadObject(ctx, previewKey, maxPreviewObjectBytes)
	if err != nil {
		return "download_preview", err
	}
	thumbnail, preview, err := repairDerivativeImages(source, candidate.Orientation)
	if err != nil {
		return "transform", err
	}
	if err := service.storageService.PutObject(ctx, repairedPreviewKey, webpContentType, preview); err != nil {
		return "upload_preview", err
	}
	if err := service.storageService.PutObject(ctx, repairedThumbnailKey, webpContentType, thumbnail); err != nil {
		return "upload_thumbnail", err
	}
	if err := service.verifyDerivative(ctx, repairedPreviewKey, int64(len(preview))); err != nil {
		return "verify_preview", err
	}
	if err := service.verifyDerivative(ctx, repairedThumbnailKey, int64(len(thumbnail))); err != nil {
		return "verify_thumbnail", err
	}

	err = service.commitDerivativeRepair(ctx, candidate, thumbnailKey, previewKey, repairedThumbnailKey, repairedPreviewKey)
	if err != nil {
		return "update_database", err
	}
	return "", nil
}

func requiredDerivativeKey(key *string, variant string) (string, error) {
	if key == nil || strings.TrimSpace(*key) == "" {
		return "", fmt.Errorf("asset has no %s key", variant)
	}
	return strings.TrimSpace(*key), nil
}

func (service *MaintenanceService) verifyDerivative(ctx context.Context, key string, expectedSize int64) error {
	info, err := service.storageService.HeadObject(ctx, key)
	if err != nil {
		return err
	}
	if info.ContentLength != expectedSize {
		return fmt.Errorf("object %q has size %d, expected %d", key, info.ContentLength, expectedSize)
	}
	return nil
}

func (service *MaintenanceService) commitDerivativeRepair(
	ctx context.Context,
	candidate derivativeRepairCandidate,
	sourceThumbnailKey string,
	sourcePreviewKey string,
	repairedThumbnailKey string,
	repairedPreviewKey string,
) error {
	transaction, err := service.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer transaction.Rollback(ctx)

	command, err := transaction.Exec(ctx, `
UPDATE assets
SET thumbnail_key = $3, preview_key = $4, derivative_version = 2
WHERE id = $1::uuid
  AND user_id = $2::uuid
  AND thumbnail_key = $5
  AND preview_key = $6`,
		candidate.AssetID,
		candidate.UserID,
		repairedThumbnailKey,
		repairedPreviewKey,
		sourceThumbnailKey,
		sourcePreviewKey,
	)
	if err != nil {
		return err
	}
	if command.RowsAffected() != 1 {
		return errors.New("asset derivative keys changed while the repair was running")
	}

	_, err = transaction.Exec(ctx, `
INSERT INTO asset_derivative_repairs (
  asset_id, source_thumbnail_key, source_preview_key,
  repaired_thumbnail_key, repaired_preview_key, original_orientation
)
VALUES ($1::uuid, $2, $3, $4, $5, $6)`,
		candidate.AssetID,
		sourceThumbnailKey,
		sourcePreviewKey,
		repairedThumbnailKey,
		repairedPreviewKey,
		candidate.Orientation,
	)
	if err != nil {
		return err
	}

	queries := service.queries.WithTx(transaction)
	asset, err := queries.GetAssetByIDForUser(ctx, sqlc.GetAssetByIDForUserParams{
		ID: candidate.AssetID, UserID: candidate.UserID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return errors.New("asset disappeared while the repair was running")
		}
		return err
	}
	if _, err := insertAssetChange(ctx, queries, asset, assetChangeUpsert); err != nil {
		return err
	}
	return transaction.Commit(ctx)
}
