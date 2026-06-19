package handler

import (
	"errors"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"

	"photo-map-app/backend/internal/service"
	"photo-map-app/backend/internal/util"
)

type AuthHandler struct {
	authService *service.AuthService
}

type registerRequest struct {
	Email       string `json:"email" binding:"required,email"`
	Password    string `json:"password" binding:"required,min=8"`
	DisplayName string `json:"displayName" binding:"required"`
}

type loginRequest struct {
	Email    string `json:"email" binding:"required,email"`
	Password string `json:"password" binding:"required"`
}

func NewAuthHandler(authService *service.AuthService) *AuthHandler {
	return &AuthHandler{authService: authService}
}

func (handler *AuthHandler) Register(ctx *gin.Context) {
	var request registerRequest
	if err := ctx.ShouldBindJSON(&request); err != nil {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "invalid register request")
		return
	}
	if strings.TrimSpace(request.DisplayName) == "" {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "displayName is required")
		return
	}

	result, err := handler.authService.Register(ctx.Request.Context(), service.RegisterUserParams{
		Email:       request.Email,
		Password:    request.Password,
		DisplayName: request.DisplayName,
	})
	if err != nil {
		if errors.Is(err, service.ErrEmailAlreadyExists) {
			util.WriteError(ctx, http.StatusConflict, "email_already_exists", "email already exists")
			return
		}
		util.WriteInternalError(ctx, err)
		return
	}

	ctx.JSON(http.StatusCreated, result)
}

func (handler *AuthHandler) Login(ctx *gin.Context) {
	var request loginRequest
	if err := ctx.ShouldBindJSON(&request); err != nil {
		util.WriteError(ctx, http.StatusBadRequest, "invalid_request", "invalid login request")
		return
	}

	result, err := handler.authService.Login(ctx.Request.Context(), service.LoginParams{
		Email:    request.Email,
		Password: request.Password,
	})
	if err != nil {
		if errors.Is(err, service.ErrInvalidCredentials) {
			util.WriteError(ctx, http.StatusUnauthorized, "invalid_credentials", "invalid email or password")
			return
		}
		util.WriteInternalError(ctx, err)
		return
	}

	ctx.JSON(http.StatusOK, result)
}
