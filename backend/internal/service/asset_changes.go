package service

import (
	"context"
	"encoding/json"
	"errors"
	"time"

	"photo-map-app/backend/internal/db/sqlc"
	"photo-map-app/backend/internal/model"
)

const (
	assetChangeUpsert  = "upsert"
	assetChangeTrash   = "trash"
	assetChangeRestore = "restore"
	assetChangeDelete  = "delete"

	defaultAssetChangesLimit = 500
	maxAssetChangesLimit     = 1000
)

type ListAssetChangesParams struct {
	UserID string
	Cursor int64
	Limit  int
}

func BuildAssetSnapshot(asset sqlc.Asset) model.AssetSnapshot {
	return model.AssetSnapshot{
		ID:                    asset.ID,
		MediaType:             asset.MediaType,
		MimeType:              asset.MimeType,
		OriginalFilename:      asset.OriginalFilename,
		FileSizeBytes:         asset.FileSizeBytes,
		ChecksumSha256:        asset.ChecksumSha256,
		ThumbnailKey:          asset.ThumbnailKey,
		PreviewKey:            asset.PreviewKey,
		PosterFrameKey:        asset.PosterFrameKey,
		TakenAt:               asset.TakenAt,
		TakenAtSource:         asset.TakenAtSource,
		TimezoneOffsetMinutes: asset.TimezoneOffsetMinutes,
		Width:                 asset.Width,
		Height:                asset.Height,
		DurationMs:            asset.DurationMs,
		Orientation:           asset.Orientation,
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
		IsTrashed:             boolValue(asset.IsTrashed),
		UploadedAt:            asset.UploadedAt,
		UpdatedAt:             asset.UpdatedAt,
	}
}

func marshalAssetSnapshot(asset sqlc.Asset) ([]byte, error) {
	return json.Marshal(BuildAssetSnapshot(asset))
}

func insertAssetChange(
	ctx context.Context,
	queries *sqlc.Queries,
	asset sqlc.Asset,
	changeType string,
) (sqlc.AssetChange, error) {
	snapshot, err := marshalAssetSnapshot(asset)
	if err != nil {
		return sqlc.AssetChange{}, err
	}
	return queries.InsertAssetChange(ctx, sqlc.InsertAssetChangeParams{
		UserID:        asset.UserID,
		AssetID:       asset.ID,
		ChangeType:    changeType,
		AssetSnapshot: snapshot,
	})
}

func insertAssetDeleteChange(
	ctx context.Context,
	queries *sqlc.Queries,
	userID string,
	assetID string,
) (sqlc.AssetChange, error) {
	return queries.InsertAssetChange(ctx, sqlc.InsertAssetChangeParams{
		UserID: userID, ChangeType: assetChangeDelete, AssetID: assetID, AssetSnapshot: nil,
	})
}

func (service *AssetService) ListChanges(
	ctx context.Context,
	params ListAssetChangesParams,
) (model.AssetChangesResponse, error) {
	if params.Cursor < 0 {
		return model.AssetChangesResponse{}, ErrInvalidCursor
	}
	limit := normalizeAssetChangesLimit(params.Limit)
	changes, err := service.queries.ListAssetChangesAfterCursor(ctx, sqlc.ListAssetChangesAfterCursorParams{
		UserID: params.UserID,
		Cursor: params.Cursor,
		Limit:  int32(limit),
	})
	if err != nil {
		return model.AssetChangesResponse{}, err
	}

	items := make([]model.AssetChangeItem, 0, len(changes))
	nextCursor := params.Cursor
	for _, change := range changes {
		item, err := service.mapAssetChange(ctx, change)
		if err != nil {
			return model.AssetChangesResponse{}, err
		}
		items = append(items, item)
		nextCursor = change.ChangeID
	}

	serverCursor, err := service.queries.GetLatestAssetChangeIDForUser(ctx, params.UserID)
	if err != nil {
		return model.AssetChangesResponse{}, err
	}

	return model.AssetChangesResponse{
		Items:        items,
		NextCursor:   nextCursor,
		HasMore:      nextCursor < serverCursor,
		ServerCursor: serverCursor,
		ServerTime:   time.Now().UTC(),
	}, nil
}

func (service *AssetService) mapAssetChange(
	ctx context.Context,
	change sqlc.AssetChange,
) (model.AssetChangeItem, error) {
	item := model.AssetChangeItem{
		ChangeID: change.ChangeID, AssetID: change.AssetID,
		ChangeType: change.ChangeType, ChangedAt: change.ChangedAt,
	}
	if change.ChangeType == assetChangeDelete {
		return item, nil
	}
	if len(change.AssetSnapshot) == 0 {
		return model.AssetChangeItem{}, errors.New("asset change snapshot is missing")
	}

	var snapshot model.AssetSnapshot
	if err := json.Unmarshal(change.AssetSnapshot, &snapshot); err != nil {
		return model.AssetChangeItem{}, err
	}
	asset := model.AssetChangeAsset{AssetSnapshot: snapshot}
	var err error
	asset.ThumbnailURL, err = service.presignOptionalKey(ctx, snapshot.ThumbnailKey)
	if err != nil {
		return model.AssetChangeItem{}, err
	}
	asset.PreviewURL, err = service.presignOptionalKey(ctx, snapshot.PreviewKey)
	if err != nil {
		return model.AssetChangeItem{}, err
	}
	asset.PosterFrameURL, err = service.presignOptionalKey(ctx, snapshot.PosterFrameKey)
	if err != nil {
		return model.AssetChangeItem{}, err
	}
	item.Asset = &asset
	return item, nil
}

func (service *AssetService) presignOptionalKey(ctx context.Context, key *string) (*string, error) {
	if key == nil || *key == "" {
		return nil, nil
	}
	url, err := service.storageService.GeneratePresignedGetURL(ctx, *key, service.readExpires)
	if err != nil {
		return nil, err
	}
	return &url, nil
}

func normalizeAssetChangesLimit(limit int) int {
	if limit <= 0 {
		return defaultAssetChangesLimit
	}
	if limit > maxAssetChangesLimit {
		return maxAssetChangesLimit
	}
	return limit
}
