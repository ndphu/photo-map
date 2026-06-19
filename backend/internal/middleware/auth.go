package middleware

import (
	"errors"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"

	"photo-map-app/backend/internal/auth"
	"photo-map-app/backend/internal/util"
)

const (
	authorizationHeader = "Authorization"
	bearerPrefix        = "Bearer "
	userIDContextKey    = "userID"
	emailContextKey     = "email"
)

func Auth(verifier *auth.TokenManager) gin.HandlerFunc {
	return func(ctx *gin.Context) {
		header := ctx.GetHeader(authorizationHeader)
		if !strings.HasPrefix(header, bearerPrefix) {
			util.WriteError(ctx, http.StatusUnauthorized, "unauthorized", "missing bearer token")
			ctx.Abort()
			return
		}

		token := strings.TrimPrefix(header, bearerPrefix)
		claims, err := verifier.Verify(token)
		if err != nil {
			util.WriteError(ctx, http.StatusUnauthorized, "unauthorized", "invalid bearer token")
			ctx.Abort()
			return
		}

		ctx.Set(userIDContextKey, claims.Subject)
		ctx.Set(emailContextKey, strings.ToLower(claims.Email))
		ctx.Next()
	}
}

func Email(ctx *gin.Context) (string, error) {
	value, exists := ctx.Get(emailContextKey)
	if !exists {
		return "", errors.New("authenticated email is missing")
	}

	email, ok := value.(string)
	if !ok || email == "" {
		return "", errors.New("authenticated email is invalid")
	}
	return email, nil
}

func UserID(ctx *gin.Context) (string, error) {
	value, exists := ctx.Get(userIDContextKey)
	if !exists {
		return "", errors.New("authenticated user id is missing")
	}

	userID, ok := value.(string)
	if !ok || userID == "" {
		return "", errors.New("authenticated user id is invalid")
	}

	return userID, nil
}
