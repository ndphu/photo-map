package middleware

import (
	"log/slog"
	"time"

	"github.com/gin-gonic/gin"
)

func RequestLogger(logger *slog.Logger) gin.HandlerFunc {
	return func(ctx *gin.Context) {
		startedAt := time.Now()

		ctx.Next()

		attributes := []any{
			slog.String("method", ctx.Request.Method),
			slog.String("path", ctx.Request.URL.Path),
			slog.Int("status", ctx.Writer.Status()),
			slog.Int("bytes", ctx.Writer.Size()),
			slog.Duration("duration", time.Since(startedAt)),
			slog.String("client_ip", ctx.ClientIP()),
		}
		if len(ctx.Errors) > 0 {
			attributes = append(attributes, slog.Any("errors", ctx.Errors.Errors()))
		}

		if ctx.Writer.Status() >= 500 {
			logger.Error("http request failed", attributes...)
			return
		}
		logger.Info("http request", attributes...)
	}
}
