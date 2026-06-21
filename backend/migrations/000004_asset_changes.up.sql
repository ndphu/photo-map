CREATE TABLE asset_changes (
  change_id BIGSERIAL PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  asset_id UUID NOT NULL,
  change_type TEXT NOT NULL CHECK (
    change_type IN ('upsert', 'trash', 'restore', 'delete')
  ),
  asset_snapshot JSONB,
  changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (
    (change_type = 'delete' AND asset_snapshot IS NULL)
    OR (change_type <> 'delete' AND asset_snapshot IS NOT NULL)
  )
);

CREATE INDEX asset_changes_user_change_id_idx
  ON asset_changes (user_id, change_id);
