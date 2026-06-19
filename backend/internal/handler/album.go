package handler

import (
	"errors"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"

	"photo-map-app/backend/internal/service"
	"photo-map-app/backend/internal/util"
)

type AlbumHandler struct {
	albumService *service.AlbumService
}

type createAlbumRequest struct {
	Name        string  `json:"name" binding:"required"`
	Description *string `json:"description"`
}

type updateAlbumRequest struct {
	Name         *string `json:"name"`
	Description  *string `json:"description"`
	CoverAssetID *string `json:"coverAssetId"`
	IsArchived   *bool   `json:"isArchived"`
}

type addAssetToAlbumRequest struct {
	AssetID   string `json:"assetId" binding:"required"`
	SortOrder *int64 `json:"sortOrder"`
}

func NewAlbumHandler(albumService *service.AlbumService) *AlbumHandler {
	return &AlbumHandler{albumService: albumService}
}

func (handler *AlbumHandler) Create(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	var request createAlbumRequest
	if err := ctx.ShouldBindJSON(&request); err != nil || strings.TrimSpace(request.Name) == "" {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "name is required")
		return
	}

	album, err := handler.albumService.Create(ctx.Request.Context(), service.CreateAlbumParams{
		UserID:      userID,
		Name:        strings.TrimSpace(request.Name),
		Description: normalizeStringPointer(request.Description),
	})
	if err != nil {
		writeAlbumError(ctx, err)
		return
	}

	ctx.JSON(http.StatusCreated, album)
}

func (handler *AlbumHandler) List(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	response, err := handler.albumService.List(ctx.Request.Context(), userID)
	if err != nil {
		writeAlbumError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, response)
}

func (handler *AlbumHandler) Get(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	album, err := handler.albumService.Get(ctx.Request.Context(), userID, ctx.Param("id"))
	if err != nil {
		writeAlbumError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, album)
}

func (handler *AlbumHandler) Update(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	var request updateAlbumRequest
	if err := ctx.ShouldBindJSON(&request); err != nil {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "invalid album update request")
		return
	}
	if request.Name != nil && strings.TrimSpace(*request.Name) == "" {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "name cannot be blank")
		return
	}

	album, err := handler.albumService.Update(ctx.Request.Context(), service.UpdateAlbumParams{
		UserID:       userID,
		AlbumID:      ctx.Param("id"),
		Name:         normalizeStringPointer(request.Name),
		Description:  normalizeStringPointer(request.Description),
		CoverAssetID: normalizeStringPointer(request.CoverAssetID),
		IsArchived:   request.IsArchived,
	})
	if err != nil {
		writeAlbumError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, album)
}

func (handler *AlbumHandler) Delete(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	if err := handler.albumService.Delete(ctx.Request.Context(), userID, ctx.Param("id")); err != nil {
		writeAlbumError(ctx, err)
		return
	}

	ctx.Status(http.StatusNoContent)
}

func (handler *AlbumHandler) AddAsset(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	var request addAssetToAlbumRequest
	if err := ctx.ShouldBindJSON(&request); err != nil || strings.TrimSpace(request.AssetID) == "" {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "assetId is required")
		return
	}

	err := handler.albumService.AddAsset(ctx.Request.Context(), service.AddAssetToAlbumParams{
		UserID:    userID,
		AlbumID:   ctx.Param("id"),
		AssetID:   strings.TrimSpace(request.AssetID),
		SortOrder: request.SortOrder,
	})
	if err != nil {
		writeAlbumError(ctx, err)
		return
	}

	ctx.Status(http.StatusNoContent)
}

func (handler *AlbumHandler) RemoveAsset(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	err := handler.albumService.RemoveAsset(ctx.Request.Context(), userID, ctx.Param("id"), ctx.Param("assetId"))
	if err != nil {
		writeAlbumError(ctx, err)
		return
	}

	ctx.Status(http.StatusNoContent)
}

func (handler *AlbumHandler) ListAssets(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	response, err := handler.albumService.ListAssets(ctx.Request.Context(), userID, ctx.Param("id"))
	if err != nil {
		writeAlbumError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, response)
}

func normalizeStringPointer(value *string) *string {
	if value == nil {
		return nil
	}
	trimmed := strings.TrimSpace(*value)
	if trimmed == "" {
		return nil
	}
	return &trimmed
}

func writeAlbumError(ctx *gin.Context, err error) {
	switch {
	case errors.Is(err, service.ErrAlbumNotFound):
		util.WriteError(ctx, http.StatusNotFound, "album_not_found", "album not found")
	case errors.Is(err, service.ErrAssetNotFound):
		util.WriteError(ctx, http.StatusNotFound, "asset_not_found", "asset not found")
	default:
		util.WriteInternalError(ctx, err)
	}
}
