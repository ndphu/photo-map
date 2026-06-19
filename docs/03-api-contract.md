# API contract

## Quy ước

- Base URL tùy môi trường, ví dụ `http://192.168.1.182:48080/`.
- API riêng tư dùng `Authorization: Bearer <JWT>`.
- Timestamp dùng RFC 3339 UTC nếu không ghi khác.
- JSON error thống nhất:

```json
{"error":{"code":"invalid_request","message":"human-readable message"}}
```

## Endpoint index

| Method | Path | Auth | Kết quả |
|---|---|---:|---|
| GET | `/health` | No | Health status |
| POST | `/auth/register` | No | JWT và user |
| POST | `/auth/login` | No | JWT và user |
| POST | `/devices/register` | Yes | Upsert device |
| GET | `/devices/me` | Yes | Devices của user |
| POST | `/upload-sessions` | Yes | Deduplicate hoặc session + PUT URLs |
| POST | `/upload-sessions/{id}/resume` | Yes | Completed asset hoặc refreshed URLs |
| PATCH | `/upload-sessions/{id}/status` | Yes | Session status |
| POST | `/upload-sessions/{id}/complete` | Yes | Asset ID |
| GET | `/assets` | Yes | Cursor-paginated timeline |
| GET | `/assets/{id}` | Yes | Asset detail |
| GET | `/assets/{id}/read-url` | Yes | Signed GET URL |
| PATCH | `/assets/{id}/favorite` | Yes | Updated asset |
| PATCH | `/assets/{id}/archive` | Yes | Updated asset |
| POST | `/assets/{id}/trash` | Yes | Updated asset |
| POST | `/assets/{id}/restore` | Yes | Updated asset |
| DELETE | `/assets/{id}` | Yes | `204` |
| GET | `/search` | Yes | Search result page |
| POST/GET/PATCH/DELETE | `/albums...` | Yes | Album operations |
| POST | `/maintenance/upload-sessions/cleanup` | Admin | Cleanup result |

## Authentication

### `POST /auth/register`

```json
{"email":"user@example.com","password":"password","displayName":"User"}
```

Password tối thiểu 8 ký tự. Thành công trả `201`.

### `POST /auth/login`

```json
{"email":"user@example.com","password":"password"}
```

Response chung:

```json
{
  "accessToken":"jwt",
  "user":{"id":"uuid","email":"user@example.com","displayName":"User"}
}
```

## Device

### `POST /devices/register`

```json
{"deviceName":"Pixel 8","platform":"android","deviceFingerprint":"stable-device-id"}
```

Upsert theo `(user_id, device_fingerprint)` và cập nhật `last_seen_at`.

### `GET /devices/me`

```json
{"devices":[{"id":"uuid","userId":"uuid","deviceName":"Pixel 8","platform":"android","deviceFingerprint":"stable-device-id","lastSeenAt":"2026-06-19T00:00:00Z"}]}
```

## Upload session

### `POST /upload-sessions`

```json
{
  "deviceId":"uuid",
  "localAssetId":"MediaStore:123",
  "mediaType":"image",
  "mimeType":"image/jpeg",
  "originalFilename":"IMG_1234.jpg",
  "fileSizeBytes":1234567,
  "expectedChecksumSha256":"64-char lowercase hex"
}
```

Response `status`:

- `already_uploaded`: device/local ID đã map tới asset; trả `asset`.
- `duplicate_found`: checksum đã tồn tại; backend tạo mapping và trả `asset`.
- `existing_session`: trả session đang active và presigned URLs.
- `created`: tạo session mới, trả `201`, session và presigned URLs.

```json
{
  "status":"created",
  "session":{
    "id":"uuid",
    "status":"created",
    "bucket":"gallery",
    "objectKey":"users/.../originals/...jpg",
    "thumbnailKey":"users/.../thumbs/...webp",
    "previewKey":"users/.../previews/...webp",
    "posterFrameKey":null,
    "expiresAt":"2026-06-19T01:00:00Z"
  },
  "uploadUrls":{"original":"https://...","thumbnail":"https://...","preview":"https://...","posterFrame":null}
}
```

### `POST /upload-sessions/{id}/resume`

- `completed`: trả existing `asset`.
- `resumed`: giữ nguyên object keys, gia hạn session nếu cần và trả fresh URLs.
- Session không thuộc user hoặc không tồn tại: `404`.

### `PATCH /upload-sessions/{id}/status`

```json
{"status":"uploading","errorMessage":null}
```

Transition hợp lệ: `created -> uploading`, `uploading -> uploaded`, và `created|uploading|uploaded -> failed`.

### `POST /upload-sessions/{id}/complete`

```json
{
  "checksumSha256":"64-char lowercase hex",
  "takenAt":"2026-06-17T10:00:00Z",
  "takenAtSource":"exif",
  "timezoneOffsetMinutes":420,
  "width":4032,
  "height":3024,
  "orientation":1,
  "durationMs":null,
  "latitude":10.123,
  "longitude":106.123,
  "cameraMake":"Google",
  "cameraModel":"Pixel 8",
  "software":"Android",
  "localCreatedAt":"2026-06-17T10:00:00Z",
  "localModifiedAt":"2026-06-17T10:00:00Z"
}
```

```json
{"assetId":"uuid","status":"completed"}
```

## Assets

### `GET /assets`

Query: `limit` (default 50, max 100), `cursor`, `mediaType`, `favorite`, `archived`, `trashed`, `city`, `from`, `to`.

Sort: `taken_at DESC NULLS LAST, id DESC`. Cursor là base64url JSON `{ "takenAt": "...", "id": "uuid" }`.

```json
{
  "items":[{
    "id":"uuid","mediaType":"image","mimeType":"image/jpeg",
    "thumbnailKey":"...","previewKey":"...",
    "thumbnailUrl":"https://...","previewUrl":"https://...",
    "takenAt":"2026-06-17T10:00:00Z","width":4032,"height":3024,
    "durationMs":null,"isFavorite":false
  }],
  "nextCursor":null
}
```

### Các mutation

- `GET /assets/{id}/read-url?variant=original|thumbnail|preview|posterFrame`
- `PATCH /assets/{id}/favorite` body `{ "isFavorite": true }`
- `PATCH /assets/{id}/archive` body `{ "isArchived": true }`
- `POST /assets/{id}/trash`
- `POST /assets/{id}/restore`
- `DELETE /assets/{id}` xóa R2 trước, sau đó mới xóa DB.

## Search

`GET /search?q=motorcycle&limit=50&cursor=...` tìm trên `asset_search_metadata.search_vector`, chỉ trả asset của user hiện tại và chưa trash. Shape response giống `GET /assets`.

## Albums

- `POST /albums`: `{ "name":"Trip", "description":"Optional" }`
- `GET /albums`, `GET /albums/{id}`
- `PATCH /albums/{id}`: các field `name`, `description`, `coverAssetId`, `isArchived`.
- `DELETE /albums/{id}`
- `POST /albums/{id}/assets`: `{ "assetId":"uuid", "sortOrder":null }`
- `DELETE /albums/{id}/assets/{assetId}`
- `GET /albums/{id}/assets`

Album và asset đều phải thuộc authenticated user.

## Maintenance

`POST /maintenance/upload-sessions/cleanup` yêu cầu email thuộc `ADMIN_EMAILS`.

```json
{"dryRun":true,"olderThanHours":24,"limit":100}
```

```json
{"dryRun":true,"scanned":10,"deletedObjects":["users/..."],"expiredSessions":["uuid"],"errors":[]}
```
