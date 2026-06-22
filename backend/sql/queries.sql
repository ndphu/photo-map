-- name: CreateUser :one
INSERT INTO app_users (email, display_name, password_hash)
VALUES ($1, $2, $3)
RETURNING id::text, email, coalesce(display_name, ''), password_hash, created_at, updated_at;

-- name: GetUserByEmail :one
SELECT id::text, email, coalesce(display_name, ''), password_hash, created_at, updated_at
FROM app_users
WHERE email = $1;

-- name: GetUserByID :one
SELECT id::text, email, coalesce(display_name, ''), password_hash, created_at, updated_at
FROM app_users
WHERE id = $1::uuid;

-- name: UpsertDevice :one
INSERT INTO devices (user_id, device_name, platform, device_fingerprint, last_seen_at)
VALUES ($1::uuid, $2, $3, $4, now())
ON CONFLICT (user_id, device_fingerprint)
DO UPDATE SET
  device_name = EXCLUDED.device_name,
  platform = EXCLUDED.platform,
  last_seen_at = now()
RETURNING id::text, user_id::text, coalesce(device_name, ''), platform, device_fingerprint, last_seen_at, created_at, updated_at;

-- name: GetDeviceByIDAndUserID :one
SELECT id::text, user_id::text, coalesce(device_name, ''), platform, device_fingerprint, last_seen_at, created_at, updated_at
FROM devices
WHERE id = $1::uuid AND user_id = $2::uuid;

-- name: ListDevicesByUserID :many
SELECT id::text, user_id::text, coalesce(device_name, ''), platform, device_fingerprint, last_seen_at, created_at, updated_at
FROM devices
WHERE user_id = $1::uuid
ORDER BY last_seen_at DESC NULLS LAST;

-- name: CreateUploadSession :one
INSERT INTO upload_sessions (
  user_id, device_id, local_asset_id, object_key, thumbnail_key, preview_key, poster_frame_key,
  bucket, media_type, mime_type, original_filename, file_size_bytes, expected_checksum_sha256,
  status, expires_at
)
VALUES ($1::uuid, $2::uuid, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, 'created', $14)
RETURNING id::text, user_id::text, device_id::text, local_asset_id, object_key, thumbnail_key,
  preview_key, poster_frame_key, bucket, media_type, mime_type, original_filename,
  file_size_bytes, expected_checksum_sha256, status, asset_id::text, error_message, expires_at,
  completed_at, created_at, updated_at;

-- name: GetActiveUploadSessionByLocalAsset :one
SELECT id::text, user_id::text, device_id::text, local_asset_id, object_key, thumbnail_key,
  preview_key, poster_frame_key, bucket, media_type, mime_type, original_filename,
  file_size_bytes, expected_checksum_sha256, status, asset_id::text, error_message, expires_at,
  completed_at, created_at, updated_at
FROM upload_sessions
WHERE user_id = $1::uuid
  AND device_id = $2::uuid
  AND local_asset_id = $3
  AND status IN ('created', 'uploading', 'uploaded', 'processing')
  AND expires_at > now()
ORDER BY expires_at DESC
LIMIT 1;

-- name: GetUploadSessionForUpdate :one
SELECT id::text, user_id::text, device_id::text, local_asset_id, object_key, thumbnail_key,
  preview_key, poster_frame_key, bucket, media_type, mime_type, original_filename,
  file_size_bytes, expected_checksum_sha256, status, asset_id::text, error_message, expires_at,
  completed_at, created_at, updated_at
FROM upload_sessions
WHERE id = $1::uuid AND user_id = $2::uuid
FOR UPDATE;

-- name: ResumeUploadSession :one
UPDATE upload_sessions
SET expires_at = $3,
  status = CASE WHEN status IN ('failed', 'expired') THEN 'created' ELSE status END,
  error_message = NULL
WHERE id = $1::uuid AND user_id = $2::uuid
RETURNING id::text, user_id::text, device_id::text, local_asset_id, object_key, thumbnail_key,
  preview_key, poster_frame_key, bucket, media_type, mime_type, original_filename,
  file_size_bytes, expected_checksum_sha256, status, asset_id::text, error_message, expires_at,
  completed_at, created_at, updated_at;

