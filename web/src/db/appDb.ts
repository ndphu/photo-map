import Dexie, { type Table } from "dexie";

export interface RemoteAssetRow {
  ownerUserId: string;
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
  ownerUserId: string;
  key: "asset_metadata";
  value: number;
  updatedAt: string;
}

export class AppDb extends Dexie {
  remote_assets_by_user!: Table<RemoteAssetRow, [string, string]>;
  remote_sync_state_by_user!: Table<
    RemoteSyncStateRow,
    [string, "asset_metadata"]
  >;

  constructor(databaseName = "photo-map-web-db") {
    super(databaseName);

    this.version(2).stores({
      remote_assets: "&id, updatedAt, isTrashed, isFavorite, isArchived",
      remote_sync_state: "&key, updatedAt",
    });

    this.version(3).stores({
      // Version 2 rows have no owner identity. Delete the legacy stores once
      // instead of assigning them to whichever account happens to log in next.
      remote_assets: null,
      remote_sync_state: null,
      remote_assets_by_user:
        "&[ownerUserId+id], ownerUserId, updatedAt, isTrashed, isFavorite, isArchived",
      remote_sync_state_by_user:
        "&[ownerUserId+key], ownerUserId, updatedAt",
    });
  }
}

export const appDb = new AppDb();
