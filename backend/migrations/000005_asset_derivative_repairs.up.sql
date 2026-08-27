CREATE TABLE asset_derivative_repairs (
  asset_id UUID PRIMARY KEY REFERENCES assets(id) ON DELETE CASCADE,
  source_thumbnail_key TEXT NOT NULL,
  source_preview_key TEXT NOT NULL,
  repaired_thumbnail_key TEXT NOT NULL,
  repaired_preview_key TEXT NOT NULL,
  original_orientation SMALLINT NOT NULL CHECK (original_orientation BETWEEN 2 AND 8),
  completed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

