package main

import (
	"io"
	"log/slog"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5/pgxpool"

	"photo-map-app/backend/internal/config"
)

func TestAssetChangesRouteIsRegistered(t *testing.T) {
	gin.SetMode(gin.TestMode)
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	router := buildRouter(logger, config.Config{
		JWTSecret: "test-secret", R2AccountID: "account", R2AccessKeyID: "key",
		R2SecretAccessKey: "secret", R2Bucket: "bucket", R2PresignedReadExpiresSeconds: 60,
	}, (*pgxpool.Pool)(nil))

	for _, route := range router.Routes() {
		if route.Method == "GET" && route.Path == "/assets/changes" {
			return
		}
	}
	t.Fatal("GET /assets/changes route is not registered")
}
