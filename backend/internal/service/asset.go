package service

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"photo-map-app/backend/internal/config"
	"photo-map-app/backend/internal/db/sqlc"
	"photo-map-app/backend/internal/model"
	"photo-map-app/backend/internal/storage"
)

const (
	defaultAssetLimit = 50
	maxAssetLimit     = 100

	variantOriginal    = "original"
	variantThumbnail   = "thumbnail"
	variantPreview     = "preview"
	variantPosterFrame = "posterFrame"
)

type AssetService struct {
	pool           *pgxpool.Pool
	queries        *sqlc.Queries
	storageService *storage.StorageService
	readExpires    time.Duration
}

type ListAssetsParams struct {
	UserID    string
	Limit     int
	Cursor    string
	MediaType *string
	Favorite  *bool
	Archived  *bool
	Trashed   bool
	City      *string
	From      *time.Time
	To        *time.Time
}

type assetCursor struct {
	TakenAt *time.Time `json:"takenAt"`
	ID      string     `json:"id"`
}

type SearchAssetsParams struct {
	UserID string
	Query  string
	Limit  int
	Cursor string
}

type ReplaceAssetMetadataParams struct {
	UserID                string
	AssetID               string
	TakenAt               *time.Time
	TakenAtSource         *string
	TimezoneOffsetMinutes *int16
	Orientation           *int16
	Latitude              *float64
	Longitude             *float64
	CameraMake            *string
	CameraModel           *string
	Software              *string
}

type searchCursor struct {
	Offset int `json:"offset"`
}

func NewAssetService(
	pool *pgxpool.Pool,
	queries *sqlc.Queries,
	storageService *storage.StorageService,
	cfg config.Config,
) *AssetService {
	return &AssetService{
		pool:           pool,
		queries:        queries,
		storageService: storageService,
		readExpires:    time.Duration(cfg.R2PresignedReadExpiresSeconds) * time.Second,
	}
}

func (service *AssetService) List(ctx context.Context, params ListAssetsParams) (model.AssetListResponse, error) {
	limit := normalizeLimit(params.Limit)
	cursor, err := decodeAssetCursor(params.Cursor)
	if err != nil {
		return model.AssetListResponse{}, err
	}

	assets, err := service.queries.ListAssetsFiltered(ctx, sqlc.ListAssetsFilteredParams{
		UserID:        params.UserID,
		MediaType:     params.MediaType,
		Favorite:      params.Favorite,
		Archived:      params.Archived,
		Trashed:       params.Trashed,
		City:          params.City,
		From:          params.From,
		To:            params.To,
		CursorID:      cursorID(cursor),
		CursorTakenAt: cursorTakenAt(cursor),
		Limit:         int32(limit + 1),
	})
	if err != nil {
		return model.AssetListResponse{}, err
	}

	hasMore := len(assets) > limit
	if hasMore {
		assets = assets[:limit]
	}

	items := make([]model.AssetListItem, 0, len(assets))
	for _, asset := range assets {
		item, err := service.mapListItem(ctx, asset)
		if err != nil {
			return model.AssetListResponse{}, err
		}
		items = append(items, item)
	}

	var nextCursor *string
	if hasMore && len(assets) > 0 {
		cursorValue, err := encodeAssetCursor(assets[len(assets)-1])
		if err != nil {
			return model.AssetListResponse{}, err
		}
		nextCursor = &cursorValue
	}

	return model.AssetListResponse{Items: items, NextCursor: nextCursor}, nil
}

func (service *AssetService) Get(ctx context.Context, userID string, assetID string) (model.AssetDetail, error) {
	asset, err := service.getAsset(ctx, userID, assetID)
	if err != nil {
		return model.AssetDetail{}, err
	}
	return mapAssetDetail(asset), nil
}

