package service

import (
	"context"
	"testing"
	"time"

	"photo-map-app/backend/internal/db/sqlc"
)

func TestBuildAssetSnapshot(t *testing.T) {
	now := time.Date(2026, time.June, 19, 10, 0, 0, 0, time.UTC)
	name := "IMG_001.jpg"
	thumbnail := "users/user/thumbs/IMG_001.webp"
	preview := "users/user/previews/IMG_001.webp"
	favorite := true
	archived := false
	trashed := false
	asset := sqlc.Asset{
		ID: "asset-id", UserID: "user-id", MediaType: "image", MimeType: "image/jpeg",
		OriginalFilename: &name, FileSizeBytes: 123456, ChecksumSha256: "checksum",
		ThumbnailKey: &thumbnail, PreviewKey: &preview, IsFavorite: &favorite,
		IsArchived: &archived, IsTrashed: &trashed, UploadedAt: now, UpdatedAt: now,
	}

	snapshot := BuildAssetSnapshot(asset)

	if snapshot.ID != asset.ID || snapshot.MediaType != asset.MediaType || snapshot.ChecksumSha256 != asset.ChecksumSha256 {
		t.Fatalf("snapshot identity fields do not match asset: %#v", snapshot)
	}
	if snapshot.ThumbnailKey == nil || *snapshot.ThumbnailKey != thumbnail {
		t.Fatalf("thumbnail key was not preserved: %#v", snapshot.ThumbnailKey)
	}
	if !snapshot.IsFavorite || snapshot.IsArchived || snapshot.IsTrashed {
		t.Fatalf("snapshot flags do not match asset: %#v", snapshot)
	}
	if !snapshot.UploadedAt.Equal(now) || !snapshot.UpdatedAt.Equal(now) {
		t.Fatalf("snapshot timestamps do not match asset")
	}
}

func TestMapDeleteAssetChangeReturnsNullAsset(t *testing.T) {
	service := &AssetService{}
	item, err := service.mapAssetChange(context.Background(), sqlc.AssetChange{
		ChangeID: 10, UserID: "user-id", AssetID: "asset-id",
		ChangeType: assetChangeDelete, ChangedAt: time.Now().UTC(), AssetSnapshot: nil,
	})
	if err != nil {
		t.Fatalf("map delete change: %v", err)
	}
	if item.Asset != nil {
		t.Fatalf("delete change must have a nil asset")
	}
}

func TestNormalizeAssetChangesLimit(t *testing.T) {
	if got := normalizeAssetChangesLimit(0); got != defaultAssetChangesLimit {
		t.Fatalf("default limit = %d", got)
	}
	if got := normalizeAssetChangesLimit(2000); got != maxAssetChangesLimit {
		t.Fatalf("maximum limit = %d", got)
	}
}
