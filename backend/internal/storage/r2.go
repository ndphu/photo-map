package storage

import (
	"context"
	"errors"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/aws/aws-sdk-go-v2/service/s3/types"

	"photo-map-app/backend/internal/config"
)

const r2Region = "auto"
const deleteObjectsBatchSize = 1000

type StorageService struct {
	bucket        string
	client        *s3.Client
	presignClient *s3.PresignClient
}

type ObjectInfo struct {
	ContentLength int64
	ContentType   string
	ETag          string
}

func NewStorageService(cfg config.Config) *StorageService {
	awsConfig := aws.Config{
		Region: r2Region,
		Credentials: credentials.NewStaticCredentialsProvider(
			cfg.R2AccessKeyID,
			cfg.R2SecretAccessKey,
			"",
		),
	}

	client := s3.NewFromConfig(awsConfig, func(options *s3.Options) {
		options.BaseEndpoint = aws.String(cfg.R2Endpoint())
		options.UsePathStyle = true
	})

	return &StorageService{
		bucket:        cfg.R2Bucket,
		client:        client,
		presignClient: s3.NewPresignClient(client),
	}
}

func (service *StorageService) Bucket() string {
	return service.bucket
}

func (service *StorageService) GeneratePresignedPutURL(ctx context.Context, key string, contentType string, expires time.Duration) (string, error) {
	request, err := service.presignClient.PresignPutObject(ctx, &s3.PutObjectInput{
		Bucket:      aws.String(service.bucket),
		Key:         aws.String(key),
		ContentType: aws.String(contentType),
	}, s3.WithPresignExpires(expires))
	if err != nil {
		return "", err
	}

	return request.URL, nil
}

func (service *StorageService) GeneratePresignedGetURL(ctx context.Context, key string, expires time.Duration) (string, error) {
	request, err := service.presignClient.PresignGetObject(ctx, &s3.GetObjectInput{
		Bucket: aws.String(service.bucket),
		Key:    aws.String(key),
	}, s3.WithPresignExpires(expires))
	if err != nil {
		return "", err
	}

	return request.URL, nil
}

func (service *StorageService) HeadObject(ctx context.Context, key string) (ObjectInfo, error) {
	output, err := service.client.HeadObject(ctx, &s3.HeadObjectInput{
		Bucket: aws.String(service.bucket),
		Key:    aws.String(key),
	})
	if err != nil {
		return ObjectInfo{}, err
	}

	info := ObjectInfo{}
	if output.ContentLength != nil {
		info.ContentLength = *output.ContentLength
	}
	if output.ContentType != nil {
		info.ContentType = *output.ContentType
	}
	if output.ETag != nil {
		info.ETag = *output.ETag
	}

	return info, nil
}

func (service *StorageService) DeleteObjects(ctx context.Context, keys []string) error {
	for start := 0; start < len(keys); start += deleteObjectsBatchSize {
		end := start + deleteObjectsBatchSize
		if end > len(keys) {
			end = len(keys)
		}

		objects := make([]types.ObjectIdentifier, 0, end-start)
		for _, key := range keys[start:end] {
			objects = append(objects, types.ObjectIdentifier{Key: aws.String(key)})
		}

		output, err := service.client.DeleteObjects(ctx, &s3.DeleteObjectsInput{
			Bucket: aws.String(service.bucket),
			Delete: &types.Delete{Objects: objects},
		})
		if err != nil {
			return err
		}
		if len(output.Errors) > 0 {
			return errors.New("delete objects failed")
		}
	}

	return nil
}