-- name: UpdateUploadSessionStatus :one
UPDATE upload_sessions
SET status = $3, error_message = $4
WHERE id = $1::uuid AND user_id = $2::uuid
RETURNING id::text, user_id::text, device_id::text, local_asset_id, object_key, thumbnail_key,
  preview_key, poster_frame_key, bucket, media_type, mime_type, original_filename,
  file_size_bytes, expected_checksum_sha256, status, asset_id::text, error_message, expires_at,
  completed_at, created_at, updated_at;

-- name: MarkUploadSessionCompleted :one
UPDATE upload_sessions
SET status = 'completed', asset_id = $2::uuid, completed_at = now()
WHERE id = $1::uuid AND user_id = $3::uuid
RETURNING id::text, user_id::text, device_id::text, local_asset_id, object_key, thumbnail_key,
  preview_key, poster_frame_key, bucket, media_type, mime_type, original_filename,
  file_size_bytes, expected_checksum_sha256, status, asset_id::text, error_message, expires_at,
  completed_at, created_at, updated_at;

-- name: CreateAsset :one
INSERT INTO assets (
  user_id, bucket, object_key, thumbnail_key, preview_key, poster_frame_key, media_type,
  mime_type, original_filename, file_size_bytes, checksum_sha256, taken_at, taken_at_source,
  timezone_offset_minutes, width, height, orientation, duration_ms, latitude, longitude,
  camera_make, camera_model, software
)
VALUES (
  $1::uuid, $2, $3, $4, $5, $6, $7, $8, $9, $10,
  $11, $12, $13, $14, $15, $16, $17, $18, $19, $20,
  $21, $22, $23
)
ON CONFLICT (user_id, checksum_sha256)
DO UPDATE SET checksum_sha256 = EXCLUDED.checksum_sha256
RETURNING id::text, user_id::text, storage_provider, bucket, object_key, thumbnail_key,
  preview_key, poster_frame_key, media_type, mime_type, original_filename, file_size_bytes,
  checksum_sha256, perceptual_hash, taken_at, taken_at_source, timezone_offset_minutes,
  width, height, orientation, duration_ms, latitude, longitude, country, region, city,
  place_name, camera_make, camera_model, software, blurhash, dominant_color, is_favorite,
  is_archived, is_hidden, is_trashed, trashed_at, uploaded_at, created_at, updated_at;

-- name: GetAssetByChecksum :one
SELECT id::text, user_id::text, storage_provider, bucket, object_key, thumbnail_key,
  preview_key, poster_frame_key, media_type, mime_type, original_filename, file_size_bytes,
  checksum_sha256, perceptual_hash, taken_at, taken_at_source, timezone_offset_minutes,
  width, height, orientation, duration_ms, latitude, longitude, country, region, city,
  place_name, camera_make, camera_model, software, blurhash, dominant_color, is_favorite,
  is_archived, is_hidden, is_trashed, trashed_at, uploaded_at, created_at, updated_at
FROM assets
WHERE user_id = $1::uuid AND checksum_sha256 = $2;

-- name: GetAssetByUploadSessionID :one
SELECT a.id::text, a.user_id::text, a.storage_provider, a.bucket, a.object_key, a.thumbnail_key,
  a.preview_key, a.poster_frame_key, a.media_type, a.mime_type, a.original_filename, a.file_size_bytes,
  a.checksum_sha256, a.perceptual_hash, a.taken_at, a.taken_at_source, a.timezone_offset_minutes,
  a.width, a.height, a.orientation, a.duration_ms, a.latitude, a.longitude, a.country, a.region, a.city,
  a.place_name, a.camera_make, a.camera_model, a.software, a.blurhash, a.dominant_color, a.is_favorite,
  a.is_archived, a.is_hidden, a.is_trashed, a.trashed_at, a.uploaded_at, a.created_at, a.updated_at
FROM upload_sessions us
JOIN assets a ON a.id = us.asset_id
WHERE us.id = $1::uuid AND us.user_id = $2::uuid AND a.user_id = $2::uuid;

