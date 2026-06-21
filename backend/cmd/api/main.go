package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5/pgxpool"

	"photo-map-app/backend/internal/auth"
	"photo-map-app/backend/internal/config"
	"photo-map-app/backend/internal/db"
	"photo-map-app/backend/internal/db/sqlc"
	"photo-map-app/backend/internal/handler"
	appmiddleware "photo-map-app/backend/internal/middleware"
	"photo-map-app/backend/internal/service"
	"photo-map-app/backend/internal/storage"
	"photo-map-app/backend/internal/util"
)

const shutdownTimeout = 10 * time.Second

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))

	cfg, err := config.Load()
	if err != nil {
		logger.Error("failed to load config", slog.Any("error", err))
		os.Exit(1)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	pool, err := db.NewPool(ctx, cfg.DatabaseURL)
	if err != nil {
		logger.Error("failed to connect database", slog.Any("error", err))
		os.Exit(1)
	}
	defer pool.Close()

	router := buildRouter(logger, cfg, pool)
	server := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           router,
		ReadHeaderTimeout: 5 * time.Second,
	}

	go func() {
		logger.Info("api server listening", slog.String("port", cfg.Port))
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Error("api server failed", slog.Any("error", err))
			os.Exit(1)
		}
	}()

	<-ctx.Done()
	logger.Info("shutdown signal received")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
	defer cancel()

	if err := server.Shutdown(shutdownCtx); err != nil {
		logger.Error("api server shutdown failed", slog.Any("error", err))
		os.Exit(1)
	}

	logger.Info("api server stopped")
}

func buildRouter(logger *slog.Logger, cfg config.Config, pool *pgxpool.Pool) *gin.Engine {
	router := gin.New()
	router.HandleMethodNotAllowed = true
	router.Use(gin.CustomRecovery(func(ctx *gin.Context, recovered interface{}) {
		logger.Error("panic recovered", slog.Any("panic", recovered))
		util.WriteError(ctx, http.StatusInternalServerError, "internal_error", "internal server error")
	}))
	router.Use(appmiddleware.RequestLogger(logger))
	router.NoRoute(func(ctx *gin.Context) {
		util.WriteError(ctx, http.StatusNotFound, "not_found", "route not found")
	})
	router.NoMethod(func(ctx *gin.Context) {
		util.WriteError(ctx, http.StatusMethodNotAllowed, "method_not_allowed", "method not allowed")
	})

	healthHandler := handler.NewHealthHandler()
	router.GET("/health", healthHandler.Get)

	queries := sqlc.New(pool)
	tokenManager := auth.NewTokenManager(cfg.JWTSecret, cfg.JWTExpiresIn)
	authService := service.NewAuthService(queries, tokenManager)
	deviceService := service.NewDeviceService(queries)
	storageService := storage.NewStorageService(cfg)
	uploadService := service.NewUploadService(pool, queries, storageService, cfg)
	assetService := service.NewAssetService(pool, queries, storageService, cfg)
	albumService := service.NewAlbumService(queries, storageService, cfg)
	maintenanceService := service.NewMaintenanceService(pool, queries, storageService)
	authHandler := handler.NewAuthHandler(authService)
	deviceHandler := handler.NewDeviceHandler(deviceService)
	uploadHandler := handler.NewUploadHandler(uploadService)
	assetHandler := handler.NewAssetHandler(assetService)
	albumHandler := handler.NewAlbumHandler(albumService)
	maintenanceHandler := handler.NewMaintenanceHandler(maintenanceService)

	router.POST("/auth/register", authHandler.Register)
	router.POST("/auth/login", authHandler.Login)

	protected := router.Group("/")
	protected.Use(appmiddleware.Auth(tokenManager))
	protected.POST("/devices/register", deviceHandler.Register)
	protected.GET("/devices/me", deviceHandler.Me)
	protected.POST("/upload-sessions", uploadHandler.CreateSession)
	protected.POST("/upload-sessions/:id/resume", uploadHandler.Resume)
	protected.PATCH("/upload-sessions/:id/status", uploadHandler.UpdateStatus)
	protected.POST("/upload-sessions/:id/complete", uploadHandler.Complete)
	protected.GET("/assets", assetHandler.List)
	protected.GET("/assets/changes", assetHandler.ListChanges)
	protected.GET("/assets/:id", assetHandler.Get)
	protected.GET("/assets/:id/read-url", assetHandler.ReadURL)
	protected.PATCH("/assets/:id/favorite", assetHandler.UpdateFavorite)
	protected.PATCH("/assets/:id/archive", assetHandler.UpdateArchive)
	protected.POST("/assets/:id/trash", assetHandler.Trash)
	protected.POST("/assets/:id/restore", assetHandler.Restore)
	protected.DELETE("/assets/:id", assetHandler.Delete)
	protected.GET("/search", assetHandler.Search)
	protected.POST("/albums", albumHandler.Create)
	protected.GET("/albums", albumHandler.List)
	protected.GET("/albums/:id", albumHandler.Get)
	protected.PATCH("/albums/:id", albumHandler.Update)
	protected.DELETE("/albums/:id", albumHandler.Delete)
	protected.POST("/albums/:id/assets", albumHandler.AddAsset)
	protected.DELETE("/albums/:id/assets/:assetId", albumHandler.RemoveAsset)
	protected.GET("/albums/:id/assets", albumHandler.ListAssets)

	maintenance := protected.Group("/maintenance")
	maintenance.Use(appmiddleware.AdminOnly(cfg.AdminEmails))
	maintenance.POST("/upload-sessions/cleanup", maintenanceHandler.CleanupUploadSessions)

	return router
}
