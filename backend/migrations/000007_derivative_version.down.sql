ALTER TABLE upload_sessions
  DROP COLUMN IF EXISTS derivative_version;

ALTER TABLE assets
  DROP COLUMN IF EXISTS derivative_version;
