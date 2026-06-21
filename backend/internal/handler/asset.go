package handler

import (
	"context"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	appmiddleware "photo-map-app/backend/internal/middleware"
	"photo-map-app/backend/internal/model"
	"photo-map-app/backend/internal/service"
	"photo-map-app/backend/internal/util"
)

const isoDateLayout = "2006-01-02"

type AssetHandler struct {
	assetService *service.AssetService
}

type updateFavoriteRequest struct {
	IsFavorite *bool `json:"isFavorite" binding:"required"`
}

type updateArchiveRequest struct {
	IsArchived *bool `json:"isArchived" binding:"required"`
}

func NewAssetHandler(assetService *service.AssetService) *AssetHandler {
	return &AssetHandler{assetService: assetService}
}

func (handler *AssetHandler) List(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	params, ok := parseListAssetsParams(ctx, userID)
	if !ok {
		return
	}

	response, err := handler.assetService.List(ctx.Request.Context(), params)
	if err != nil {
		writeAssetError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, response)
}

func (handler *AssetHandler) ListChanges(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	cursor, err := parseChangeCursor(ctx.Query("cursor"))
	if err != nil {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_cursor", "cursor must be a non-negative integer")
		return
	}
	limit, ok := parseLimit(ctx)
	if !ok {
		return
	}

	response, err := handler.assetService.ListChanges(ctx.Request.Context(), service.ListAssetChangesParams{
		UserID: userID,
		Cursor: cursor,
		Limit:  limit,
	})
	if err != nil {
		writeAssetError(ctx, err)
		return
	}
	ctx.JSON(http.StatusOK, response)
}

func (handler *AssetHandler) Search(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	query := strings.TrimSpace(ctx.Query("q"))
	if query == "" {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "q is required")
		return
	}

	limit, ok := parseLimit(ctx)
	if !ok {
		return
	}

	response, err := handler.assetService.Search(ctx.Request.Context(), service.SearchAssetsParams{
		UserID: userID,
		Query:  query,
		Limit:  limit,
		Cursor: ctx.Query("cursor"),
	})
	if err != nil {
		writeAssetError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, response)
}

func (handler *AssetHandler) Get(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	asset, err := handler.assetService.Get(ctx.Request.Context(), userID, ctx.Param("id"))
	if err != nil {
		writeAssetError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, asset)
}

func (handler *AssetHandler) ReadURL(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	response, err := handler.assetService.ReadURL(ctx.Request.Context(), userID, ctx.Param("id"), ctx.Query("variant"))
	if err != nil {
		writeAssetError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, response)
}

func (handler *AssetHandler) UpdateFavorite(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	var request updateFavoriteRequest
	if err := ctx.ShouldBindJSON(&request); err != nil || request.IsFavorite == nil {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "isFavorite is required")
		return
	}

	asset, err := handler.assetService.UpdateFavorite(ctx.Request.Context(), userID, ctx.Param("id"), *request.IsFavorite)
	if err != nil {
		writeAssetError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, asset)
}

func (handler *AssetHandler) UpdateArchive(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	var request updateArchiveRequest
	if err := ctx.ShouldBindJSON(&request); err != nil || request.IsArchived == nil {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "isArchived is required")
		return
	}

	asset, err := handler.assetService.UpdateArchive(ctx.Request.Context(), userID, ctx.Param("id"), *request.IsArchived)
	if err != nil {
		writeAssetError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, asset)
}

func (handler *AssetHandler) Trash(ctx *gin.Context) {
	handler.mutateAsset(ctx, handler.assetService.Trash)
}

func (handler *AssetHandler) Restore(ctx *gin.Context) {
	handler.mutateAsset(ctx, handler.assetService.Restore)
}

func (handler *AssetHandler) Delete(ctx *gin.Context) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	if err := handler.assetService.Delete(ctx.Request.Context(), userID, ctx.Param("id")); err != nil {
		writeAssetError(ctx, err)
		return
	}

	ctx.Status(http.StatusNoContent)
}

func (handler *AssetHandler) mutateAsset(ctx *gin.Context, mutate func(context.Context, string, string) (model.AssetDetail, error)) {
	userID, ok := authenticatedUserID(ctx)
	if !ok {
		return
	}

	asset, err := mutate(ctx.Request.Context(), userID, ctx.Param("id"))
	if err != nil {
		writeAssetError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, asset)
}

