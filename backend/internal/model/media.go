package model

import "time"

type Media struct {
	ID          string     `json:"id"`
	OwnerID     string     `json:"owner_id"`
	ObjectKey   string     `json:"object_key"`
	ContentType string     `json:"content_type"`
	SizeBytes   int64      `json:"size_bytes"`
	CapturedAt  *time.Time `json:"captured_at,omitempty"`
	Latitude    *float64   `json:"latitude,omitempty"`
	Longitude   *float64   `json:"longitude,omitempty"`
	CreatedAt   time.Time  `json:"created_at"`
	UpdatedAt   time.Time  `json:"updated_at"`
}
