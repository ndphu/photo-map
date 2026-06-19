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
	mediaTypeImage      = "image"
	mediaTypeVideo      = "video"
	uploadStatusPending = "created"
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
	_, err := service.queries.GetDeviceByIDAndUserID(ctx, sqlc.GetDeviceByIDAndUserIDParams{
		ID:     params.DeviceID,
		UserID: params.UserID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return model.UploadSessionResponse{}, ErrDeviceNotFound
		}
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

	session, err := service.queries.CreateUploadSession(ctx, sqlc.CreateUploadSessionParams{
		UserID:                 params.UserID,
		DeviceID:               params.DeviceID,
		LocalAssetID:           strings.TrimSpace(params.LocalAssetID),
		ObjectKey:              keys.Original,
		ThumbnailKey:           keys.Thumbnail,
		PreviewKey:             keys.Preview,
		PosterFrameKey:         keys.PosterFrame,
		Bucket:                 service.bucket,
		MediaType:              params.MediaType,
		MimeType:               strings.TrimSpace(params.MimeType),
		OriginalFilename:       originalFilename,
		FileSizeBytes:          params.FileSizeBytes,
		ExpectedChecksumSha256: normalizeOptionalString(params.ExpectedChecksumSha256),
		ExpiresAt:              expiresAt,
	})
	if err != nil {
		return model.UploadSessionResponse{}, err
	}

	uploadURLs, err := service.generateUploadURLs(ctx, keys, params.MimeType)
	if err != nil {
		return model.UploadSessionResponse{}, err
	}

	return model.UploadSessionResponse{
		ID:             session.ID,
		Bucket:         session.Bucket,
		ObjectKey:      session.ObjectKey,
		ThumbnailKey:   stringValue(session.ThumbnailKey),
		PreviewKey:     stringValue(session.PreviewKey),
		PosterFrameKey: session.PosterFrameKey,
		UploadURLs:     uploadURLs,
		ExpiresAt:      session.ExpiresAt,
	}, nil
}

func (service *UploadService) Complete(ctx context.Context, params CompleteUploadParams) (model.CompleteUploadResponse, error) {
	tx, err := service.pool.Begin(ctx)
	if err != nil {
		return model.CompleteUploadResponse{}, err
	}
	defer tx.Rollback(ctx)

	queries := service.queries.WithTx(tx)
	session, err := queries.GetUploadSessionForUpdate(ctx, params.UploadSessionID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return model.CompleteUploadResponse{}, ErrUploadNotFound
		}
		return model.CompleteUploadResponse{}, err
	}

	if session.UserID != params.UserID {
		return model.CompleteUploadResponse{}, ErrUploadForbidden
	}
	if session.Status != uploadStatusPending {
		return model.CompleteUploadResponse{}, ErrUploadCompleted
	}
	if time.Now().UTC().After(session.ExpiresAt) {
		return model.CompleteUploadResponse{}, ErrUploadExpired
	}
	if session.ExpectedChecksumSha256 != nil && params.ChecksumSha256 != nil && !strings.EqualFold(*session.ExpectedChecksumSha256, *params.ChecksumSha256) {
		return model.CompleteUploadResponse{}, ErrChecksumMismatch
	}

	objectInfo, err := service.storageService.HeadObject(ctx, session.ObjectKey)
	if err != nil {
		return model.CompleteUploadResponse{}, ErrObjectNotFound
	}
	if session.FileSizeBytes != nil && objectInfo.ContentLength > 0 && objectInfo.ContentLength != *session.FileSizeBytes {
		return model.CompleteUploadResponse{}, ErrObjectSizeMismatch
	}
	checksum := normalizeOptionalString(params.ChecksumSha256)
	if checksum == nil {
		return model.CompleteUploadResponse{}, ErrChecksumMismatch
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

	if session.DeviceID == nil || session.LocalAssetID == nil {
		return model.CompleteUploadResponse{}, ErrUploadNotFound
	}

	if err := queries.CreateOrUpdateDeviceAsset(ctx, sqlc.CreateOrUpdateDeviceAssetParams{
		UserID:          session.UserID,
		DeviceID:        *session.DeviceID,
		AssetID:         &asset.ID,
		LocalAssetID:    *session.LocalAssetID,
		SyncStatus:      "synced",
		LocalCreatedAt:  params.LocalCreatedAt,
		LocalModifiedAt: params.LocalModifiedAt,
	}); err != nil {
		return model.CompleteUploadResponse{}, err
	}

	if _, err := queries.CompleteUploadSession(ctx, sqlc.CompleteUploadSessionParams{ID: session.ID, AssetID: asset.ID}); err != nil {
		return model.CompleteUploadResponse{}, err
	}

	if err := tx.Commit(ctx); err != nil {
		return model.CompleteUploadResponse{}, err
	}

	return model.CompleteUploadResponse{AssetID: asset.ID, Status: "completed"}, nil
}

func (service *UploadService) generateUploadURLs(ctx context.Context, keys objectKeys, originalMimeType string) (model.UploadURLs, error) {
	originalURL, err := service.storageService.GeneratePresignedPutURL(ctx, keys.Original, originalMimeType, service.uploadExpires)
	if err != nil {
		return model.UploadURLs{}, err
	}

	thumbnailURL, err := service.storageService.GeneratePresignedPutURL(ctx, keys.Thumbnail, "image/webp", service.uploadExpires)
	if err != nil {
		return model.UploadURLs{}, err
	}

	previewURL, err := service.storageService.GeneratePresignedPutURL(ctx, keys.Preview, "image/webp", service.uploadExpires)
	if err != nil {
		return model.UploadURLs{}, err
	}

	var posterFrameURL *string
	if keys.PosterFrame != nil {
		url, err := service.storageService.GeneratePresignedPutURL(ctx, *keys.PosterFrame, "image/webp", service.uploadExpires)
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
