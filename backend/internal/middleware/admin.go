package middleware

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"

	"photo-map-app/backend/internal/util"
)

func AdminOnly(adminEmails []string) gin.HandlerFunc {
	allowed := make(map[string]struct{}, len(adminEmails))
	for _, email := range adminEmails {
		allowed[strings.ToLower(strings.TrimSpace(email))] = struct{}{}
	}

	return func(ctx *gin.Context) {
		email, err := Email(ctx)
		if err != nil {
			util.WriteError(ctx, http.StatusUnauthorized, "unauthorized", "authenticated email is required")
			ctx.Abort()
			return
		}
		if _, ok := allowed[email]; !ok {
			util.WriteError(ctx, http.StatusForbidden, "admin_required", "admin access is required")
			ctx.Abort()
			return
		}
		ctx.Next()
	}
}