-- name: GetAssetByIDForUser :one
SELECT id::text, user_id::text, storage_provider, bucket, object_key, thumbnail_key,
  preview_key, poster_frame_key, media_type, mime_type, original_filename, file_size_bytes,
  checksum_sha256, perceptual_hash, taken_at, taken_at_source, timezone_offset_minutes,
  width, height, orientation, duration_ms, latitude, longitude, country, region, city,
  place_name, camera_make, camera_model, software, blurhash, dominant_color, is_favorite,
  is_archived, is_hidden, is_trashed, trashed_at, uploaded_at, created_at, updated_at
FROM assets
WHERE id = $1::uuid AND user_id = $2::uuid;

-- name: ListAssetsTimeline :many
SELECT id::text, user_id::text, storage_provider, bucket, object_key, thumbnail_key,
  preview_key, poster_frame_key, media_type, mime_type, original_filename, file_size_bytes,
  checksum_sha256, perceptual_hash, taken_at, taken_at_source, timezone_offset_minutes,
  width, height, orientation, duration_ms, latitude, longitude, country, region, city,
  place_name, camera_make, camera_model, software, blurhash, dominant_color, is_favorite,
  is_archived, is_hidden, is_trashed, trashed_at, uploaded_at, created_at, updated_at
FROM assets
WHERE user_id = $1::uuid AND is_trashed = false
ORDER BY taken_at DESC NULLS LAST, id DESC
LIMIT $2 OFFSET $3;

-- name: ListAssetsFiltered :many
SELECT id::text, user_id::text, storage_provider, bucket, object_key, thumbnail_key,
  preview_key, poster_frame_key, media_type, mime_type, original_filename, file_size_bytes,
  checksum_sha256, perceptual_hash, taken_at, taken_at_source, timezone_offset_minutes,
  width, height, orientation, duration_ms, latitude, longitude, country, region, city,
  place_name, camera_make, camera_model, software, blurhash, dominant_color, is_favorite,
  is_archived, is_hidden, is_trashed, trashed_at, uploaded_at, created_at, updated_at
FROM assets
WHERE user_id = $1::uuid
  AND ($2::text IS NULL OR media_type = $2)
  AND ($3::boolean IS NULL OR is_favorite = $3)
  AND ($4::boolean IS NULL OR is_archived = $4)
  AND is_trashed = $5
  AND ($6::text IS NULL OR city = $6)
  AND ($7::timestamptz IS NULL OR taken_at >= $7)
  AND ($8::timestamptz IS NULL OR taken_at <= $8)
  AND (
    $9::uuid IS NULL
    OR (
      $10::timestamptz IS NOT NULL
      AND (taken_at < $10 OR (taken_at = $10 AND id < $9::uuid) OR taken_at IS NULL)
    )
    OR (
      $10::timestamptz IS NULL
      AND taken_at IS NULL
      AND id < $9::uuid
    )
  )
ORDER BY taken_at DESC NULLS LAST, id DESC
LIMIT $11;

-- name: UpdateAssetFavorite :one
UPDATE assets SET is_favorite = $3
WHERE id = $1::uuid AND user_id = $2::uuid
RETURNING id::text, user_id::text, storage_provider, bucket, object_key, thumbnail_key,
  preview_key, poster_frame_key, media_type, mime_type, original_filename, file_size_bytes,
  checksum_sha256, perceptual_hash, taken_at, taken_at_source, timezone_offset_minutes,
  width, height, orientation, duration_ms, latitude, longitude, country, region, city,
  place_name, camera_make, camera_model, software, blurhash, dominant_color, is_favorite,
  is_archived, is_hidden, is_trashed, trashed_at, uploaded_at, created_at, updated_at;

-- name: UpdateAssetArchive :one
UPDATE assets SET is_archived = $3
WHERE id = $1::uuid AND user_id = $2::uuid
RETURNING id::text, user_id::text, storage_provider, bucket, object_key, thumbnail_key,
  preview_key, poster_frame_key, media_type, mime_type, original_filename, file_size_bytes,
  checksum_sha256, perceptual_hash, taken_at, taken_at_source, timezone_offset_minutes,
  width, height, orientation, duration_ms, latitude, longitude, country, region, city,
  place_name, camera_make, camera_model, software, blurhash, dominant_color, is_favorite,
  is_archived, is_hidden, is_trashed, trashed_at, uploaded_at, created_at, updated_at;

