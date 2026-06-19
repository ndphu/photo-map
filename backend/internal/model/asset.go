package model

import "time"

type AssetListItem struct {
	ID           string     `json:"id"`
	MediaType    string     `json:"mediaType"`
	MimeType     string     `json:"mimeType"`
	ThumbnailKey *string    `json:"thumbnailKey"`
	PreviewKey   *string    `json:"previewKey"`
	ThumbnailURL *string    `json:"thumbnailUrl"`
	PreviewURL   *string    `json:"previewUrl"`
	TakenAt      *time.Time `json:"takenAt"`
	Width        *int32     `json:"width"`
	Height       *int32     `json:"height"`
	DurationMs   *int64     `json:"durationMs"`
	IsFavorite   bool       `json:"isFavorite"`
}

type AssetListResponse struct {
	Items      []AssetListItem `json:"items"`
	NextCursor *string         `json:"nextCursor"`
}

type AssetDetail struct {
	ID             string     `json:"id"`
	MediaType      string     `json:"mediaType"`
	MimeType       string     `json:"mimeType"`
	ObjectKey      string     `json:"objectKey"`
	ThumbnailKey   *string    `json:"thumbnailKey"`
	PreviewKey     *string    `json:"previewKey"`
	PosterFrameKey *string    `json:"posterFrameKey"`
	OriginalName   *string    `json:"originalFilename"`
	FileSizeBytes  int64      `json:"fileSizeBytes"`
	TakenAt        *time.Time `json:"takenAt"`
	Width          *int32     `json:"width"`
	Height         *int32     `json:"height"`
	DurationMs     *int64     `json:"durationMs"`
	Latitude       *float64   `json:"latitude"`
	Longitude      *float64   `json:"longitude"`
	City           *string    `json:"city"`
	IsFavorite     bool       `json:"isFavorite"`
	IsArchived     bool       `json:"isArchived"`
	IsTrashed      bool       `json:"isTrashed"`
	CreatedAt      time.Time  `json:"createdAt"`
	UpdatedAt      time.Time  `json:"updatedAt"`
}

type ReadURLResponse struct {
	URL string `json:"url"`
}
