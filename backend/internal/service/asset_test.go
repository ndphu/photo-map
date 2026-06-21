package service

import (
	"testing"
	"time"

	"photo-map-app/backend/internal/db/sqlc"
)

func TestMapAssetDetailIncludesExtendedMetadata(t *testing.T) {
	takenAtSource := "exif"
	timezoneOffset := int16(420)
	orientation := int16(1)
	country := "Vietnam"
	cameraModel := "Pixel 8"
	now := time.Now().UTC()

	detail := mapAssetDetail(sqlc.Asset{
		ID:                    "asset-id",
		MediaType:             "image",
		MimeType:              "image/jpeg",
		ObjectKey:             "private-key",
		Bucket:                "bucket",
		FileSizeBytes:         123,
		ChecksumSha256:        "checksum",
		TakenAtSource:         &takenAtSource,
		TimezoneOffsetMinutes: &timezoneOffset,
		Orientation:           &orientation,
		Country:               &country,
		CameraModel:           &cameraModel,
		UploadedAt:            now,
		CreatedAt:             now,
		UpdatedAt:             now,
	})

	if detail.ChecksumSha256 != "checksum" {
		t.Fatalf("expected checksum metadata, got %q", detail.ChecksumSha256)
	}
	if detail.TakenAtSource == nil || *detail.TakenAtSource != takenAtSource {
		t.Fatal("expected takenAtSource metadata")
	}
	if detail.Country == nil || *detail.Country != country {
		t.Fatal("expected country metadata")
	}
	if detail.CameraModel == nil || *detail.CameraModel != cameraModel {
		t.Fatal("expected camera model metadata")
	}
}
