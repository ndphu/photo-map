CREATE UNIQUE INDEX IF NOT EXISTS upload_sessions_user_object_key_uidx
ON upload_sessions (user_id, object_key);

CREATE INDEX IF NOT EXISTS idx_upload_sessions_user_device_local_status
ON upload_sessions (user_id, device_id, local_asset_id, status, expires_at DESC);