-- name: ReplaceAssetMetadata :one
UPDATE assets SET
  taken_at = sqlc.narg('taken_at'),
  taken_at_source = sqlc.narg('taken_at_source'),
  timezone_offset_minutes = sqlc.narg('timezone_offset_minutes'),
  orientation = sqlc.narg('orientation'),
  latitude = sqlc.narg('latitude'),
  longitude = sqlc.narg('longitude'),
  camera_make = sqlc.narg('camera_make'),
  camera_model = sqlc.narg('camera_model'),
  software = sqlc.narg('software'),
  updated_at = now(),
  country = NULL,
  region = NULL,
  city = NULL,
  place_name = NULL
WHERE id = sqlc.arg('id')::uuid AND user_id = sqlc.arg('user_id')::uuid
RETURNING id::text, user_id::text, storage_provider, bucket, object_key, thumbnail_key,
  preview_key, poster_frame_key, media_type, mime_type, original_filename, file_size_bytes,
  checksum_sha256, perceptual_hash, taken_at, taken_at_source, timezone_offset_minutes,
  width, height, orientation, duration_ms, latitude, longitude, country, region, city,
  place_name, camera_make, camera_model, software, blurhash, dominant_color, is_favorite,
  is_archived, is_hidden, is_trashed, trashed_at, uploaded_at, created_at, updated_at;

-- name: MoveAssetToTrash :one
UPDATE assets SET is_trashed = true, trashed_at = now()
WHERE id = $1::uuid AND user_id = $2::uuid
RETURNING id::text, user_id::text, storage_provider, bucket, object_key, thumbnail_key,
  preview_key, poster_frame_key, media_type, mime_type, original_filename, file_size_bytes,
  checksum_sha256, perceptual_hash, taken_at, taken_at_source, timezone_offset_minutes,
  width, height, orientation, duration_ms, latitude, longitude, country, region, city,
  place_name, camera_make, camera_model, software, blurhash, dominant_color, is_favorite,
  is_archived, is_hidden, is_trashed, trashed_at, uploaded_at, created_at, updated_at;

-- name: RestoreAssetFromTrash :one
UPDATE assets SET is_trashed = false, trashed_at = NULL
WHERE id = $1::uuid AND user_id = $2::uuid
RETURNING id::text, user_id::text, storage_provider, bucket, object_key, thumbnail_key,
  preview_key, poster_frame_key, media_type, mime_type, original_filename, file_size_bytes,
  checksum_sha256, perceptual_hash, taken_at, taken_at_source, timezone_offset_minutes,
  width, height, orientation, duration_ms, latitude, longitude, country, region, city,
  place_name, camera_make, camera_model, software, blurhash, dominant_color, is_favorite,
  is_archived, is_hidden, is_trashed, trashed_at, uploaded_at, created_at, updated_at;

-- name: DeleteAssetByID :one
DELETE FROM assets
WHERE id = $1::uuid AND user_id = $2::uuid
RETURNING id::text;

-- name: InsertAssetChange :one
INSERT INTO asset_changes (user_id, asset_id, change_type, asset_snapshot)
VALUES ($1::uuid, $2::uuid, $3, $4::jsonb)
RETURNING change_id, user_id::text, asset_id::text, change_type,
  asset_snapshot, changed_at;

-- name: ListAssetChangesAfterCursor :many
SELECT change_id, user_id::text, asset_id::text, change_type,
  asset_snapshot, changed_at
FROM asset_changes
WHERE user_id = $1::uuid
  AND change_id > $2
ORDER BY change_id ASC
LIMIT $3;

-- name: GetLatestAssetChangeIDForUser :one
SELECT COALESCE(MAX(change_id), 0)::bigint
FROM asset_changes
WHERE user_id = $1::uuid;

-- name: CountAssetsWithoutChanges :one
SELECT COUNT(*)::bigint
FROM assets a
WHERE NOT EXISTS (
  SELECT 1 FROM asset_changes ac WHERE ac.asset_id = a.id
);

