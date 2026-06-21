package service

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"photo-map-app/backend/internal/db/sqlc"
	"photo-map-app/backend/internal/model"
	"photo-map-app/backend/internal/storage"
)

const (
	defaultCleanupLimit = 100
	maxCleanupLimit     = 1000

	auditCleanupDryRun  = "upload_session_cleanup_dry_run"
	auditCleanupDeleted = "upload_session_cleanup_deleted"
	auditCleanupError   = "upload_session_cleanup_error"
	backfillLogInterval = 100
)

type MaintenanceService struct {
	pool           *pgxpool.Pool
	queries        *sqlc.Queries
	storageService *storage.StorageService
}

type CleanupUploadSessionsParams struct {
	DryRun      bool
	OlderThan   time.Duration
	Limit       int
	ActorUserID *string
}

func NewMaintenanceService(pool *pgxpool.Pool, queries *sqlc.Queries, storageService *storage.StorageService) *MaintenanceService {
	return &MaintenanceService{pool: pool, queries: queries, storageService: storageService}
}

func (service *MaintenanceService) CleanupExpiredUploadSessions(
	ctx context.Context,
	dryRun bool,
	olderThan time.Duration,
) (model.CleanupUploadSessionsResult, error) {
	return service.CleanupExpiredUploadSessionsWithOptions(ctx, CleanupUploadSessionsParams{
		DryRun: dryRun, OlderThan: olderThan, Limit: defaultCleanupLimit,
	})
}

func (service *MaintenanceService) BackfillAssetChanges(
	ctx context.Context,
	dryRun bool,
) (model.BackfillAssetChangesResult, error) {
	startedAt := time.Now()
	result := model.BackfillAssetChangesResult{DryRun: dryRun}
	slog.InfoContext(ctx, "asset changes backfill started", slog.Bool("dry_run", dryRun))
	if dryRun {
		count, err := service.queries.CountAssetsWithoutChanges(ctx)
		if err != nil {
			slog.ErrorContext(ctx, "asset changes backfill failed", slog.String("stage", "count_candidates"), slog.Any("error", err))
			return result, err
		}
		result.Candidates = count
		slog.InfoContext(
			ctx,
			"asset changes backfill dry run completed",
			slog.Int64("candidates", count),
			slog.Duration("duration", time.Since(startedAt)),
		)
		return result, nil
	}

	tx, err := service.pool.Begin(ctx)
	if err != nil {
		slog.ErrorContext(ctx, "asset changes backfill failed", slog.String("stage", "begin_transaction"), slog.Any("error", err))
		return result, err
	}
	defer tx.Rollback(ctx)
	queries := service.queries.WithTx(tx)
	slog.InfoContext(ctx, "asset changes backfill waiting for advisory lock")
	if _, err := queries.AcquireAssetChangesBackfillLock(ctx); err != nil {
		slog.ErrorContext(ctx, "asset changes backfill failed", slog.String("stage", "acquire_lock"), slog.Any("error", err))
		return result, err
	}
	slog.InfoContext(ctx, "asset changes backfill advisory lock acquired")

	assets, err := queries.ListAssetsWithoutChanges(ctx)
	if err != nil {
		slog.ErrorContext(ctx, "asset changes backfill failed", slog.String("stage", "list_candidates"), slog.Any("error", err))
		return result, err
	}
	result.Candidates = int64(len(assets))
	slog.InfoContext(ctx, "asset changes backfill candidates loaded", slog.Int("candidates", len(assets)))
	for index, asset := range assets {
		snapshot, err := marshalAssetSnapshot(asset)
		if err != nil {
			slog.ErrorContext(ctx, "asset changes backfill failed", slog.String("stage", "build_snapshot"), slog.Int("processed", index), slog.Any("error", err))
			return result, err
		}
		if _, err := queries.InsertInitialAssetChangeIfMissing(
			ctx,
			sqlc.InsertInitialAssetChangeIfMissingParams{
				UserID: asset.UserID, AssetID: asset.ID, AssetSnapshot: snapshot,
			},
		); err != nil {
			if errors.Is(err, pgx.ErrNoRows) {
				logBackfillProgress(ctx, index+1, len(assets), result.Inserted)
				continue
			}
			slog.ErrorContext(ctx, "asset changes backfill failed", slog.String("stage", "insert_change"), slog.Int("processed", index), slog.Any("error", err))
			return result, err
		}
		result.Inserted++
		logBackfillProgress(ctx, index+1, len(assets), result.Inserted)
	}
	if err := tx.Commit(ctx); err != nil {
		slog.ErrorContext(ctx, "asset changes backfill failed", slog.String("stage", "commit"), slog.Any("error", err))
		return result, err
	}
	slog.InfoContext(
		ctx,
		"asset changes backfill completed",
		slog.Int64("candidates", result.Candidates),
		slog.Int64("inserted", result.Inserted),
		slog.Duration("duration", time.Since(startedAt)),
	)
	return result, nil
}

