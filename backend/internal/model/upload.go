package model

import "time"

type UploadURLs struct {
	Original    string  `json:"original"`
	Thumbnail   string  `json:"thumbnail"`
	Preview     string  `json:"preview"`
	PosterFrame *string `json:"posterFrame"`
}

type UploadSessionResponse struct {
	ID             string     `json:"id"`
	Bucket         string     `json:"bucket"`
	ObjectKey      string     `json:"objectKey"`
	ThumbnailKey   string     `json:"thumbnailKey"`
	PreviewKey     string     `json:"previewKey"`
	PosterFrameKey *string    `json:"posterFrameKey"`
	UploadURLs     UploadURLs `json:"uploadUrls"`
	ExpiresAt      time.Time  `json:"expiresAt"`
}

type CompleteUploadResponse struct {
	AssetID string `json:"assetId"`
	Status  string `json:"status"`
}
