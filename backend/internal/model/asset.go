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
	ID                    string     `json:"id"`
	MediaType             string     `json:"mediaType"`
	MimeType              string     `json:"mimeType"`
	ObjectKey             string     `json:"objectKey"`
	ThumbnailKey          *string    `json:"thumbnailKey"`
	PreviewKey            *string    `json:"previewKey"`
	PosterFrameKey        *string    `json:"posterFrameKey"`
	OriginalName          *string    `json:"originalFilename"`
	FileSizeBytes         int64      `json:"fileSizeBytes"`
	ChecksumSha256        string     `json:"checksumSha256"`
	TakenAt               *time.Time `json:"takenAt"`
	TakenAtSource         *string    `json:"takenAtSource"`
	TimezoneOffsetMinutes *int16     `json:"timezoneOffsetMinutes"`
	Width                 *int32     `json:"width"`
	Height                *int32     `json:"height"`
	Orientation           *int16     `json:"orientation"`
	DurationMs            *int64     `json:"durationMs"`
	Latitude              *float64   `json:"latitude"`
	Longitude             *float64   `json:"longitude"`
	Country               *string    `json:"country"`
	Region                *string    `json:"region"`
	City                  *string    `json:"city"`
	PlaceName             *string    `json:"placeName"`
	CameraMake            *string    `json:"cameraMake"`
	CameraModel           *string    `json:"cameraModel"`
	Software              *string    `json:"software"`
	IsFavorite            bool       `json:"isFavorite"`
	IsArchived            bool       `json:"isArchived"`
	IsHidden              bool       `json:"isHidden"`
	IsTrashed             bool       `json:"isTrashed"`
	TrashedAt             *time.Time `json:"trashedAt"`
	UploadedAt            time.Time  `json:"uploadedAt"`
	CreatedAt             time.Time  `json:"createdAt"`
	UpdatedAt             time.Time  `json:"updatedAt"`
}

type ReadURLResponse struct {
	URL string `json:"url"`
}

type AssetSnapshot struct {
	ID                    string     `json:"id"`
	MediaType             string     `json:"mediaType"`
	MimeType              string     `json:"mimeType"`
	OriginalFilename      *string    `json:"originalFilename"`
	FileSizeBytes         int64      `json:"fileSizeBytes"`
	ChecksumSha256        string     `json:"checksumSha256"`
	ThumbnailKey          *string    `json:"thumbnailKey"`
	PreviewKey            *string    `json:"previewKey"`
	PosterFrameKey        *string    `json:"posterFrameKey"`
	TakenAt               *time.Time `json:"takenAt"`
	TakenAtSource         *string    `json:"takenAtSource"`
	TimezoneOffsetMinutes *int16     `json:"timezoneOffsetMinutes"`
	Width                 *int32     `json:"width"`
	Height                *int32     `json:"height"`
	DurationMs            *int64     `json:"durationMs"`
	Orientation           *int16     `json:"orientation"`
	Latitude              *float64   `json:"latitude"`
	Longitude             *float64   `json:"longitude"`
	Country               *string    `json:"country"`
	Region                *string    `json:"region"`
	City                  *string    `json:"city"`
	PlaceName             *string    `json:"placeName"`
	CameraMake            *string    `json:"cameraMake"`
	CameraModel           *string    `json:"cameraModel"`
	Software              *string    `json:"software"`
	IsFavorite            bool       `json:"isFavorite"`
	IsArchived            bool       `json:"isArchived"`
	IsTrashed             bool       `json:"isTrashed"`
	UploadedAt            time.Time  `json:"uploadedAt"`
	UpdatedAt             time.Time  `json:"updatedAt"`
}

type AssetChangeAsset struct {
	AssetSnapshot
	ThumbnailURL   *string `json:"thumbnailUrl"`
	PreviewURL     *string `json:"previewUrl"`
	PosterFrameURL *string `json:"posterFrameUrl"`
}

type AssetChangeItem struct {
	ChangeID   int64             `json:"changeId"`
	AssetID    string            `json:"assetId"`
	ChangeType string            `json:"changeType"`
	ChangedAt  time.Time         `json:"changedAt"`
	Asset      *AssetChangeAsset `json:"asset"`
}

type AssetChangesResponse struct {
	Items        []AssetChangeItem `json:"items"`
	NextCursor   int64             `json:"nextCursor"`
	HasMore      bool              `json:"hasMore"`
	ServerCursor int64             `json:"serverCursor"`
	ServerTime   time.Time         `json:"serverTime"`
}
