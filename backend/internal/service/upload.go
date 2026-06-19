package service

import (
	"context"
	"errors"
	"fmt"
	"path/filepath"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"photo-map-app/backend/internal/config"
	"photo-map-app/backend/internal/db/sqlc"
	"photo-map-app/backend/internal/model"
	"photo-map-app/backend/internal/storage"
	"photo-map-app/backend/internal/util"
)

const (
	mediaTypeImage              = "image"
	mediaTypeVideo              = "video"
	uploadStatusCreated         = "created"
	uploadStatusUploading       = "uploading"
	uploadStatusUploaded        = "uploaded"
	uploadStatusProcessing      = "processing"
	uploadStatusCompleted       = "completed"
	uploadStatusFailed          = "failed"
	uploadStatusExpired         = "expired"
	createStatusAlreadyUploaded = "already_uploaded"
	createStatusDuplicateFound  = "duplicate_found"
	createStatusExistingSession = "existing_session"
	createStatusCreated         = "created"
	resumeStatusResumed         = "resumed"
)

type UploadService struct {
	pool           *pgxpool.Pool
	queries        *sqlc.Queries
	storageService *storage.StorageService
	bucket         string
	uploadExpires  time.Duration
}

type CreateUploadSessionParams struct {
	UserID                 string
	DeviceID               string
	LocalAssetID           string
	MediaType              string
	MimeType               string
	OriginalFilename       string
	FileSizeBytes          int64
	ExpectedChecksumSha256 *string
}

type CompleteUploadParams struct {
	UserID                string
	UploadSessionID       string
	ChecksumSha256        *string
	TakenAt               *time.Time
	TakenAtSource         *string
	TimezoneOffsetMinutes *int32
	Width                 *int32
	Height                *int32
	Orientation           *int32
	DurationMs            *int64
	Latitude              *float64
	Longitude             *float64
	CameraMake            *string
	CameraModel           *string
	Software              *string
	LocalCreatedAt        *time.Time
	LocalModifiedAt       *time.Time
}

type UpdateUploadStatusParams struct {
	UserID          string
	UploadSessionID string
	Status          string
	ErrorMessage    *string
}

type objectKeys struct {
	Original    string
	Thumbnail   string
	Preview     string
	PosterFrame *string
}

func NewUploadService(pool *pgxpool.Pool, queries *sqlc.Queries, storageService *storage.StorageService, cfg config.Config) *UploadService {
	return &UploadService{
		pool:           pool,
		queries:        queries,
		storageService: storageService,
		bucket:         cfg.R2Bucket,
		uploadExpires:  time.Duration(cfg.R2PresignedUploadExpiresSeconds) * time.Second,
	}
}

