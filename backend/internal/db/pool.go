package db

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

const pingTimeout = 5 * time.Second

func NewPool(ctx context.Context, databaseURL string) (*pgxpool.Pool, error) {
	return newPool(ctx, databaseURL, 0)
}

func NewPoolWithMaxConnections(
	ctx context.Context,
	databaseURL string,
	maxConnections int32,
) (*pgxpool.Pool, error) {
	if maxConnections < 1 {
		return nil, errors.New("maxConnections must be positive")
	}
	return newPool(ctx, databaseURL, maxConnections)
}

func newPool(ctx context.Context, databaseURL string, maxConnections int32) (*pgxpool.Pool, error) {
	poolConfig, err := pgxpool.ParseConfig(databaseURL)
	if err != nil {
		return nil, err
	}
	if maxConnections > 0 {
		poolConfig.MaxConns = maxConnections
	}

	pool, err := pgxpool.NewWithConfig(ctx, poolConfig)
	if err != nil {
		return nil, err
	}

	pingCtx, cancel := context.WithTimeout(ctx, pingTimeout)
	defer cancel()

	if err := pool.Ping(pingCtx); err != nil {
		pool.Close()
		return nil, err
	}

	return pool, nil
}