-- name: ListAssetsWithoutChanges :many
SELECT a.id::text, a.user_id::text, a.storage_provider, a.bucket, a.object_key,
  a.thumbnail_key, a.preview_key, a.poster_frame_key, a.media_type, a.mime_type,
  a.original_filename, a.file_size_bytes, a.checksum_sha256, a.perceptual_hash,
  a.taken_at, a.taken_at_source, a.timezone_offset_minutes, a.width, a.height,
  a.orientation, a.duration_ms, a.latitude, a.longitude, a.country, a.region,
  a.city, a.place_name, a.camera_make, a.camera_model, a.software, a.blurhash,
  a.dominant_color, a.is_favorite, a.is_archived, a.is_hidden, a.is_trashed,
  a.trashed_at, a.uploaded_at, a.created_at, a.updated_at
FROM assets a
WHERE NOT EXISTS (
  SELECT 1 FROM asset_changes ac WHERE ac.asset_id = a.id
)
ORDER BY a.user_id, a.id;

-- name: InsertInitialAssetChangeIfMissing :one
INSERT INTO asset_changes (user_id, asset_id, change_type, asset_snapshot)
SELECT a.user_id, a.id, 'upsert', $3::jsonb
FROM assets a
WHERE a.user_id = $1::uuid
  AND a.id = $2::uuid
  AND NOT EXISTS (
  SELECT 1 FROM asset_changes WHERE asset_id = $2::uuid
)
RETURNING change_id;

-- name: AcquireAssetChangesBackfillLock :one
SELECT true AS locked
FROM (SELECT pg_advisory_xact_lock(hashtext('asset_changes_backfill'))) AS lock;

-- name: CreateOrUpdateDeviceAsset :exec
INSERT INTO device_assets (
  user_id, device_id, asset_id, local_asset_id, local_uri, local_created_at,
  local_modified_at, sync_status, last_error, last_synced_at
)
VALUES ($1::uuid, $2::uuid, $3::uuid, $4, $5, $6, $7, $8, $9, $10)
ON CONFLICT (user_id, device_id, local_asset_id)
DO UPDATE SET
  asset_id = EXCLUDED.asset_id,
  local_uri = EXCLUDED.local_uri,
  local_created_at = EXCLUDED.local_created_at,
  local_modified_at = EXCLUDED.local_modified_at,
  sync_status = EXCLUDED.sync_status,
  last_error = EXCLUDED.last_error,
  last_synced_at = EXCLUDED.last_synced_at;

-- name: GetDeviceAssetByLocalID :one
SELECT asset_id::text
FROM device_assets
WHERE user_id = $1::uuid AND device_id = $2::uuid AND local_asset_id = $3;

-- name: UpsertDeviceAssetToAsset :exec
INSERT INTO device_assets (
  user_id, device_id, asset_id, local_asset_id, local_created_at, local_modified_at,
  sync_status, last_synced_at
)
VALUES ($1::uuid, $2::uuid, $3::uuid, $4, $5, $6, 'synced', now())
ON CONFLICT (user_id, device_id, local_asset_id)
DO UPDATE SET
  asset_id = EXCLUDED.asset_id,
  local_created_at = coalesce(EXCLUDED.local_created_at, device_assets.local_created_at),
  local_modified_at = coalesce(EXCLUDED.local_modified_at, device_assets.local_modified_at),
  sync_status = 'synced',
  last_error = NULL,
  last_synced_at = now();

-- name: CreateAlbum :one
INSERT INTO albums (user_id, name, description, cover_asset_id)
VALUES ($1::uuid, $2, $3, $4::uuid)
RETURNING id::text, user_id::text, name, description, cover_asset_id::text, is_archived, created_at, updated_at;

-- name: ListAlbums :many
SELECT id::text, user_id::text, name, description, cover_asset_id::text, is_archived, created_at, updated_at
FROM albums
WHERE user_id = $1::uuid
ORDER BY created_at DESC;

-- name: GetAlbumByIDForUser :one
SELECT id::text, user_id::text, name, description, cover_asset_id::text, is_archived, created_at, updated_at
FROM albums
WHERE id = $1::uuid AND user_id = $2::uuid;

-- name: UpdateAlbum :one
UPDATE albums
SET name = $3, description = $4, cover_asset_id = $5::uuid, is_archived = $6
WHERE id = $1::uuid AND user_id = $2::uuid
RETURNING id::text, user_id::text, name, description, cover_asset_id::text, is_archived, created_at, updated_at;

