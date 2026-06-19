package service

import (
	"context"
	"strings"

	"photo-map-app/backend/internal/db/sqlc"
	"photo-map-app/backend/internal/model"
)

type DeviceService struct {
	queries *sqlc.Queries
}

type RegisterDeviceParams struct {
	UserID            string
	DeviceName        string
	Platform          string
	DeviceFingerprint string
}

func NewDeviceService(queries *sqlc.Queries) *DeviceService {
	return &DeviceService{queries: queries}
}

func (service *DeviceService) Register(ctx context.Context, params RegisterDeviceParams) (model.Device, error) {
	device, err := service.queries.UpsertDevice(ctx, sqlc.UpsertDeviceParams{
		UserID:            params.UserID,
		DeviceName:        strings.TrimSpace(params.DeviceName),
		Platform:          strings.TrimSpace(params.Platform),
		DeviceFingerprint: strings.TrimSpace(params.DeviceFingerprint),
	})
	if err != nil {
		return model.Device{}, err
	}

	return mapDevice(device), nil
}

func (service *DeviceService) ListForUser(ctx context.Context, userID string) ([]model.Device, error) {
	devices, err := service.queries.ListDevicesByUserID(ctx, userID)
	if err != nil {
		return nil, err
	}

	result := make([]model.Device, 0, len(devices))
	for _, device := range devices {
		result = append(result, mapDevice(device))
	}

	return result, nil
}

func mapDevice(device sqlc.Device) model.Device {
	return model.Device{
		ID:                device.ID,
		UserID:            device.UserID,
		DeviceName:        device.DeviceName,
		Platform:          device.Platform,
		DeviceFingerprint: device.DeviceFingerprint,
		LastSeenAt:        device.LastSeenAt,
		CreatedAt:         device.CreatedAt,
		UpdatedAt:         device.UpdatedAt,
	}
}
