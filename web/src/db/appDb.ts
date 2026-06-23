import Dexie, { type Table } from "dexie";

export interface RemoteAssetRow {
  id: string;
  mediaType: string;
  mimeType: string;
  originalFilename: string | null;
  fileSizeBytes: number;
  checksumSha256: string;
  thumbnailKey: string | null;
  previewKey: string | null;
  posterFrameKey: string | null;
  takenAt: string | null;
  takenAtSource: string | null;
  timezoneOffsetMinutes: number | null;
  width: number | null;
  height: number | null;
  durationMs: number | null;
  orientation: number | null;
  latitude: number | null;
  longitude: number | null;
  country: string | null;
  region: string | null;
  city: string | null;
  placeName: string | null;
  cameraMake: string | null;
  cameraModel: string | null;
  software: string | null;
  isFavorite: boolean;
  isArchived: boolean;
  isTrashed: boolean;
  uploadedAt: string;
  updatedAt: string;
  thumbnailUrl: string | null;
  previewUrl: string | null;
  // track last replicated change for diagnostics and ordering checks
  lastChangeId: number;
}

export interface RemoteSyncStateRow {
  key: "asset_metadata";
  value: number;
  updatedAt: string;
}

class AppDb extends Dexie {
  remote_assets!: Table<RemoteAssetRow, string>;
  remote_sync_state!: Table<RemoteSyncStateRow, "asset_metadata">;

  constructor() {
    super("photo-map-web-db");

    this.version(2).stores({
      remote_assets: "&id, updatedAt, isTrashed, isFavorite, isArchived",
      remote_sync_state: "&key, updatedAt",
    });
  }
}

export const appDb = new AppDb();