-- name: DeleteAlbum :exec
DELETE FROM albums WHERE id = $1::uuid AND user_id = $2::uuid;

-- name: AddAssetToAlbum :exec
INSERT INTO album_assets (album_id, asset_id, sort_order)
VALUES ($1::uuid, $2::uuid, $3)
ON CONFLICT (album_id, asset_id)
DO UPDATE SET sort_order = EXCLUDED.sort_order;

-- name: RemoveAssetFromAlbum :exec
DELETE FROM album_assets WHERE album_id = $1::uuid AND asset_id = $2::uuid;

-- name: ListAlbumAssets :many
SELECT a.id::text, a.user_id::text, a.storage_provider, a.bucket, a.object_key, a.thumbnail_key,
  a.preview_key, a.poster_frame_key, a.media_type, a.mime_type, a.original_filename, a.file_size_bytes,
  a.checksum_sha256, a.perceptual_hash, a.taken_at, a.taken_at_source, a.timezone_offset_minutes,
  a.width, a.height, a.orientation, a.duration_ms, a.latitude, a.longitude, a.country, a.region, a.city,
  a.place_name, a.camera_make, a.camera_model, a.software, a.blurhash, a.dominant_color, a.is_favorite,
  a.is_archived, a.is_hidden, a.is_trashed, a.trashed_at, a.uploaded_at, a.created_at, a.updated_at
FROM album_assets aa
JOIN albums al ON al.id = aa.album_id
JOIN assets a ON a.id = aa.asset_id
WHERE aa.album_id = $1::uuid AND al.user_id = $2::uuid
ORDER BY aa.sort_order NULLS LAST, aa.added_at DESC;

-- name: SearchAssets :many
SELECT a.id::text, a.user_id::text, a.storage_provider, a.bucket, a.object_key, a.thumbnail_key,
  a.preview_key, a.poster_frame_key, a.media_type, a.mime_type, a.original_filename, a.file_size_bytes,
  a.checksum_sha256, a.perceptual_hash, a.taken_at, a.taken_at_source, a.timezone_offset_minutes,
  a.width, a.height, a.orientation, a.duration_ms, a.latitude, a.longitude, a.country, a.region, a.city,
  a.place_name, a.camera_make, a.camera_model, a.software, a.blurhash, a.dominant_color, a.is_favorite,
  a.is_archived, a.is_hidden, a.is_trashed, a.trashed_at, a.uploaded_at, a.created_at, a.updated_at
FROM assets a
JOIN asset_search_metadata asm ON asm.asset_id = a.id
WHERE a.user_id = $1::uuid
  AND a.is_trashed = false
  AND asm.search_vector @@ plainto_tsquery('english', $2)
ORDER BY ts_rank(asm.search_vector, plainto_tsquery('english', $2)) DESC, a.taken_at DESC NULLS LAST
LIMIT $3 OFFSET $4;

-- name: ListExpiredIncompleteUploadSessions :many
SELECT id::text, user_id::text, device_id::text, local_asset_id, object_key, thumbnail_key,
  preview_key, poster_frame_key, bucket, media_type, mime_type, original_filename,
  file_size_bytes, expected_checksum_sha256, status, asset_id::text, error_message, expires_at,
  completed_at, created_at, updated_at
FROM upload_sessions
WHERE status IN ('created', 'uploading', 'uploaded', 'failed', 'expired')
  AND asset_id IS NULL
  AND expires_at < $1
ORDER BY expires_at ASC
LIMIT $2;

-- name: MarkUploadSessionExpired :one
UPDATE upload_sessions
SET status = 'expired', error_message = $3
WHERE id = $1::uuid
  AND user_id = $2::uuid
  AND asset_id IS NULL
  AND status IN ('created', 'uploading', 'uploaded', 'failed', 'expired')
RETURNING id::text;

-- name: CheckObjectKeyUsedByAsset :one
SELECT EXISTS (
  SELECT 1
  FROM assets
  WHERE object_key = $1
    OR thumbnail_key = $1
    OR preview_key = $1
    OR poster_frame_key = $1
);

-- name: CreateAuditLog :exec
INSERT INTO audit_logs (user_id, action, entity_type, entity_id, metadata)
VALUES ($1::uuid, $2, $3, $4::uuid, $5::jsonb);
