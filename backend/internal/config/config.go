package config

import (
	"errors"
	"fmt"
	"os"
	"strconv"
	"time"
)

const (
	defaultPort                         = "8080"
	defaultJWTExpiresIn                 = 24 * time.Hour
	defaultPresignedUploadExpires       = 15 * time.Minute
	defaultPresignedReadExpires         = 15 * time.Minute
	minPresignedExpirationSeconds int64 = 1
)

type Config struct {
	DatabaseURL                     string
	JWTSecret                       string
	JWTExpiresIn                    time.Duration
	R2AccountID                     string
	R2AccessKeyID                   string
	R2SecretAccessKey               string
	R2Bucket                        string
	R2PresignedUploadExpiresSeconds int64
	R2PresignedReadExpiresSeconds   int64
	Port                            string
}

func Load() (Config, error) {
	if err := loadDotEnv(".env"); err != nil {
		return Config{}, err
	}

	cfg := Config{
		DatabaseURL:                     os.Getenv("DATABASE_URL"),
		JWTSecret:                       os.Getenv("JWT_SECRET"),
		JWTExpiresIn:                    getDuration("JWT_EXPIRES_IN", defaultJWTExpiresIn),
		R2AccountID:                     os.Getenv("R2_ACCOUNT_ID"),
		R2AccessKeyID:                   os.Getenv("R2_ACCESS_KEY_ID"),
		R2SecretAccessKey:               os.Getenv("R2_SECRET_ACCESS_KEY"),
		R2Bucket:                        os.Getenv("R2_BUCKET"),
		R2PresignedUploadExpiresSeconds: getInt64("R2_PRESIGNED_UPLOAD_EXPIRES_SECONDS", int64(defaultPresignedUploadExpires.Seconds())),
		R2PresignedReadExpiresSeconds:   getInt64("R2_PRESIGNED_READ_EXPIRES_SECONDS", int64(defaultPresignedReadExpires.Seconds())),
		Port:                            getString("PORT", defaultPort),
	}

	if err := cfg.validate(); err != nil {
		return Config{}, err
	}

	return cfg, nil
}

func (cfg Config) R2Endpoint() string {
	return fmt.Sprintf("https://%s.r2.cloudflarestorage.com", cfg.R2AccountID)
}

func (cfg Config) validate() error {
	if cfg.DatabaseURL == "" {
		return errors.New("DATABASE_URL is required")
	}
	if cfg.JWTSecret == "" {
		return errors.New("JWT_SECRET is required")
	}
	if cfg.R2AccountID == "" {
		return errors.New("R2_ACCOUNT_ID is required")
	}
	if cfg.R2AccessKeyID == "" {
		return errors.New("R2_ACCESS_KEY_ID is required")
	}
	if cfg.R2SecretAccessKey == "" {
		return errors.New("R2_SECRET_ACCESS_KEY is required")
	}
	if cfg.R2Bucket == "" {
		return errors.New("R2_BUCKET is required")
	}
	if cfg.R2PresignedUploadExpiresSeconds < minPresignedExpirationSeconds {
		return errors.New("R2_PRESIGNED_UPLOAD_EXPIRES_SECONDS must be greater than zero")
	}
	if cfg.R2PresignedReadExpiresSeconds < minPresignedExpirationSeconds {
		return errors.New("R2_PRESIGNED_READ_EXPIRES_SECONDS must be greater than zero")
	}
	return nil
}

func getString(key string, fallback string) string {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	return value
}

func getDuration(key string, fallback time.Duration) time.Duration {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}

	duration, err := time.ParseDuration(value)
	if err != nil {
		return fallback
	}

	return duration
}

func getInt64(key string, fallback int64) int64 {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}

	parsed, err := strconv.ParseInt(value, 10, 64)
	if err != nil {
		return fallback
	}

	return parsed
}
