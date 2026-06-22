package middleware

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

const (
	corsAllowedMethods = "GET, POST, PATCH, DELETE, OPTIONS"
	corsAllowedHeaders = "Authorization, Content-Type"
)

func CORS(allowedOrigins []string) gin.HandlerFunc {
	allowed := make(map[string]struct{}, len(allowedOrigins))
	for _, origin := range allowedOrigins {
		allowed[origin] = struct{}{}
	}

	return func(ctx *gin.Context) {
		origin := ctx.GetHeader("Origin")
		if _, ok := allowed[origin]; !ok || origin == "" {
			ctx.Next()
			return
		}

		headers := ctx.Writer.Header()
		headers.Set("Access-Control-Allow-Origin", origin)
		headers.Set("Access-Control-Allow-Methods", corsAllowedMethods)
		headers.Set("Access-Control-Allow-Headers", corsAllowedHeaders)
		headers.Add("Vary", "Origin")

		if ctx.Request.Method == http.MethodOptions {
			ctx.AbortWithStatus(http.StatusNoContent)
			return
		}

		ctx.Next()
	}
}
