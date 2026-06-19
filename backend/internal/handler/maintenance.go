package handler

import (
	"net/http"
	"time"

	"github.com/gin-gonic/gin"

	appmiddleware "photo-map-app/backend/internal/middleware"
	"photo-map-app/backend/internal/service"
	"photo-map-app/backend/internal/util"
)

const maxCleanupRequestLimit = 1000

type MaintenanceHandler struct {
	maintenanceService *service.MaintenanceService
}

type cleanupUploadSessionsRequest struct {
	DryRun         bool `json:"dryRun"`
	OlderThanHours *int `json:"olderThanHours"`
	Limit          int  `json:"limit"`
}

func NewMaintenanceHandler(maintenanceService *service.MaintenanceService) *MaintenanceHandler {
	return &MaintenanceHandler{maintenanceService: maintenanceService}
}

func (handler *MaintenanceHandler) CleanupUploadSessions(ctx *gin.Context) {
	userID, err := appmiddleware.UserID(ctx)
	if err != nil {
		util.WriteError(ctx, http.StatusUnauthorized, "unauthorized", "authenticated user is required")
		return
	}

	var request cleanupUploadSessionsRequest
	if err := ctx.ShouldBindJSON(&request); err != nil || request.OlderThanHours == nil || *request.OlderThanHours < 0 {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "olderThanHours must be zero or greater")
		return
	}
	if request.Limit < 0 || request.Limit > maxCleanupRequestLimit {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "limit must be between 1 and 1000")
		return
	}

	result, err := handler.maintenanceService.CleanupExpiredUploadSessionsWithOptions(
		ctx.Request.Context(),
		service.CleanupUploadSessionsParams{
			DryRun: request.DryRun, OlderThan: time.Duration(*request.OlderThanHours) * time.Hour,
			Limit: request.Limit, ActorUserID: &userID,
		},
	)
	if err != nil {
		util.WriteInternalError(ctx, err)
		return
	}
	ctx.JSON(http.StatusOK, result)
}
