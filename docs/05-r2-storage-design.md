# Cloudflare R2 storage design

## Nguyên tắc

- Backend không proxy upload và không lưu media trên local filesystem.
- Android upload trực tiếp bằng presigned PUT URL.
- Backend giữ R2 access key; client không bao giờ nhận credential dài hạn.
- PostgreSQL lưu object key và metadata, không lưu binary media.

## Endpoint và client

AWS SDK Go v2 S3 client sử dụng:

```text
https://<R2_ACCOUNT_ID>.r2.cloudflarestorage.com
region: auto
path-style: true
```

Các operation backend:

- `GeneratePresignedPutURL`
- `GeneratePresignedGetURL`
- `HeadObject`
- `DeleteObjects` theo batch tối đa 1000 keys

## Object key convention

```text
users/{userId}/originals/{yyyy}/{mm}/{assetUuid}.{ext}
users/{userId}/thumbs/{yyyy}/{mm}/{assetUuid}_320.webp
users/{userId}/previews/{yyyy}/{mm}/{assetUuid}_1600.webp
users/{userId}/posters/{yyyy}/{mm}/{assetUuid}_poster.webp
```

- `assetUuid` được sinh một lần khi tạo upload session.
- Resume không tạo key mới.
- Poster frame chỉ dùng cho video.
- Thumbnail và preview chuẩn hóa sang WebP.
- Key chứa `userId` để phân vùng ownership về mặt namespace; authorization vẫn được enforce bằng DB/JWT.

## Presigned URL

- Upload expiry: `R2_PRESIGNED_UPLOAD_EXPIRES_SECONDS`.
- Read expiry: `R2_PRESIGNED_READ_EXPIRES_SECONDS`.
- PUT URL được ký cùng content type dự kiến.
- URL hết hạn có thể refresh qua `/upload-sessions/{id}/resume` mà không đổi key.
- Gallery không trả public object URL; backend sinh signed GET URL cho thumbnail/preview.

## Complete upload

Backend khóa upload session, kiểm tra ownership và gọi `HeadObject` cho original. Original phải tồn tại và kích thước phải khớp nếu R2 trả content length. Thumbnail/preview/poster thiếu không chặn complete; derivative có thể rebuild sau.

## Hard delete

1. Load asset theo `id + authenticated user_id`.
2. Thu thập original, thumbnail, preview, poster keys không rỗng.
3. Xóa object R2.
4. Chỉ xóa row PostgreSQL sau khi R2 delete thành công.

## Orphan cleanup

Candidate session phải thỏa toàn bộ:

- status thuộc `created`, `uploading`, `uploaded`, `failed`, `expired`;
- `asset_id IS NULL`;
- `expires_at < now() - olderThan`.

Trước khi xóa, service khóa lại session bằng `FOR UPDATE`, revalidate status/expiry và kiểm tra từng key không xuất hiện trong `assets.object_key`, `thumbnail_key`, `preview_key`, hoặc `poster_frame_key`.

- Dry-run chỉ trả danh sách dự kiến và ghi audit.
- Live run xóa R2, đánh dấu session `expired`, rồi ghi audit.
- Completed session, session có `asset_id`, hoặc key được asset tham chiếu luôn bị bỏ qua.
- Cleanup không bao giờ xóa row `assets`.

CLI:

```bash
go run ./cmd/maintenance cleanup-upload-sessions --dry-run --older-than=24h --limit=100
```

## CORS tối thiểu cho R2

R2 bucket cần cho phép Android PUT tới presigned URL với content types được ký. Không mở public read. Nếu có web client sau này, cấu hình origin cụ thể; không dùng wildcard origin kèm credential.
