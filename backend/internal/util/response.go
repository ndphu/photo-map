package util

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

type ErrorResponse struct {
	Error ErrorBody `json:"error"`
}

type ErrorBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

func WriteError(ctx *gin.Context, statusCode int, code string, message string) {
	ctx.JSON(statusCode, ErrorResponse{
		Error: ErrorBody{
			Code:    code,
			Message: message,
		},
	})
}

func WriteInternalError(ctx *gin.Context, cause error) {
	if cause != nil {
		_ = ctx.Error(cause)
	}
	WriteError(ctx, http.StatusInternalServerError, "internal_error", "internal server error")
}