func (service *UploadService) CreateSession(ctx context.Context, params CreateUploadSessionParams) (model.UploadSessionResponse, error) {
	params.LocalAssetID = strings.TrimSpace(params.LocalAssetID)
	params.ExpectedChecksumSha256 = normalizeChecksum(params.ExpectedChecksumSha256)

	tx, err := service.pool.Begin(ctx)
	if err != nil {
		return model.UploadSessionResponse{}, err
	}
	defer tx.Rollback(ctx)

	queries := service.queries.WithTx(tx)
	_, err = queries.GetDeviceByIDAndUserID(ctx, sqlc.GetDeviceByIDAndUserIDParams{
		ID:     params.DeviceID,
		UserID: params.UserID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return model.UploadSessionResponse{}, ErrDeviceNotFound
		}
		return model.UploadSessionResponse{}, err
	}

	lockKey := strings.Join([]string{params.UserID, params.DeviceID, params.LocalAssetID}, ":")
	if _, err := tx.Exec(ctx, "SELECT pg_advisory_xact_lock(hashtextextended($1, 0))", lockKey); err != nil {
		return model.UploadSessionResponse{}, err
	}

	assetID, err := queries.GetDeviceAssetByLocalID(ctx, sqlc.GetDeviceAssetByLocalIDParams{
		UserID:       params.UserID,
		DeviceID:     params.DeviceID,
		LocalAssetID: params.LocalAssetID,
	})
	if err == nil && assetID != nil {
		asset, err := queries.GetAssetByIDForUser(ctx, sqlc.GetAssetByIDForUserParams{
			ID:     *assetID,
			UserID: params.UserID,
		})
		if err != nil {
			return model.UploadSessionResponse{}, err
		}
		if err := tx.Commit(ctx); err != nil {
			return model.UploadSessionResponse{}, err
		}
		return uploadAssetResponse(createStatusAlreadyUploaded, asset), nil
	}
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return model.UploadSessionResponse{}, err
	}

	if params.ExpectedChecksumSha256 != nil {
		asset, err := queries.GetAssetByChecksum(ctx, sqlc.GetAssetByChecksumParams{
			UserID:         params.UserID,
			ChecksumSha256: *params.ExpectedChecksumSha256,
		})
		if err == nil {
			if err := queries.UpsertDeviceAssetToAsset(ctx, sqlc.UpsertDeviceAssetToAssetParams{
				UserID:       params.UserID,
				DeviceID:     params.DeviceID,
				AssetID:      asset.ID,
				LocalAssetID: params.LocalAssetID,
			}); err != nil {
				return model.UploadSessionResponse{}, err
			}
			if err := tx.Commit(ctx); err != nil {
				return model.UploadSessionResponse{}, err
			}
			return uploadAssetResponse(createStatusDuplicateFound, asset), nil
		}
		if !errors.Is(err, pgx.ErrNoRows) {
			return model.UploadSessionResponse{}, err
		}
	}

	existingSession, err := queries.GetActiveUploadSessionByLocalAsset(ctx, sqlc.GetActiveUploadSessionByLocalAssetParams{
		UserID:       params.UserID,
		DeviceID:     params.DeviceID,
		LocalAssetID: params.LocalAssetID,
	})
	if err == nil {
		if err := tx.Commit(ctx); err != nil {
			return model.UploadSessionResponse{}, err
		}
		return service.uploadSessionResponse(ctx, createStatusExistingSession, existingSession)
	}
	if !errors.Is(err, pgx.ErrNoRows) {
		return model.UploadSessionResponse{}, err
	}

	assetUUID, err := util.NewUUID()
	if err != nil {
		return model.UploadSessionResponse{}, err
	}

	now := time.Now().UTC()
	keys := buildObjectKeys(params.UserID, assetUUID, params.MediaType, params.MimeType, params.OriginalFilename, now)
	expiresAt := now.Add(service.uploadExpires)
	originalFilename := strings.TrimSpace(params.OriginalFilename)

	session, err := queries.CreateUploadSession(ctx, sqlc.CreateUploadSessionParams{
		UserID:                 params.UserID,
		DeviceID:               params.DeviceID,
		LocalAssetID:           params.LocalAssetID,
		ObjectKey:              keys.Original,
		ThumbnailKey:           keys.Thumbnail,
		PreviewKey:             keys.Preview,
		PosterFrameKey:         keys.PosterFrame,
		Bucket:                 service.bucket,
		MediaType:              params.MediaType,
		MimeType:               strings.TrimSpace(params.MimeType),
		OriginalFilename:       originalFilename,
		FileSizeBytes:          params.FileSizeBytes,
		ExpectedChecksumSha256: params.ExpectedChecksumSha256,
		ExpiresAt:              expiresAt,
	})
	if err != nil {
		return model.UploadSessionResponse{}, err
	}

	if err := tx.Commit(ctx); err != nil {
		return model.UploadSessionResponse{}, err
	}

	return service.uploadSessionResponse(ctx, createStatusCreated, session)
}

