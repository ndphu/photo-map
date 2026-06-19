package service

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"

	"photo-map-app/backend/internal/config"
	"photo-map-app/backend/internal/db/sqlc"
	"photo-map-app/backend/internal/model"
	"photo-map-app/backend/internal/storage"
)

type AlbumService struct {
	queries        *sqlc.Queries
	storageService *storage.StorageService
	readExpires    time.Duration
}

type CreateAlbumParams struct {
	UserID      string
	Name        string
	Description *string
}

type UpdateAlbumParams struct {
	UserID       string
	AlbumID      string
	Name         *string
	Description  *string
	CoverAssetID *string
	IsArchived   *bool
}

type AddAssetToAlbumParams struct {
	UserID    string
	AlbumID   string
	AssetID   string
	SortOrder *int64
}

func NewAlbumService(queries *sqlc.Queries, storageService *storage.StorageService, cfg config.Config) *AlbumService {
	return &AlbumService{
		queries:        queries,
		storageService: storageService,
		readExpires:    time.Duration(cfg.R2PresignedReadExpiresSeconds) * time.Second,
	}
}

func (service *AlbumService) Create(ctx context.Context, params CreateAlbumParams) (model.Album, error) {
	album, err := service.queries.CreateAlbum(ctx, sqlc.CreateAlbumParams{
		UserID:      params.UserID,
		Name:        params.Name,
		Description: params.Description,
	})
	if err != nil {
		return model.Album{}, err
	}
	return mapAlbum(album), nil
}

func (service *AlbumService) List(ctx context.Context, userID string) (model.AlbumListResponse, error) {
	albums, err := service.queries.ListAlbums(ctx, userID)
	if err != nil {
		return model.AlbumListResponse{}, err
	}

	items := make([]model.Album, 0, len(albums))
	for _, album := range albums {
		items = append(items, mapAlbum(album))
	}
	return model.AlbumListResponse{Items: items}, nil
}

func (service *AlbumService) Get(ctx context.Context, userID string, albumID string) (model.Album, error) {
	album, err := service.getAlbum(ctx, userID, albumID)
	if err != nil {
		return model.Album{}, err
	}
	return mapAlbum(album), nil
}

func (service *AlbumService) Update(ctx context.Context, params UpdateAlbumParams) (model.Album, error) {
	current, err := service.getAlbum(ctx, params.UserID, params.AlbumID)
	if err != nil {
		return model.Album{}, err
	}

	name := current.Name
	if params.Name != nil {
		name = *params.Name
	}

	description := current.Description
	if params.Description != nil {
		description = params.Description
	}

	coverAssetID := current.CoverAssetID
	if params.CoverAssetID != nil {
		if _, err := service.getAsset(ctx, params.UserID, *params.CoverAssetID); err != nil {
			return model.Album{}, err
		}
		coverAssetID = params.CoverAssetID
	}

	isArchived := boolValue(current.IsArchived)
	if params.IsArchived != nil {
		isArchived = *params.IsArchived
	}

	album, err := service.queries.UpdateAlbum(ctx, sqlc.UpdateAlbumParams{
		ID:           params.AlbumID,
		UserID:       params.UserID,
		Name:         name,
		Description:  description,
		CoverAssetID: coverAssetID,
		IsArchived:   isArchived,
	})
	if err != nil {
		return model.Album{}, mapAlbumNoRows(err)
	}
	return mapAlbum(album), nil
}

func (service *AlbumService) Delete(ctx context.Context, userID string, albumID string) error {
	if _, err := service.getAlbum(ctx, userID, albumID); err != nil {
		return err
	}
	return service.queries.DeleteAlbum(ctx, sqlc.GetAlbumByIDForUserParams{ID: albumID, UserID: userID})
}

func (service *AlbumService) AddAsset(ctx context.Context, params AddAssetToAlbumParams) error {
	if _, err := service.getAlbum(ctx, params.UserID, params.AlbumID); err != nil {
		return err
	}
	if _, err := service.getAsset(ctx, params.UserID, params.AssetID); err != nil {
		return err
	}
	return service.queries.AddAssetToAlbum(ctx, sqlc.AddAssetToAlbumParams{
		AlbumID:   params.AlbumID,
		AssetID:   params.AssetID,
		SortOrder: params.SortOrder,
	})
}

func (service *AlbumService) RemoveAsset(ctx context.Context, userID string, albumID string, assetID string) error {
	if _, err := service.getAlbum(ctx, userID, albumID); err != nil {
		return err
	}
	if _, err := service.getAsset(ctx, userID, assetID); err != nil {
		return err
	}
	return service.queries.RemoveAssetFromAlbum(ctx, sqlc.RemoveAssetFromAlbumParams{AlbumID: albumID, AssetID: assetID})
}

func (service *AlbumService) ListAssets(ctx context.Context, userID string, albumID string) (model.AssetListResponse, error) {
	if _, err := service.getAlbum(ctx, userID, albumID); err != nil {
		return model.AssetListResponse{}, err
	}

	assets, err := service.queries.ListAlbumAssets(ctx, sqlc.ListAlbumAssetsParams{AlbumID: albumID, UserID: userID})
	if err != nil {
		return model.AssetListResponse{}, err
	}

	items := make([]model.AssetListItem, 0, len(assets))
	for _, asset := range assets {
		item, err := service.mapListItem(ctx, asset)
		if err != nil {
			return model.AssetListResponse{}, err
		}
		items = append(items, item)
	}
	return model.AssetListResponse{Items: items}, nil
}

func (service *AlbumService) getAlbum(ctx context.Context, userID string, albumID string) (sqlc.Album, error) {
	album, err := service.queries.GetAlbumByIDForUser(ctx, sqlc.GetAlbumByIDForUserParams{ID: albumID, UserID: userID})
	if err != nil {
		return sqlc.Album{}, mapAlbumNoRows(err)
	}
	return album, nil
}

func (service *AlbumService) getAsset(ctx context.Context, userID string, assetID string) (sqlc.Asset, error) {
	asset, err := service.queries.GetAssetByIDForUser(ctx, sqlc.GetAssetByIDForUserParams{ID: assetID, UserID: userID})
	if err != nil {
		return sqlc.Asset{}, mapNoRows(err)
	}
	return asset, nil
}

func (service *AlbumService) mapListItem(ctx context.Context, asset sqlc.Asset) (model.AssetListItem, error) {
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

func mapAlbum(album sqlc.Album) model.Album {
	return model.Album{
		ID:           album.ID,
		Name:         album.Name,
		Description:  album.Description,
		CoverAssetID: album.CoverAssetID,
		IsArchived:   boolValue(album.IsArchived),
		CreatedAt:    album.CreatedAt,
		UpdatedAt:    album.UpdatedAt,
	}
}

func mapAlbumNoRows(err error) error {
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrAlbumNotFound
	}
	return err
}
