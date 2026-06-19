package model

import "time"

type Device struct {
	ID                string    `json:"id"`
	UserID            string    `json:"userId"`
	DeviceName        string    `json:"deviceName"`
	Platform          string    `json:"platform"`
	DeviceFingerprint string    `json:"deviceFingerprint"`
	LastSeenAt        time.Time `json:"lastSeenAt"`
	CreatedAt         time.Time `json:"createdAt"`
	UpdatedAt         time.Time `json:"updatedAt"`
}