func (service *UploadService) Complete(ctx context.Context, params CompleteUploadParams) (model.CompleteUploadResponse, error) {
	tx, err := service.pool.Begin(ctx)
	if err != nil {
		return model.CompleteUploadResponse{}, err
	}
	defer tx.Rollback(ctx)

	queries := service.queries.WithTx(tx)
	session, err := queries.GetUploadSessionForUpdate(ctx, sqlc.GetUploadSessionForUpdateParams{
		ID:     params.UploadSessionID,
		UserID: params.UserID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return model.CompleteUploadResponse{}, ErrUploadNotFound
		}
		return model.CompleteUploadResponse{}, err
	}

	if session.Status == uploadStatusCompleted {
		asset, err := queries.GetAssetByUploadSessionID(ctx, sqlc.GetAssetByUploadSessionIDParams{
			ID:     session.ID,
			UserID: params.UserID,
		})
		if err != nil {
			if errors.Is(err, pgx.ErrNoRows) {
				return model.CompleteUploadResponse{}, ErrUploadNotFound
			}
			return model.CompleteUploadResponse{}, err
		}
		if err := tx.Commit(ctx); err != nil {
			return model.CompleteUploadResponse{}, err
		}
		return model.CompleteUploadResponse{AssetID: asset.ID, Status: uploadStatusCompleted}, nil
	}
	if !isCompletableUploadStatus(session.Status) {
		return model.CompleteUploadResponse{}, ErrUploadCompleted
	}
	checksum := normalizeChecksum(params.ChecksumSha256)
	if checksum == nil {
		return model.CompleteUploadResponse{}, ErrChecksumMismatch
	}
	if session.ExpectedChecksumSha256 != nil && params.ChecksumSha256 != nil && !strings.EqualFold(*session.ExpectedChecksumSha256, *params.ChecksumSha256) {
		return model.CompleteUploadResponse{}, ErrChecksumMismatch
	}
	if session.DeviceID == nil || session.LocalAssetID == nil {
		return model.CompleteUploadResponse{}, ErrUploadNotFound
	}

	existingAsset, err := queries.GetAssetByChecksum(ctx, sqlc.GetAssetByChecksumParams{
		UserID:         params.UserID,
		ChecksumSha256: *checksum,
	})
	if err == nil {
		return completeUploadWithAsset(ctx, tx, queries, session, existingAsset, params)
	}
	if !errors.Is(err, pgx.ErrNoRows) {
		return model.CompleteUploadResponse{}, err
	}

	objectInfo, err := service.storageService.HeadObject(ctx, session.ObjectKey)
	if err != nil {
		return model.CompleteUploadResponse{}, ErrObjectNotFound
	}
	if session.FileSizeBytes != nil && objectInfo.ContentLength > 0 && objectInfo.ContentLength != *session.FileSizeBytes {
		return model.CompleteUploadResponse{}, ErrObjectSizeMismatch
	}

	asset, err := queries.CreateAsset(ctx, sqlc.CreateAssetParams{
		UserID:                session.UserID,
		Bucket:                session.Bucket,
		ObjectKey:             session.ObjectKey,
		ThumbnailKey:          session.ThumbnailKey,
		PreviewKey:            session.PreviewKey,
		PosterFrameKey:        session.PosterFrameKey,
		MediaType:             session.MediaType,
		MimeType:              session.MimeType,
		OriginalFilename:      session.OriginalFilename,
		FileSizeBytes:         int64Value(session.FileSizeBytes),
		ChecksumSha256:        *checksum,
		TakenAt:               params.TakenAt,
		TakenAtSource:         normalizeOptionalString(params.TakenAtSource),
		TimezoneOffsetMinutes: int32ToInt16(params.TimezoneOffsetMinutes),
		Width:                 params.Width,
		Height:                params.Height,
		Orientation:           int32ToInt16(params.Orientation),
		DurationMs:            params.DurationMs,
		Latitude:              params.Latitude,
		Longitude:             params.Longitude,
		CameraMake:            normalizeOptionalString(params.CameraMake),
		CameraModel:           normalizeOptionalString(params.CameraModel),
		Software:              normalizeOptionalString(params.Software),
	})
	if err != nil {
		return model.CompleteUploadResponse{}, err
	}

	return completeUploadWithAsset(ctx, tx, queries, session, asset, params)
}

func (service *UploadService) Resume(ctx context.Context, userID string, uploadSessionID string) (model.UploadSessionResponse, error) {
	tx, err := service.pool.Begin(ctx)
	if err != nil {
		return model.UploadSessionResponse{}, err
	}
	defer tx.Rollback(ctx)

	queries := service.queries.WithTx(tx)
	session, err := queries.GetUploadSessionForUpdate(ctx, sqlc.GetUploadSessionForUpdateParams{
		ID:     uploadSessionID,
		UserID: userID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return model.UploadSessionResponse{}, ErrUploadNotFound
		}
		return model.UploadSessionResponse{}, err
	}

	if session.Status == uploadStatusCompleted {
		asset, err := queries.GetAssetByUploadSessionID(ctx, sqlc.GetAssetByUploadSessionIDParams{
			ID:     session.ID,
			UserID: userID,
		})
		if err != nil {
			if errors.Is(err, pgx.ErrNoRows) {
				return model.UploadSessionResponse{}, ErrUploadNotFound
			}
			return model.UploadSessionResponse{}, err
		}
		if err := tx.Commit(ctx); err != nil {
			return model.UploadSessionResponse{}, err
		}
		return uploadAssetResponse(uploadStatusCompleted, asset), nil
	}

	newExpiresAt := session.ExpiresAt
	if !session.ExpiresAt.After(time.Now().UTC()) {
		newExpiresAt = time.Now().UTC().Add(service.uploadExpires)
	}
	if session.Status == uploadStatusFailed || session.Status == uploadStatusExpired || !newExpiresAt.Equal(session.ExpiresAt) {
		session, err = queries.ResumeUploadSession(ctx, sqlc.ResumeUploadSessionParams{
			ID:        session.ID,
			UserID:    userID,
			ExpiresAt: newExpiresAt,
		})
		if err != nil {
			return model.UploadSessionResponse{}, err
		}
	}

	if err := tx.Commit(ctx); err != nil {
		return model.UploadSessionResponse{}, err
	}
	return service.uploadSessionResponseWithDuration(ctx, resumeStatusResumed, session, service.uploadExpires)
}

