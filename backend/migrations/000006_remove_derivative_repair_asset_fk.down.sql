ALTER TABLE asset_derivative_repairs
  ADD CONSTRAINT asset_derivative_repairs_asset_id_fkey
  FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE;

