ALTER TABLE assets
  ADD COLUMN derivative_version SMALLINT NOT NULL DEFAULT 1
  CHECK (derivative_version IN (1, 2));

ALTER TABLE upload_sessions
  ADD COLUMN derivative_version SMALLINT NOT NULL DEFAULT 1
  CHECK (derivative_version IN (1, 2));

UPDATE assets asset
SET derivative_version = 2
WHERE EXISTS (
  SELECT 1
  FROM asset_derivative_repairs repair
  WHERE repair.asset_id = asset.id
);