func (service *UploadService) UpdateStatus(ctx context.Context, params UpdateUploadStatusParams) (model.UploadSessionStatusResponse, error) {
	tx, err := service.pool.Begin(ctx)
	if err != nil {
		return model.UploadSessionStatusResponse{}, err
	}
	defer tx.Rollback(ctx)

	queries := service.queries.WithTx(tx)
	session, err := queries.GetUploadSessionForUpdate(ctx, sqlc.GetUploadSessionForUpdateParams{
		ID:     params.UploadSessionID,
		UserID: params.UserID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return model.UploadSessionStatusResponse{}, ErrUploadNotFound
		}
		return model.UploadSessionStatusResponse{}, err
	}

	if session.Status != params.Status && !isAllowedUploadStatusTransition(session.Status, params.Status) {
		return model.UploadSessionStatusResponse{}, ErrInvalidUploadTransition
	}
	if session.Status != params.Status {
		session, err = queries.UpdateUploadSessionStatus(ctx, sqlc.UpdateUploadSessionStatusParams{
			ID:           session.ID,
			UserID:       params.UserID,
			Status:       params.Status,
			ErrorMessage: normalizeOptionalString(params.ErrorMessage),
		})
		if err != nil {
			return model.UploadSessionStatusResponse{}, err
		}
	}

	if err := tx.Commit(ctx); err != nil {
		return model.UploadSessionStatusResponse{}, err
	}
	return model.UploadSessionStatusResponse{
		Status:       session.Status,
		ErrorMessage: session.ErrorMessage,
	}, nil
}

func completeUploadWithAsset(
	ctx context.Context,
	tx pgx.Tx,
	queries *sqlc.Queries,
	session sqlc.UploadSession,
	asset sqlc.Asset,
	params CompleteUploadParams,
) (model.CompleteUploadResponse, error) {
	if err := queries.UpsertDeviceAssetToAsset(ctx, sqlc.UpsertDeviceAssetToAssetParams{
		UserID:          session.UserID,
		DeviceID:        *session.DeviceID,
		AssetID:         asset.ID,
		LocalAssetID:    *session.LocalAssetID,
		LocalCreatedAt:  params.LocalCreatedAt,
		LocalModifiedAt: params.LocalModifiedAt,
	}); err != nil {
		return model.CompleteUploadResponse{}, err
	}

	if _, err := queries.MarkUploadSessionCompleted(ctx, sqlc.MarkUploadSessionCompletedParams{
		ID:      session.ID,
		AssetID: asset.ID,
		UserID:  session.UserID,
	}); err != nil {
		return model.CompleteUploadResponse{}, err
	}

	if err := tx.Commit(ctx); err != nil {
		return model.CompleteUploadResponse{}, err
	}

	return model.CompleteUploadResponse{AssetID: asset.ID, Status: uploadStatusCompleted}, nil
}

func uploadAssetResponse(status string, asset sqlc.Asset) model.UploadSessionResponse {
	detail := mapAssetDetail(asset)
	return model.UploadSessionResponse{Status: status, Asset: &detail}
}

func (service *UploadService) uploadSessionResponse(
	ctx context.Context,
	status string,
	session sqlc.UploadSession,
) (model.UploadSessionResponse, error) {
	expires := time.Until(session.ExpiresAt)
	if expires <= 0 {
		return model.UploadSessionResponse{}, ErrUploadExpired
	}
	if expires > service.uploadExpires {
		expires = service.uploadExpires
	}
	return service.uploadSessionResponseWithDuration(ctx, status, session, expires)
}

func (service *UploadService) uploadSessionResponseWithDuration(
	ctx context.Context,
	status string,
	session sqlc.UploadSession,
	expires time.Duration,
) (model.UploadSessionResponse, error) {
	keys := objectKeys{
		Original:    session.ObjectKey,
		Thumbnail:   stringValue(session.ThumbnailKey),
		Preview:     stringValue(session.PreviewKey),
		PosterFrame: session.PosterFrameKey,
	}
	uploadURLs, err := service.generateUploadURLs(ctx, keys, session.MimeType, expires)
	if err != nil {
		return model.UploadSessionResponse{}, err
	}

	details := model.UploadSessionDetails{
		ID:             session.ID,
		Status:         session.Status,
		Bucket:         session.Bucket,
		ObjectKey:      session.ObjectKey,
		ThumbnailKey:   stringValue(session.ThumbnailKey),
		PreviewKey:     stringValue(session.PreviewKey),
		PosterFrameKey: session.PosterFrameKey,
		ExpiresAt:      session.ExpiresAt,
	}
	return model.UploadSessionResponse{
		Status:     status,
		Session:    &details,
		UploadURLs: &uploadURLs,
	}, nil
}

