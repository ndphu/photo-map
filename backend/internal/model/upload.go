package model

import "time"

type UploadURLs struct {
	Original    string  `json:"original"`
	Thumbnail   string  `json:"thumbnail"`
	Preview     string  `json:"preview"`
	PosterFrame *string `json:"posterFrame"`
}

type UploadSessionDetails struct {
	ID             string    `json:"id"`
	Status         string    `json:"status"`
	Bucket         string    `json:"bucket"`
	ObjectKey      string    `json:"objectKey"`
	ThumbnailKey   string    `json:"thumbnailKey"`
	PreviewKey     string    `json:"previewKey"`
	PosterFrameKey *string   `json:"posterFrameKey"`
	ExpiresAt      time.Time `json:"expiresAt"`
}

type UploadSessionStatusResponse struct {
	Status       string  `json:"status"`
	ErrorMessage *string `json:"errorMessage,omitempty"`
}

type UploadSessionResponse struct {
	Status     string                `json:"status"`
	Asset      *AssetDetail          `json:"asset,omitempty"`
	Session    *UploadSessionDetails `json:"session,omitempty"`
	UploadURLs *UploadURLs           `json:"uploadUrls,omitempty"`
}

type CompleteUploadResponse struct {
	AssetID string `json:"assetId"`
	Status  string `json:"status"`
}
