# Codex prompts

Tài liệu này tổng hợp các prompt nghiệp vụ đã dùng để xây project. Đây không phải system/developer prompt nội bộ. Có thể dùng từng prompt độc lập, nhưng Codex phải đọc code hiện tại trước khi sửa và giữ backward compatibility trừ khi prompt yêu cầu breaking change.

## 1. Backend MVP

```text
Build a Golang backend MVP for a private cloud photo/video gallery.

Use Go, Gin, PostgreSQL, pgxpool, sqlc, golang-migrate, JWT, bcrypt and AWS SDK Go v2 for Cloudflare R2. Backend stores only metadata; Android uploads directly to R2 using presigned URLs. Implement env config, pgxpool, graceful shutdown, consistent JSON errors, request logging, auth middleware placeholder and GET /health. Keep code clean and compilable.
```

## 2. Auth và devices

```text
Continue the existing backend. Implement POST /auth/register, POST /auth/login, POST /devices/register and GET /devices/me. Hash passwords with bcrypt. JWT contains sub, email, iat and exp. Protect device routes and store authenticated user ID in Gin context. Upsert device by user_id + device_fingerprint and update last_seen_at. Never return password_hash.
```

## 3. R2 và upload sessions

```text
Implement Cloudflare R2 storage using AWS SDK Go v2. Add presigned PUT/GET, HEAD and batch delete. Use user-scoped original/thumb/preview/poster object keys. Add POST /upload-sessions and POST /upload-sessions/{id}/complete. Complete must lock the session, validate ownership, HEAD original, insert asset, upsert device_assets, mark completed and commit. Never upload media through backend.
```

## 4. Assets, albums và search

```text
Implement authenticated asset APIs with base64url cursor pagination sorted by taken_at DESC NULLS LAST, id DESC. Return signed thumbnail and preview URLs. Add detail, read-url, favorite, archive, trash, restore and hard delete. Hard delete removes R2 objects before DB row. Add album CRUD/membership and full-text search over stored labels_text, ocr_text and ai_caption. Scope every query by authenticated user_id.
```

## 5. Android app

```text
Build an Android app using Kotlin, Jetpack Compose, MVVM, Retrofit, Room, WorkManager, MediaStore, Coil and Coroutines/Flow. Implement auth, secure JWT storage, device registration, media permissions, MediaStore scan, local sync state, direct presigned uploads, gallery timeline, detail and settings. Extract MediaStore/EXIF metadata. Failed uploads must be retryable.
```

## 6. Parallel và background upload

```text
Refactor Android sync to upload assets concurrently. Use preset concurrency 8/16/32/64/128, default 8, and allow uploads to be paused. Add optional background sync and Wi-Fi-only settings. Scan every hour with periodic WorkManager, use unique work, foreground dataSync notification and reset interrupted UPLOADING rows. Stream original from MediaStore and do not swallow CancellationException.
```

## 7. Reliable Sync v2 - Idempotent Upload

```text
Before creating an upload session, check device_assets by user/device/local ID, then assets by checksum, then active upload sessions. Return statuses already_uploaded, duplicate_found, existing_session or created. Complete must be transactional and return an existing asset on duplicate checksum or repeated completion. Android sends expected checksum and skips upload when backend confirms an existing asset.
```

## 8. Reliable Sync v2 - Retry and Resume

```text
Add POST /upload-sessions/{id}/resume and PATCH /upload-sessions/{id}/status. Resume preserves object keys and refreshes presigned URLs. Android persists uploadSessionId, resumes after process death, retries network failures with exponential backoff, refreshes expired PUT URLs, clears invalid sessions and only marks uploaded after backend confirmation.
```

## 9. Reliable Sync v2 - Orphan Cleanup

```text
Implement CleanupExpiredUploadSessions(ctx, dryRun, olderThan). Select expired incomplete sessions with no asset, verify every object key is not referenced by assets, delete R2 objects only in live mode and mark sessions expired. Add admin endpoint controlled by ADMIN_EMAILS, reusable maintenance CLI, audit logs and Android recovery when an old session no longer exists. Safety is more important than aggressive cleanup.
```

## 10. Documentation maintenance prompt

```text
Read the current backend migrations, handlers, services and Android sync implementation. Update architecture, database schema, API contract, Android sync, R2 design, roadmap and Codex prompt documents. Clearly distinguish implemented behavior from roadmap items. Do not document a contract that is not present in code.
```

## 11. Cloud metadata replication

```text
Add GET /assets/changes backed by an ordered per-user asset_changes log. Android stores remote_assets and a durable cursor in Room, applies each page and cursor transactionally, renders Gallery from Room, and preserves cached rows when refresh fails.
```

## 12. Android metadata mutation queue

```text
Persist favorite, archive, trash, restore and hard-delete operations in remote_asset_pending_ops. Update remote_assets optimistically, coalesce compatible operations, push sequentially with WorkManager retry, preserve operations on 401, and reconcile final state through GET /assets/changes.
```

## 13. Gallery organization and actions

```text
Implement date headers, All/Photos/Videos/Favorites filters, Archive and Trash views, search, albums, multi-select actions and stable navigation state. Keep backend contracts unchanged and never delete local MediaStore files from cloud asset actions.
```

## 14. Room-first asset viewer

```text
Render asset detail immediately from remote_assets, observe the active asset while swiping left/right, fetch richer detail metadata in the background, and refresh signed preview URLs only when missing or expired. Do not show a full-screen loader when Room already has the asset.
```

## 15. Signed URL and offline image cache

```text
Use stable Coil memory/disk cache keys based on assetId and variant. Refresh thumbnail/preview/poster URLs through read-url after HTTP 403, deduplicate refreshes, and prefetch previews then thumbnails into an app-private configurable LRU cache. Never cache originals or expose public R2 URLs.
```

## 16. Original image viewing and download

```text
Keep preview as the default detail image. Add Load original using a temporary file and subsampling full-resolution viewer, plus Download original through CreateDocument. Cancel and remove temporary transfers on swipe/back, retry one expired signed URL, and never persist the original URL.
```

## 17. Runtime backend URL

```text
Use BuildConfig.API_BASE_URL by default and allow a validated custom root URL from Login, Register and Settings. Rewrite Retrofit requests at runtime without restarting the process. When the effective backend changes, cancel workers, logout, clear server-scoped Room/cache state and reset local upload mappings.
```