func (service *UploadService) generateUploadURLs(
	ctx context.Context,
	keys objectKeys,
	originalMimeType string,
	expires time.Duration,
) (model.UploadURLs, error) {
	if expires <= 0 {
		return model.UploadURLs{}, ErrUploadExpired
	}

	originalURL, err := service.storageService.GeneratePresignedPutURL(ctx, keys.Original, originalMimeType, expires)
	if err != nil {
		return model.UploadURLs{}, err
	}

	thumbnailURL, err := service.storageService.GeneratePresignedPutURL(ctx, keys.Thumbnail, "image/webp", expires)
	if err != nil {
		return model.UploadURLs{}, err
	}

	previewURL, err := service.storageService.GeneratePresignedPutURL(ctx, keys.Preview, "image/webp", expires)
	if err != nil {
		return model.UploadURLs{}, err
	}

	var posterFrameURL *string
	if keys.PosterFrame != nil {
		url, err := service.storageService.GeneratePresignedPutURL(ctx, *keys.PosterFrame, "image/webp", expires)
		if err != nil {
			return model.UploadURLs{}, err
		}
		posterFrameURL = &url
	}

	return model.UploadURLs{
		Original:    originalURL,
		Thumbnail:   thumbnailURL,
		Preview:     previewURL,
		PosterFrame: posterFrameURL,
	}, nil
}

func isCompletableUploadStatus(status string) bool {
	switch status {
	case uploadStatusCreated, uploadStatusUploading, uploadStatusUploaded, uploadStatusProcessing:
		return true
	default:
		return false
	}
}

func isAllowedUploadStatusTransition(current string, next string) bool {
	if next == uploadStatusFailed {
		return current == uploadStatusCreated || current == uploadStatusUploading || current == uploadStatusUploaded
	}
	return (current == uploadStatusCreated && next == uploadStatusUploading) ||
		(current == uploadStatusUploading && next == uploadStatusUploaded)
}

func buildObjectKeys(userID string, assetUUID string, mediaType string, mimeType string, originalFilename string, now time.Time) objectKeys {
	year := now.Format("2006")
	month := now.Format("01")
	ext := extensionForObject(mimeType, originalFilename)
	basePath := fmt.Sprintf("users/%s", userID)
	datedPath := fmt.Sprintf("%s/%s/%s", year, month, assetUUID)

	keys := objectKeys{
		Original:  fmt.Sprintf("%s/originals/%s.%s", basePath, datedPath, ext),
		Thumbnail: fmt.Sprintf("%s/thumbs/%s_320.webp", basePath, datedPath),
		Preview:   fmt.Sprintf("%s/previews/%s_1600.webp", basePath, datedPath),
	}

	if mediaType == mediaTypeVideo {
		posterFrame := fmt.Sprintf("%s/posters/%s_poster.webp", basePath, datedPath)
		keys.PosterFrame = &posterFrame
	}

	return keys
}

func extensionForObject(mimeType string, originalFilename string) string {
	ext := strings.TrimPrefix(strings.ToLower(filepath.Ext(originalFilename)), ".")
	if ext != "" {
		return ext
	}

	switch strings.ToLower(strings.TrimSpace(mimeType)) {
	case "image/jpeg":
		return "jpg"
	case "image/png":
		return "png"
	case "image/webp":
		return "webp"
	case "video/quicktime":
		return "mov"
	case "video/mp4":
		return "mp4"
	default:
		return "bin"
	}
}

func normalizeOptionalString(value *string) *string {
	if value == nil {
		return nil
	}

	trimmed := strings.TrimSpace(*value)
	if trimmed == "" {
		return nil
	}

	return &trimmed
}

func normalizeChecksum(value *string) *string {
	normalized := normalizeOptionalString(value)
	if normalized == nil {
		return nil
	}
	lowercase := strings.ToLower(*normalized)
	return &lowercase
}

func stringValue(value *string) string {
	if value == nil {
		return ""
	}
	return *value
}

func int64Value(value *int64) int64 {
	if value == nil {
		return 0
	}
	return *value
}

func int32ToInt16(value *int32) *int16 {
	if value == nil {
		return nil
	}
	converted := int16(*value)
	return &converted
}
