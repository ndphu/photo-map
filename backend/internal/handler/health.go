package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

type HealthHandler struct{}

type HealthResponse struct {
	Status string `json:"status"`
}

func NewHealthHandler() HealthHandler {
	return HealthHandler{}
}

func (handler HealthHandler) Get(ctx *gin.Context) {
	ctx.JSON(http.StatusOK, HealthResponse{Status: "ok"})
}
