package handler

import (
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	appmiddleware "photo-map-app/backend/internal/middleware"
	"photo-map-app/backend/internal/service"
	"photo-map-app/backend/internal/util"
)

type UploadHandler struct {
	uploadService *service.UploadService
}

type createUploadSessionRequest struct {
	DeviceID               string  `json:"deviceId" binding:"required"`
	LocalAssetID           string  `json:"localAssetId" binding:"required"`
	MediaType              string  `json:"mediaType" binding:"required,oneof=image video"`
	MimeType               string  `json:"mimeType" binding:"required"`
	OriginalFilename       string  `json:"originalFilename" binding:"required"`
	FileSizeBytes          int64   `json:"fileSizeBytes" binding:"required,gt=0"`
	ExpectedChecksumSha256 *string `json:"expectedChecksumSha256"`
}

type completeUploadSessionRequest struct {
	ChecksumSha256        *string    `json:"checksumSha256" binding:"required"`
	TakenAt               *time.Time `json:"takenAt"`
	TakenAtSource         *string    `json:"takenAtSource"`
	TimezoneOffsetMinutes *int32     `json:"timezoneOffsetMinutes"`
	Width                 *int32     `json:"width"`
	Height                *int32     `json:"height"`
	Orientation           *int32     `json:"orientation"`
	DurationMs            *int64     `json:"durationMs"`
	Latitude              *float64   `json:"latitude"`
	Longitude             *float64   `json:"longitude"`
	CameraMake            *string    `json:"cameraMake"`
	CameraModel           *string    `json:"cameraModel"`
	Software              *string    `json:"software"`
	LocalCreatedAt        *time.Time `json:"localCreatedAt"`
	LocalModifiedAt       *time.Time `json:"localModifiedAt"`
}

func NewUploadHandler(uploadService *service.UploadService) *UploadHandler {
	return &UploadHandler{uploadService: uploadService}
}

func (handler *UploadHandler) CreateSession(ctx *gin.Context) {
	userID, err := appmiddleware.UserID(ctx)
	if err != nil {
		util.WriteError(ctx, http.StatusUnauthorized, "unauthorized", "authenticated user is required")
		return
	}

	var request createUploadSessionRequest
	if err := ctx.ShouldBindJSON(&request); err != nil {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "invalid upload session request")
		return
	}
	if hasBlank(request.DeviceID, request.LocalAssetID, request.MimeType, request.OriginalFilename) {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "required fields cannot be blank")
		return
	}

	response, err := handler.uploadService.CreateSession(ctx.Request.Context(), service.CreateUploadSessionParams{
		UserID:                 userID,
		DeviceID:               request.DeviceID,
		LocalAssetID:           request.LocalAssetID,
		MediaType:              request.MediaType,
		MimeType:               request.MimeType,
		OriginalFilename:       request.OriginalFilename,
		FileSizeBytes:          request.FileSizeBytes,
		ExpectedChecksumSha256: request.ExpectedChecksumSha256,
	})
	if err != nil {
		if errors.Is(err, service.ErrDeviceNotFound) {
			util.WriteError(ctx, http.StatusNotFound, "device_not_found", "device not found")
			return
		}
		util.WriteInternalError(ctx, err)
		return
	}

	ctx.JSON(http.StatusCreated, response)
}

func (handler *UploadHandler) Complete(ctx *gin.Context) {
	userID, err := appmiddleware.UserID(ctx)
	if err != nil {
		util.WriteError(ctx, http.StatusUnauthorized, "unauthorized", "authenticated user is required")
		return
	}

	uploadSessionID := strings.TrimSpace(ctx.Param("id"))
	if uploadSessionID == "" {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "upload session id is required")
		return
	}

	var request completeUploadSessionRequest
	if err := ctx.ShouldBindJSON(&request); err != nil {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "invalid complete upload request")
		return
	}
	if request.ChecksumSha256 == nil || strings.TrimSpace(*request.ChecksumSha256) == "" {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "checksumSha256 is required")
		return
	}

	response, err := handler.uploadService.Complete(ctx.Request.Context(), service.CompleteUploadParams{
		UserID:                userID,
		UploadSessionID:       uploadSessionID,
		ChecksumSha256:        request.ChecksumSha256,
		TakenAt:               request.TakenAt,
		TakenAtSource:         request.TakenAtSource,
		TimezoneOffsetMinutes: request.TimezoneOffsetMinutes,
		Width:                 request.Width,
		Height:                request.Height,
		Orientation:           request.Orientation,
		DurationMs:            request.DurationMs,
		Latitude:              request.Latitude,
		Longitude:             request.Longitude,
		CameraMake:            request.CameraMake,
		CameraModel:           request.CameraModel,
		Software:              request.Software,
		LocalCreatedAt:        request.LocalCreatedAt,
		LocalModifiedAt:       request.LocalModifiedAt,
	})
	if err != nil {
		writeCompleteUploadError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, response)
}

func writeCompleteUploadError(ctx *gin.Context, err error) {
	switch {
	case errors.Is(err, service.ErrUploadNotFound):
		util.WriteError(ctx, http.StatusNotFound, "upload_session_not_found", "upload session not found")
	case errors.Is(err, service.ErrUploadForbidden):
		util.WriteError(ctx, http.StatusForbidden, "forbidden", "upload session belongs to another user")
	case errors.Is(err, service.ErrUploadExpired):
		util.WriteError(ctx, http.StatusConflict, "upload_session_expired", "upload session expired")
	case errors.Is(err, service.ErrUploadCompleted):
		util.WriteError(ctx, http.StatusConflict, "upload_session_completed", "upload session already completed")
	case errors.Is(err, service.ErrChecksumMismatch):
		util.WriteError(ctx, http.StatusConflict, "checksum_mismatch", "checksum does not match expected checksum")
	case errors.Is(err, service.ErrObjectNotFound):
		util.WriteError(ctx, http.StatusConflict, "object_not_found", "uploaded object was not found")
	case errors.Is(err, service.ErrObjectSizeMismatch):
		util.WriteError(ctx, http.StatusConflict, "object_size_mismatch", "uploaded object size does not match upload session")
	default:
		util.WriteInternalError(ctx, err)
	}
}

func hasBlank(values ...string) bool {
	for _, value := range values {
		if strings.TrimSpace(value) == "" {
			return true
		}
	}

	return false
}
