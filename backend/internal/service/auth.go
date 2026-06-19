package service

import (
	"context"
	"errors"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"

	"photo-map-app/backend/internal/auth"
	"photo-map-app/backend/internal/db/sqlc"
	"photo-map-app/backend/internal/model"
)

const uniqueViolationCode = "23505"

type AuthService struct {
	queries      *sqlc.Queries
	tokenManager *auth.TokenManager
}

type RegisterUserParams struct {
	Email       string
	Password    string
	DisplayName string
}

type LoginParams struct {
	Email    string
	Password string
}

type AuthResult struct {
	AccessToken string     `json:"accessToken"`
	User        model.User `json:"user"`
}

func NewAuthService(queries *sqlc.Queries, tokenManager *auth.TokenManager) *AuthService {
	return &AuthService{
		queries:      queries,
		tokenManager: tokenManager,
	}
}

func (service *AuthService) Register(ctx context.Context, params RegisterUserParams) (AuthResult, error) {
	passwordHash, err := auth.HashPassword(params.Password)
	if err != nil {
		return AuthResult{}, err
	}

	user, err := service.queries.CreateUser(ctx, sqlc.CreateUserParams{
		Email:        normalizeEmail(params.Email),
		DisplayName:  strings.TrimSpace(params.DisplayName),
		PasswordHash: passwordHash,
	})
	if err != nil {
		if isUniqueViolation(err) {
			return AuthResult{}, ErrEmailAlreadyExists
		}
		return AuthResult{}, err
	}

	return service.authResult(user)
}

func (service *AuthService) Login(ctx context.Context, params LoginParams) (AuthResult, error) {
	user, err := service.queries.GetUserByEmail(ctx, normalizeEmail(params.Email))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return AuthResult{}, ErrInvalidCredentials
		}
		return AuthResult{}, err
	}

	if !auth.CheckPassword(params.Password, user.PasswordHash) {
		return AuthResult{}, ErrInvalidCredentials
	}

	return service.authResult(user)
}

func (service *AuthService) authResult(user sqlc.User) (AuthResult, error) {
	accessToken, err := service.tokenManager.Sign(user.ID, user.Email)
	if err != nil {
		return AuthResult{}, err
	}

	return AuthResult{
		AccessToken: accessToken,
		User:        mapUser(user),
	}, nil
}

func mapUser(user sqlc.User) model.User {
	return model.User{
		ID:          user.ID,
		Email:       user.Email,
		DisplayName: user.DisplayName,
	}
}

func normalizeEmail(email string) string {
	return strings.ToLower(strings.TrimSpace(email))
}

func isUniqueViolation(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == uniqueViolationCode
}
