package handler

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"

	appmiddleware "photo-map-app/backend/internal/middleware"
	"photo-map-app/backend/internal/service"
	"photo-map-app/backend/internal/util"
)

type DeviceHandler struct {
	deviceService *service.DeviceService
}

type registerDeviceRequest struct {
	DeviceName        string `json:"deviceName" binding:"required"`
	Platform          string `json:"platform" binding:"required,oneof=android ios web"`
	DeviceFingerprint string `json:"deviceFingerprint" binding:"required"`
}

func NewDeviceHandler(deviceService *service.DeviceService) *DeviceHandler {
	return &DeviceHandler{deviceService: deviceService}
}

func (handler *DeviceHandler) Register(ctx *gin.Context) {
	userID, err := appmiddleware.UserID(ctx)
	if err != nil {
		util.WriteError(ctx, http.StatusUnauthorized, "unauthorized", "authenticated user is required")
		return
	}

	var request registerDeviceRequest
	if err := ctx.ShouldBindJSON(&request); err != nil {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "invalid device register request")
		return
	}
	if strings.TrimSpace(request.DeviceName) == "" || strings.TrimSpace(request.DeviceFingerprint) == "" {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "deviceName and deviceFingerprint are required")
		return
	}

	device, err := handler.deviceService.Register(ctx.Request.Context(), service.RegisterDeviceParams{
		UserID:            userID,
		DeviceName:        request.DeviceName,
		Platform:          request.Platform,
		DeviceFingerprint: request.DeviceFingerprint,
	})
	if err != nil {
		util.WriteInternalError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, device)
}

func (handler *DeviceHandler) Me(ctx *gin.Context) {
	userID, err := appmiddleware.UserID(ctx)
	if err != nil {
		util.WriteError(ctx, http.StatusUnauthorized, "unauthorized", "authenticated user is required")
		return
	}

	devices, err := handler.deviceService.ListForUser(ctx.Request.Context(), userID)
	if err != nil {
		util.WriteInternalError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, gin.H{"devices": devices})
}