func (service *AssetService) Search(ctx context.Context, params SearchAssetsParams) (model.AssetListResponse, error) {
	limit := normalizeLimit(params.Limit)
	cursor, err := decodeSearchCursor(params.Cursor)
	if err != nil {
		return model.AssetListResponse{}, err
	}

	assets, err := service.queries.SearchAssets(ctx, sqlc.SearchAssetsParams{
		UserID: params.UserID,
		Query:  params.Query,
		Limit:  int32(limit + 1),
		Offset: int32(cursor.Offset),
	})
	if err != nil {
		return model.AssetListResponse{}, err
	}

	hasMore := len(assets) > limit
	if hasMore {
		assets = assets[:limit]
	}

	items := make([]model.AssetListItem, 0, len(assets))
	for _, asset := range assets {
		item, err := service.mapListItem(ctx, asset)
		if err != nil {
			return model.AssetListResponse{}, err
		}
		items = append(items, item)
	}

	var nextCursor *string
	if hasMore {
		cursorValue, err := encodeSearchCursor(searchCursor{Offset: cursor.Offset + len(assets)})
		if err != nil {
			return model.AssetListResponse{}, err
		}
		nextCursor = &cursorValue
	}

	return model.AssetListResponse{Items: items, NextCursor: nextCursor}, nil
}

func (service *AssetService) ReadURL(ctx context.Context, userID string, assetID string, variant string) (model.ReadURLResponse, error) {
	asset, err := service.getAsset(ctx, userID, assetID)
	if err != nil {
		return model.ReadURLResponse{}, err
	}

	key, err := assetVariantKey(asset, variant)
	if err != nil {
		return model.ReadURLResponse{}, err
	}

	url, err := service.storageService.GeneratePresignedGetURL(ctx, key, service.readExpires)
	if err != nil {
		return model.ReadURLResponse{}, err
	}

	return model.ReadURLResponse{URL: url}, nil
}

func (service *AssetService) UpdateFavorite(ctx context.Context, userID string, assetID string, isFavorite bool) (model.AssetDetail, error) {
	return service.mutateAssetWithChange(ctx, assetChangeUpsert, func(queries *sqlc.Queries) (sqlc.Asset, error) {
		return queries.UpdateAssetFavorite(ctx, sqlc.UpdateAssetFavoriteParams{
			ID: assetID, UserID: userID, IsFavorite: isFavorite,
		})
	})
}

func (service *AssetService) UpdateArchive(ctx context.Context, userID string, assetID string, isArchived bool) (model.AssetDetail, error) {
	return service.mutateAssetWithChange(ctx, assetChangeUpsert, func(queries *sqlc.Queries) (sqlc.Asset, error) {
		return queries.UpdateAssetArchive(ctx, sqlc.UpdateAssetArchiveParams{
			ID: assetID, UserID: userID, IsArchived: isArchived,
		})
	})
}

func (service *AssetService) ReplaceMetadata(ctx context.Context, params ReplaceAssetMetadataParams) (model.AssetDetail, error) {
	return service.mutateAssetWithChange(ctx, assetChangeUpsert, func(queries *sqlc.Queries) (sqlc.Asset, error) {
		return queries.ReplaceAssetMetadata(ctx, sqlc.ReplaceAssetMetadataParams{
			ID: params.AssetID, UserID: params.UserID, TakenAt: params.TakenAt,
			TakenAtSource: params.TakenAtSource, TimezoneOffsetMinutes: params.TimezoneOffsetMinutes,
			Orientation: params.Orientation, Latitude: params.Latitude, Longitude: params.Longitude,
			CameraMake: params.CameraMake, CameraModel: params.CameraModel, Software: params.Software,
		})
	})
}

func (service *AssetService) Trash(ctx context.Context, userID string, assetID string) (model.AssetDetail, error) {
	return service.mutateAssetWithChange(ctx, assetChangeTrash, func(queries *sqlc.Queries) (sqlc.Asset, error) {
		return queries.MoveAssetToTrash(ctx, sqlc.GetAssetByIDForUserParams{ID: assetID, UserID: userID})
	})
}

