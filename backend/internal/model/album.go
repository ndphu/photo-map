package model

import "time"

type Album struct {
	ID           string    `json:"id"`
	Name         string    `json:"name"`
	Description  *string   `json:"description"`
	CoverAssetID *string   `json:"coverAssetId"`
	IsArchived   bool      `json:"isArchived"`
	CreatedAt    time.Time `json:"createdAt"`
	UpdatedAt    time.Time `json:"updatedAt"`
}

type AlbumListResponse struct {
	Items []Album `json:"items"`
}