func authenticatedUserID(ctx *gin.Context) (string, bool) {
	userID, err := appmiddleware.UserID(ctx)
	if err != nil {
		util.WriteError(ctx, http.StatusUnauthorized, "unauthorized", "authenticated user is required")
		return "", false
	}
	return userID, true
}

func parseListAssetsParams(ctx *gin.Context, userID string) (service.ListAssetsParams, bool) {
	limit, ok := parseLimit(ctx)
	if !ok {
		return service.ListAssetsParams{}, false
	}

	mediaType := optionalQuery(ctx, "mediaType")
	if mediaType != nil && *mediaType != "image" && *mediaType != "video" {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "mediaType must be image or video")
		return service.ListAssetsParams{}, false
	}

	favorite, ok := optionalBoolQuery(ctx, "favorite")
	if !ok {
		return service.ListAssetsParams{}, false
	}
	archived, ok := optionalBoolQuery(ctx, "archived")
	if !ok {
		return service.ListAssetsParams{}, false
	}
	trashed, ok := optionalBoolQuery(ctx, "trashed")
	if !ok {
		return service.ListAssetsParams{}, false
	}

	from, ok := optionalTimeQuery(ctx, "from")
	if !ok {
		return service.ListAssetsParams{}, false
	}
	to, ok := optionalTimeQuery(ctx, "to")
	if !ok {
		return service.ListAssetsParams{}, false
	}

	return service.ListAssetsParams{
		UserID:    userID,
		Limit:     limit,
		Cursor:    ctx.Query("cursor"),
		MediaType: mediaType,
		Favorite:  favorite,
		Archived:  archived,
		Trashed:   boolValue(trashed),
		City:      optionalQuery(ctx, "city"),
		From:      from,
		To:        to,
	}, true
}

func parseLimit(ctx *gin.Context) (int, bool) {
	value := strings.TrimSpace(ctx.Query("limit"))
	if value == "" {
		return 0, true
	}

	limit, err := strconv.Atoi(value)
	if err != nil || limit < 1 {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "limit must be a positive integer")
		return 0, false
	}

	return limit, true
}

func parseChangeCursor(value string) (int64, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		return 0, nil
	}
	cursor, err := strconv.ParseInt(value, 10, 64)
	if err != nil || cursor < 0 {
		return 0, service.ErrInvalidCursor
	}
	return cursor, nil
}

func optionalQuery(ctx *gin.Context, key string) *string {
	value := strings.TrimSpace(ctx.Query(key))
	if value == "" {
		return nil
	}
	return &value
}

func optionalBoolQuery(ctx *gin.Context, key string) (*bool, bool) {
	value := strings.TrimSpace(ctx.Query(key))
	if value == "" {
		return nil, true
	}

	parsed, err := strconv.ParseBool(value)
	if err != nil {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", key+" must be a boolean")
		return nil, false
	}

	return &parsed, true
}

func optionalTimeQuery(ctx *gin.Context, key string) (*time.Time, bool) {
	value := strings.TrimSpace(ctx.Query(key))
	if value == "" {
		return nil, true
	}

	parsed, err := time.Parse(time.RFC3339, value)
	if err != nil {
		dateOnly, dateErr := time.Parse(isoDateLayout, value)
		if dateErr != nil {
			util.WriteError(ctx, http.StatusBadRequest, "invalid_request", key+" must be an ISO date")
			return nil, false
		}
		parsed = dateOnly
	}

	return &parsed, true
}

func boolValue(value *bool) bool {
	return value != nil && *value
}

func writeAssetError(ctx *gin.Context, err error) {
	switch {
	case errors.Is(err, service.ErrAssetNotFound):
		util.WriteError(ctx, http.StatusNotFound, "asset_not_found", "asset not found")
	case errors.Is(err, service.ErrInvalidCursor):
		util.WriteError(ctx, http.StatusBadRequest, "invalid_cursor", "cursor is invalid")
	case errors.Is(err, service.ErrInvalidVariant):
		util.WriteError(ctx, http.StatusBadRequest, "invalid_variant", "variant must be original, thumbnail, preview, or posterFrame")
	default:
		util.WriteInternalError(ctx, err)
	}
}