func (service *AssetService) Restore(ctx context.Context, userID string, assetID string) (model.AssetDetail, error) {
	return service.mutateAssetWithChange(ctx, assetChangeRestore, func(queries *sqlc.Queries) (sqlc.Asset, error) {
		return queries.RestoreAssetFromTrash(ctx, sqlc.GetAssetByIDForUserParams{ID: assetID, UserID: userID})
	})
}

func (service *AssetService) Delete(ctx context.Context, userID string, assetID string) error {
	asset, err := service.getAsset(ctx, userID, assetID)
	if err != nil {
		return err
	}

	keys := assetObjectKeys(asset)
	if err := service.storageService.DeleteObjects(ctx, keys); err != nil {
		return err
	}

	tx, err := service.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	queries := service.queries.WithTx(tx)
	deletedAssetID, err := queries.DeleteAssetByID(ctx, sqlc.GetAssetByIDForUserParams{
		ID: assetID, UserID: userID,
	})
	if err != nil {
		return mapNoRows(err)
	}
	if _, err := insertAssetDeleteChange(ctx, queries, userID, deletedAssetID); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

func (service *AssetService) mutateAssetWithChange(
	ctx context.Context,
	changeType string,
	mutate func(*sqlc.Queries) (sqlc.Asset, error),
) (model.AssetDetail, error) {
	tx, err := service.pool.Begin(ctx)
	if err != nil {
		return model.AssetDetail{}, err
	}
	defer tx.Rollback(ctx)

	queries := service.queries.WithTx(tx)
	asset, err := mutate(queries)
	if err != nil {
		return model.AssetDetail{}, mapNoRows(err)
	}
	if _, err := insertAssetChange(ctx, queries, asset, changeType); err != nil {
		return model.AssetDetail{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return model.AssetDetail{}, err
	}
	return mapAssetDetail(asset), nil
}

func (service *AssetService) getAsset(ctx context.Context, userID string, assetID string) (sqlc.Asset, error) {
	asset, err := service.queries.GetAssetByIDForUser(ctx, sqlc.GetAssetByIDForUserParams{ID: assetID, UserID: userID})
	if err != nil {
		return sqlc.Asset{}, mapNoRows(err)
	}
	return asset, nil
}

func (service *AssetService) mapListItem(ctx context.Context, asset sqlc.Asset) (model.AssetListItem, error) {
	var thumbnailURL *string
	if asset.ThumbnailKey != nil {
		url, err := service.storageService.GeneratePresignedGetURL(ctx, *asset.ThumbnailKey, service.readExpires)
		if err != nil {
			return model.AssetListItem{}, err
		}
		thumbnailURL = &url
	}

	var previewURL *string
	if asset.PreviewKey != nil {
		url, err := service.storageService.GeneratePresignedGetURL(ctx, *asset.PreviewKey, service.readExpires)
		if err != nil {
			return model.AssetListItem{}, err
		}
		previewURL = &url
	}

	return model.AssetListItem{
		ID:           asset.ID,
		MediaType:    asset.MediaType,
		MimeType:     asset.MimeType,
		ThumbnailKey: asset.ThumbnailKey,
		PreviewKey:   asset.PreviewKey,
		ThumbnailURL: thumbnailURL,
		PreviewURL:   previewURL,
		TakenAt:      asset.TakenAt,
		Width:        asset.Width,
		Height:       asset.Height,
		DurationMs:   asset.DurationMs,
		IsFavorite:   boolValue(asset.IsFavorite),
	}, nil
}

func mapAssetDetail(asset sqlc.Asset) model.AssetDetail {
	return model.AssetDetail{
		ID:                    asset.ID,
		MediaType:             asset.MediaType,
		MimeType:              asset.MimeType,
		ObjectKey:             asset.ObjectKey,
		ThumbnailKey:          asset.ThumbnailKey,
		PreviewKey:            asset.PreviewKey,
		PosterFrameKey:        asset.PosterFrameKey,
		OriginalName:          asset.OriginalFilename,
		FileSizeBytes:         asset.FileSizeBytes,
		ChecksumSha256:        asset.ChecksumSha256,
		TakenAt:               asset.TakenAt,
		TakenAtSource:         asset.TakenAtSource,
		TimezoneOffsetMinutes: asset.TimezoneOffsetMinutes,
		Width:                 asset.Width,
		Height:                asset.Height,
		Orientation:           asset.Orientation,
		DurationMs:            asset.DurationMs,
		Latitude:              asset.Latitude,
		Longitude:             asset.Longitude,
		Country:               asset.Country,
		Region:                asset.Region,
		City:                  asset.City,
		PlaceName:             asset.PlaceName,
		CameraMake:            asset.CameraMake,
		CameraModel:           asset.CameraModel,
		Software:              asset.Software,
		IsFavorite:            boolValue(asset.IsFavorite),
		IsArchived:            boolValue(asset.IsArchived),
		IsHidden:              boolValue(asset.IsHidden),
		IsTrashed:             boolValue(asset.IsTrashed),
		TrashedAt:             asset.TrashedAt,
		UploadedAt:            asset.UploadedAt,
		CreatedAt:             asset.CreatedAt,
		UpdatedAt:             asset.UpdatedAt,
	}
}

func assetVariantKey(asset sqlc.Asset, variant string) (string, error) {
	switch variant {
	case variantOriginal:
		return asset.ObjectKey, nil
	case variantThumbnail:
		return optionalKey(asset.ThumbnailKey)
	case variantPreview:
		return optionalKey(asset.PreviewKey)
	case variantPosterFrame:
		return optionalKey(asset.PosterFrameKey)
	default:
		return "", ErrInvalidVariant
	}
}

func optionalKey(key *string) (string, error) {
	if key == nil || *key == "" {
		return "", ErrInvalidVariant
	}
	return *key, nil
}

func assetObjectKeys(asset sqlc.Asset) []string {
	keys := []string{asset.ObjectKey}
	for _, key := range []*string{asset.ThumbnailKey, asset.PreviewKey, asset.PosterFrameKey} {
		if key != nil && *key != "" {
			keys = append(keys, *key)
		}
	}
	return keys
}

func normalizeLimit(limit int) int {
	if limit <= 0 {
		return defaultAssetLimit
	}
	if limit > maxAssetLimit {
		return maxAssetLimit
	}
	return limit
}

func decodeAssetCursor(value string) (*assetCursor, error) {
	if value == "" {
		return nil, nil
	}

	bytes, err := base64.RawURLEncoding.DecodeString(value)
	if err != nil {
		return nil, ErrInvalidCursor
	}

	var cursor assetCursor
	if err := json.Unmarshal(bytes, &cursor); err != nil {
		return nil, ErrInvalidCursor
	}
	if cursor.ID == "" {
		return nil, ErrInvalidCursor
	}

	return &cursor, nil
}

func decodeSearchCursor(value string) (searchCursor, error) {
	if value == "" {
		return searchCursor{}, nil
	}

	bytes, err := base64.RawURLEncoding.DecodeString(value)
	if err != nil {
		return searchCursor{}, ErrInvalidCursor
	}

	var cursor searchCursor
	if err := json.Unmarshal(bytes, &cursor); err != nil {
		return searchCursor{}, ErrInvalidCursor
	}
	if cursor.Offset < 0 {
		return searchCursor{}, ErrInvalidCursor
	}

	return cursor, nil
}

func encodeSearchCursor(cursor searchCursor) (string, error) {
	bytes, err := json.Marshal(cursor)
	if err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(bytes), nil
}

func encodeAssetCursor(asset sqlc.Asset) (string, error) {
	bytes, err := json.Marshal(assetCursor{TakenAt: asset.TakenAt, ID: asset.ID})
	if err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(bytes), nil
}

func cursorID(cursor *assetCursor) *string {
	if cursor == nil {
		return nil
	}
	return &cursor.ID
}

func cursorTakenAt(cursor *assetCursor) *time.Time {
	if cursor == nil {
		return nil
	}
	return cursor.TakenAt
}

func boolValue(value *bool) bool {
	return value != nil && *value
}

func mapNoRows(err error) error {
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrAssetNotFound
	}
	return err
}
