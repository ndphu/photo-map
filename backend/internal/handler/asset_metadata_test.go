package handler

import "testing"

func TestValidAssetMetadataCoordinates(t *testing.T) {
	latitude := 10.123
	longitude := 106.123
	if !validAssetMetadata(replaceAssetMetadataRequest{Latitude: &latitude, Longitude: &longitude}) {
		t.Fatal("expected valid coordinate pair")
	}

	if validAssetMetadata(replaceAssetMetadataRequest{Latitude: &latitude}) {
		t.Fatal("expected partial coordinate pair to be rejected")
	}

	invalidLatitude := 91.0
	if validAssetMetadata(replaceAssetMetadataRequest{
		Latitude: &invalidLatitude, Longitude: &longitude,
	}) {
		t.Fatal("expected out-of-range latitude to be rejected")
	}
}

func TestValidAssetMetadataEnumsAndRanges(t *testing.T) {
	source := "video_metadata"
	timezone := int16(420)
	orientation := int16(1)
	if !validAssetMetadata(replaceAssetMetadataRequest{
		TakenAtSource: &source, TimezoneOffsetMinutes: &timezone, Orientation: &orientation,
	}) {
		t.Fatal("expected valid metadata fields")
	}

	invalidSource := "device"
	if validAssetMetadata(replaceAssetMetadataRequest{TakenAtSource: &invalidSource}) {
		t.Fatal("expected unknown takenAtSource to be rejected")
	}
}