func logBackfillProgress(
	ctx context.Context,
	processed int,
	total int,
	inserted int64,
) {
	if processed%backfillLogInterval != 0 && processed != total {
		return
	}
	slog.InfoContext(
		ctx,
		"asset changes backfill progress",
		slog.Int("processed", processed),
		slog.Int("total", total),
		slog.Int64("inserted", inserted),
	)
}

func (service *MaintenanceService) CleanupExpiredUploadSessionsWithOptions(
	ctx context.Context,
	params CleanupUploadSessionsParams,
) (model.CleanupUploadSessionsResult, error) {
	result := model.CleanupUploadSessionsResult{
		DryRun: params.DryRun, DeletedObjects: []string{}, ExpiredSessions: []string{}, Errors: []model.CleanupError{},
	}
	if params.OlderThan < 0 {
		return result, errors.New("olderThan cannot be negative")
	}
	limit := params.Limit
	if limit == 0 {
		limit = defaultCleanupLimit
	}
	if limit < 1 || limit > maxCleanupLimit {
		return result, fmt.Errorf("limit must be between 1 and %d", maxCleanupLimit)
	}

	cutoff := time.Now().UTC().Add(-params.OlderThan)
	sessions, err := service.queries.ListExpiredIncompleteUploadSessions(ctx, sqlc.ListExpiredIncompleteUploadSessionsParams{
		ExpiresBefore: cutoff,
		Limit:         int32(limit),
	})
	if err != nil {
		return result, err
	}
	result.Scanned = len(sessions)

	for _, candidate := range sessions {
		keys, cleanupErr := service.cleanupSession(ctx, candidate, cutoff, params)
		if cleanupErr != nil {
			result.Errors = append(result.Errors, *cleanupErr)
			metadata, _ := json.Marshal(map[string]any{
				"objectKey": cleanupErr.ObjectKey,
				"error":     cleanupErr.Message,
			})
			_ = service.writeAudit(
				ctx,
				service.queries,
				params.ActorUserID,
				auditCleanupError,
				candidate.ID,
				string(metadata),
			)
			continue
		}
		if keys == nil {
			continue
		}
		result.DeletedObjects = append(result.DeletedObjects, keys...)
		result.ExpiredSessions = append(result.ExpiredSessions, candidate.ID)
	}

	return result, nil
}

