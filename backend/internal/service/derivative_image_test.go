package service

import (
	"bytes"
	"context"
	"image"
	"image/color"
	"reflect"
	"testing"

	webp "github.com/HugoSmits86/nativewebp"
)

func TestRebuildDerivativesRejectsAutoDryRun(t *testing.T) {
	maintenanceService := MaintenanceService{}
	result, err := maintenanceService.RebuildDerivatives(context.Background(), RebuildDerivativesParams{
		DryRun: true,
		Auto:   true,
	})
	if err == nil {
		t.Fatal("RebuildDerivatives() error = nil")
	}
	if !result.Auto || !result.DryRun {
		t.Fatalf("RebuildDerivatives() result = %#v", result)
	}
}

func TestRepairDerivativeImagesProducesWebPVariants(t *testing.T) {
	source, err := encodeWebP(numberedImage(2, 3))
	if err != nil {
		t.Fatalf("encodeWebP() error = %v", err)
	}

	thumbnail, preview, err := repairDerivativeImages(source, 6)
	if err != nil {
		t.Fatalf("repairDerivativeImages() error = %v", err)
	}
	for variant, data := range map[string][]byte{"thumbnail": thumbnail, "preview": preview} {
		decoded, err := webp.Decode(bytes.NewReader(data))
		if err != nil {
			t.Fatalf("decode %s error = %v", variant, err)
		}
		if decoded.Bounds().Dx() != 3 || decoded.Bounds().Dy() != 2 {
			t.Fatalf("%s bounds = %v, want 3x2", variant, decoded.Bounds())
		}
	}
}

func TestNormalizeOrientation(t *testing.T) {
	source := numberedImage(2, 3)
	tests := []struct {
		orientation int16
		width       int
		height      int
		pixels      []uint8
	}{
		{orientation: 1, width: 2, height: 3, pixels: []uint8{1, 2, 3, 4, 5, 6}},
		{orientation: 2, width: 2, height: 3, pixels: []uint8{2, 1, 4, 3, 6, 5}},
		{orientation: 3, width: 2, height: 3, pixels: []uint8{6, 5, 4, 3, 2, 1}},
		{orientation: 4, width: 2, height: 3, pixels: []uint8{5, 6, 3, 4, 1, 2}},
		{orientation: 5, width: 3, height: 2, pixels: []uint8{1, 3, 5, 2, 4, 6}},
		{orientation: 6, width: 3, height: 2, pixels: []uint8{5, 3, 1, 6, 4, 2}},
		{orientation: 7, width: 3, height: 2, pixels: []uint8{6, 4, 2, 5, 3, 1}},
		{orientation: 8, width: 3, height: 2, pixels: []uint8{2, 4, 6, 1, 3, 5}},
	}

	for _, test := range tests {
		t.Run(string(rune('0'+test.orientation)), func(t *testing.T) {
			result, err := normalizeOrientation(source, test.orientation)
			if err != nil {
				t.Fatalf("normalizeOrientation() error = %v", err)
			}
			if result.Bounds().Dx() != test.width || result.Bounds().Dy() != test.height {
				t.Fatalf("bounds = %v, want %dx%d", result.Bounds(), test.width, test.height)
			}
			if pixels := redChannel(result); !reflect.DeepEqual(pixels, test.pixels) {
				t.Fatalf("pixels = %v, want %v", pixels, test.pixels)
			}
		})
	}
}

func TestNormalizeOrientationRejectsUnsupportedValue(t *testing.T) {
	if _, err := normalizeOrientation(numberedImage(1, 1), 9); err == nil {
		t.Fatal("normalizeOrientation() error = nil, want unsupported orientation error")
	}
}

func TestResizeMaxDimension(t *testing.T) {
	source := numberedImage(800, 400)
	resized := resizeMaxDimension(source, 320)
	if resized.Bounds().Dx() != 320 || resized.Bounds().Dy() != 160 {
		t.Fatalf("resized bounds = %v, want 320x160", resized.Bounds())
	}

	small := numberedImage(100, 50)
	if result := resizeMaxDimension(small, 320); result != small {
		t.Fatal("small image should not be upscaled")
	}
}

func TestVersionDerivativeKey(t *testing.T) {
	tests := []struct {
		name      string
		key       string
		dimension int
		want      string
		wantError bool
	}{
		{name: "preview", key: "users/u/previews/a_1600.webp", dimension: 1600, want: "users/u/previews/a_v2_1600.webp"},
		{name: "thumbnail", key: "users/u/thumbs/a_320.webp", dimension: 320, want: "users/u/thumbs/a_v2_320.webp"},
		{name: "unexpected suffix", key: "users/u/previews/a.webp", dimension: 1600, wantError: true},
		{name: "already versioned", key: "users/u/previews/a_v2_1600.webp", dimension: 1600, wantError: true},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			result, err := versionDerivativeKey(test.key, test.dimension)
			if test.wantError {
				if err == nil {
					t.Fatal("versionDerivativeKey() error = nil")
				}
				return
			}
			if err != nil {
				t.Fatalf("versionDerivativeKey() error = %v", err)
			}
			if result != test.want {
				t.Fatalf("versionDerivativeKey() = %q, want %q", result, test.want)
			}
		})
	}
}

func TestNormalizeDerivativeParallel(t *testing.T) {
	tests := []struct {
		parallel  int
		want      int
		wantError bool
	}{
		{parallel: 0, want: 1},
		{parallel: 1, want: 1},
		{parallel: 32, want: 32},
		{parallel: -1, wantError: true},
		{parallel: 33, wantError: true},
	}
	for _, test := range tests {
		result, err := normalizeDerivativeParallel(test.parallel)
		if test.wantError {
			if err == nil {
				t.Fatalf("normalizeDerivativeParallel(%d) error = nil", test.parallel)
			}
			continue
		}
		if err != nil {
			t.Fatalf("normalizeDerivativeParallel(%d) error = %v", test.parallel, err)
		}
		if result != test.want {
			t.Fatalf("normalizeDerivativeParallel(%d) = %d, want %d", test.parallel, result, test.want)
		}
	}
}

func numberedImage(width int, height int) *image.NRGBA {
	result := image.NewNRGBA(image.Rect(0, 0, width, height))
	value := uint8(1)
	for y := 0; y < height; y++ {
		for x := 0; x < width; x++ {
			result.SetNRGBA(x, y, color.NRGBA{R: value, A: 255})
			value++
		}
	}
	return result
}

func redChannel(source image.Image) []uint8 {
	bounds := source.Bounds()
	result := make([]uint8, 0, bounds.Dx()*bounds.Dy())
	for y := bounds.Min.Y; y < bounds.Max.Y; y++ {
		for x := bounds.Min.X; x < bounds.Max.X; x++ {
			red, _, _, _ := source.At(x, y).RGBA()
			result = append(result, uint8(red>>8))
		}
	}
	return result
}
