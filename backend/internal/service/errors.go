package service

import "errors"

var (
	ErrInvalidCredentials = errors.New("invalid credentials")
	ErrEmailAlreadyExists = errors.New("email already exists")
	ErrDeviceNotFound     = errors.New("device not found")
	ErrUploadNotFound     = errors.New("upload session not found")
	ErrUploadForbidden    = errors.New("upload session forbidden")
	ErrUploadExpired      = errors.New("upload session expired")
	ErrUploadCompleted    = errors.New("upload session already completed")
	ErrChecksumMismatch   = errors.New("checksum mismatch")
	ErrObjectNotFound     = errors.New("object not found")
	ErrObjectSizeMismatch = errors.New("object size mismatch")
	ErrAssetNotFound      = errors.New("asset not found")
	ErrInvalidCursor      = errors.New("invalid cursor")
	ErrInvalidVariant     = errors.New("invalid asset variant")
	ErrAlbumNotFound      = errors.New("album not found")
)
