package service

import (
	"bytes"
	"errors"
	"fmt"
	"image"
	"path"
	"strings"

	webp "github.com/HugoSmits86/nativewebp"
	"golang.org/x/image/draw"
)

const (
	thumbnailMaxDimension = 320
	previewMaxDimension   = 1600
	maxPreviewPixels      = 20_000_000
	derivativeVersion     = "v2"
)

func repairDerivativeImages(source []byte, orientation int16) ([]byte, []byte, error) {
	config, err := webp.DecodeConfig(bytes.NewReader(source))
	if err != nil {
		return nil, nil, fmt.Errorf("decode preview metadata: %w", err)
	}
	if config.Width < 1 || config.Height < 1 || int64(config.Width)*int64(config.Height) > maxPreviewPixels {
		return nil, nil, fmt.Errorf("preview dimensions %dx%d are outside the supported range", config.Width, config.Height)
	}

	decoded, err := webp.Decode(bytes.NewReader(source))
	if err != nil {
		return nil, nil, fmt.Errorf("decode preview: %w", err)
	}
	normalized, err := normalizeOrientation(decoded, orientation)
	if err != nil {
		return nil, nil, err
	}

	preview := resizeMaxDimension(normalized, previewMaxDimension)
	thumbnail := resizeMaxDimension(preview, thumbnailMaxDimension)
	previewBytes, err := encodeWebP(preview)
	if err != nil {
		return nil, nil, fmt.Errorf("encode preview: %w", err)
	}
	thumbnailBytes, err := encodeWebP(thumbnail)
	if err != nil {
		return nil, nil, fmt.Errorf("encode thumbnail: %w", err)
	}
	return thumbnailBytes, previewBytes, nil
}

func normalizeOrientation(source image.Image, orientation int16) (*image.NRGBA, error) {
	if orientation < 1 || orientation > 8 {
		return nil, fmt.Errorf("unsupported orientation %d", orientation)
	}

	bounds := source.Bounds()
	width := bounds.Dx()
	height := bounds.Dy()
	if width < 1 || height < 1 {
		return nil, errors.New("source image is empty")
	}
	destinationWidth, destinationHeight := width, height
	if orientation >= 5 {
		destinationWidth, destinationHeight = height, width
	}

	destination := image.NewNRGBA(image.Rect(0, 0, destinationWidth, destinationHeight))
	for y := 0; y < destinationHeight; y++ {
		for x := 0; x < destinationWidth; x++ {
			sourceX, sourceY := orientedSourcePoint(x, y, width, height, orientation)
			destination.Set(x, y, source.At(bounds.Min.X+sourceX, bounds.Min.Y+sourceY))
		}
	}
	return destination, nil
}

func orientedSourcePoint(x int, y int, width int, height int, orientation int16) (int, int) {
	switch orientation {
	case 2:
		return width - 1 - x, y
	case 3:
		return width - 1 - x, height - 1 - y
	case 4:
		return x, height - 1 - y
	case 5:
		return y, x
	case 6:
		return y, height - 1 - x
	case 7:
		return width - 1 - y, height - 1 - x
	case 8:
		return width - 1 - y, x
	default:
		return x, y
	}
}

func resizeMaxDimension(source image.Image, maxDimension int) image.Image {
	bounds := source.Bounds()
	width := bounds.Dx()
	height := bounds.Dy()
	largestDimension := max(width, height)
	if largestDimension <= maxDimension {
		return source
	}

	ratio := float64(maxDimension) / float64(largestDimension)
	destinationWidth := max(1, int(float64(width)*ratio))
	destinationHeight := max(1, int(float64(height)*ratio))
	destination := image.NewNRGBA(image.Rect(0, 0, destinationWidth, destinationHeight))
	draw.CatmullRom.Scale(destination, destination.Bounds(), source, bounds, draw.Over, nil)
	return destination
}

func encodeWebP(source image.Image) ([]byte, error) {
	var output bytes.Buffer
	err := webp.Encode(&output, source, &webp.Options{CompressionLevel: webp.DefaultCompression})
	if err != nil {
		return nil, err
	}
	return output.Bytes(), nil
}

func versionDerivativeKey(key string, dimension int) (string, error) {
	trimmedKey := strings.TrimSpace(key)
	if trimmedKey == "" {
		return "", errors.New("derivative key is empty")
	}
	extension := path.Ext(trimmedKey)
	stem := strings.TrimSuffix(trimmedKey, extension)
	dimensionSuffix := fmt.Sprintf("_%d", dimension)
	if !strings.HasSuffix(stem, dimensionSuffix) {
		return "", fmt.Errorf("derivative key %q does not end with %s", trimmedKey, dimensionSuffix)
	}
	base := strings.TrimSuffix(stem, dimensionSuffix)
	if strings.HasSuffix(base, "_"+derivativeVersion) {
		return "", fmt.Errorf("derivative key %q is already versioned", trimmedKey)
	}
	return fmt.Sprintf("%s_%s%s%s", base, derivativeVersion, dimensionSuffix, extension), nil
}