func (service *MaintenanceService) cleanupSession(
	ctx context.Context,
	candidate sqlc.UploadSession,
	cutoff time.Time,
	params CleanupUploadSessionsParams,
) ([]string, *model.CleanupError) {
	tx, err := service.pool.Begin(ctx)
	if err != nil {
		return nil, newCleanupError(candidate.ID, nil, err)
	}
	defer tx.Rollback(ctx)

	queries := service.queries.WithTx(tx)
	session, err := queries.GetUploadSessionForUpdate(ctx, sqlc.GetUploadSessionForUpdateParams{
		ID: candidate.ID, UserID: candidate.UserID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, nil
		}
		return nil, newCleanupError(candidate.ID, nil, err)
	}
	if session.AssetID != nil || session.Status == uploadStatusCompleted || !isCleanupStatus(session.Status) || !session.ExpiresAt.Before(cutoff) {
		return nil, nil
	}

	keys := uploadSessionObjectKeys(session)
	for _, key := range keys {
		used, err := queries.CheckObjectKeyUsedByAsset(ctx, key)
		if err != nil {
			return nil, newCleanupError(session.ID, &key, err)
		}
		if used {
			err := fmt.Errorf("object key is referenced by an existing asset")
			return nil, newCleanupError(session.ID, &key, err)
		}
	}

	metadata := cleanupAuditMetadata(session, keys, params.DryRun, params.OlderThan)
	if params.DryRun {
		if err := tx.Rollback(ctx); err != nil && !errors.Is(err, pgx.ErrTxClosed) {
			return nil, newCleanupError(session.ID, nil, err)
		}
		if err := service.writeAudit(ctx, service.queries, params.ActorUserID, auditCleanupDryRun, session.ID, metadata); err != nil {
			return nil, newCleanupError(session.ID, nil, err)
		}
		return keys, nil
	}

	if err := service.storageService.DeleteObjects(ctx, keys); err != nil {
		return nil, newCleanupError(session.ID, nil, err)
	}
	message := fmt.Sprintf("orphan cleanup deleted %d object(s)", len(keys))
	if _, err := queries.MarkUploadSessionExpired(ctx, sqlc.MarkUploadSessionExpiredParams{
		ID: session.ID, UserID: session.UserID, ErrorMessage: &message,
	}); err != nil {
		return nil, newCleanupError(session.ID, nil, err)
	}
	if err := tx.Commit(ctx); err != nil {
		return nil, newCleanupError(session.ID, nil, err)
	}
	_ = service.writeAudit(ctx, service.queries, params.ActorUserID, auditCleanupDeleted, session.ID, metadata)
	return keys, nil
}

func newCleanupError(
	sessionID string,
	objectKey *string,
	cause error,
) *model.CleanupError {
	return &model.CleanupError{SessionID: &sessionID, ObjectKey: objectKey, Message: cause.Error()}
}

func (service *MaintenanceService) writeAudit(
	ctx context.Context,
	queries *sqlc.Queries,
	actorUserID *string,
	action string,
	sessionID string,
	metadata string,
) error {
	entityType := "upload_session"
	return queries.CreateAuditLog(ctx, sqlc.CreateAuditLogParams{
		UserID: actorUserID, Action: action, EntityType: &entityType, EntityID: &sessionID, Metadata: metadata,
	})
}

func uploadSessionObjectKeys(session sqlc.UploadSession) []string {
	values := []*string{&session.ObjectKey, session.ThumbnailKey, session.PreviewKey, session.PosterFrameKey}
	keys := make([]string, 0, len(values))
	seen := make(map[string]struct{}, len(values))
	for _, value := range values {
		if value == nil {
			continue
		}
		key := strings.TrimSpace(*value)
		if key == "" {
			continue
		}
		if _, exists := seen[key]; exists {
			continue
		}
		seen[key] = struct{}{}
		keys = append(keys, key)
	}
	return keys
}

func cleanupAuditMetadata(session sqlc.UploadSession, keys []string, dryRun bool, olderThan time.Duration) string {
	metadata, _ := json.Marshal(map[string]any{
		"dryRun": dryRun, "objectKeys": keys, "olderThanSeconds": int64(olderThan.Seconds()), "previousStatus": session.Status,
	})
	return string(metadata)
}

func isCleanupStatus(status string) bool {
	switch status {
	case uploadStatusCreated, uploadStatusUploading, uploadStatusUploaded, uploadStatusFailed, uploadStatusExpired:
		return true
	default:
		return false
	}
}
