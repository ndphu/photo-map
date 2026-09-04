import { appDb, type RemoteAssetRow } from "../../db/appDb";
import { apiRequest } from "../../lib/apiClient";
import {
  clearRemoteAssets,
  deleteRemoteAsset,
  putRemoteAsset,
  updateRemoteAsset,
} from "./assetReplica";

const ASSET_METADATA_STATE_KEY = "asset_metadata" as const;
const PAGE_LIMIT = 400;

export interface SyncAssetsChangesOptions {
  full?: boolean;
  onProgress?: (progress: AssetSyncProgress) => void;
}

export interface AssetSyncProgress {
  completedCount: number;
  remainingCount: number | null;
  totalCount: number | null;
  percent: number | null;
}

interface AssetSnapshot {
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
}

interface AssetChangeAsset extends AssetSnapshot {
  // Signed URLs are temporary and may expire; keep them as transient cache fields.
  thumbnailUrl: string | null;
  previewUrl: string | null;
}

type AssetChangeType = "upsert" | "trash" | "restore" | "delete";

interface AssetChangeItem {
  changeId: number;
  assetId: string;
  changeType: AssetChangeType;
  changedAt: string;
  asset: AssetChangeAsset | null;
}

interface AssetChangesPage {
  items: AssetChangeItem[];
  nextCursor: number;
  hasMore: boolean;
  remainingCount?: number;
  serverCursor: number;
  serverTime: string;
}

export function calculateAssetSyncProgress(
  completedCount: number,
  remainingCount: number | undefined,
): AssetSyncProgress {
  if (remainingCount === undefined) {
    return {
      completedCount,
      remainingCount: null,
      totalCount: null,
      percent: null,
    };
  }

  const safeRemainingCount = Math.max(0, remainingCount);
  const totalCount = completedCount + safeRemainingCount;
  const percent =
    totalCount === 0 ? 100 : Math.floor((completedCount * 100) / totalCount);

  return {
    completedCount,
    remainingCount: safeRemainingCount,
    totalCount,
    percent,
  };
}

interface ReadUrlResponse {
  url: string;
}

export type AssetReadUrlVariant = "thumbnail" | "preview";

function toRemoteAssetRow(
  ownerUserId: string,
  asset: AssetChangeAsset,
  changeId: number,
): RemoteAssetRow {
  return {
    ownerUserId,
    id: asset.id,
    mediaType: asset.mediaType,
    mimeType: asset.mimeType,
    originalFilename: asset.originalFilename,
    fileSizeBytes: asset.fileSizeBytes,
    checksumSha256: asset.checksumSha256,
    thumbnailKey: asset.thumbnailKey,
    previewKey: asset.previewKey,
    posterFrameKey: asset.posterFrameKey,
    takenAt: asset.takenAt,
    takenAtSource: asset.takenAtSource,
    timezoneOffsetMinutes: asset.timezoneOffsetMinutes,
    width: asset.width,
    height: asset.height,
    durationMs: asset.durationMs,
    orientation: asset.orientation,
    latitude: asset.latitude,
    longitude: asset.longitude,
    country: asset.country,
    region: asset.region,
    city: asset.city,
    placeName: asset.placeName,
    cameraMake: asset.cameraMake,
    cameraModel: asset.cameraModel,
    software: asset.software,
    isFavorite: asset.isFavorite,
    isArchived: asset.isArchived,
    isTrashed: asset.isTrashed,
    uploadedAt: asset.uploadedAt,
    updatedAt: asset.updatedAt,
    thumbnailUrl: asset.thumbnailUrl,
    previewUrl: asset.previewUrl,
    lastChangeId: changeId,
  };
}

async function getLastCommittedCursor(ownerUserId: string): Promise<number> {
  const state = await appDb.remote_sync_state_by_user.get([
    ownerUserId,
    ASSET_METADATA_STATE_KEY,
  ]);
  return state?.value ?? 0;
}

async function fetchChangesPage(cursor: number): Promise<AssetChangesPage> {
  const query = new URLSearchParams({
    cursor: String(cursor),
    limit: String(PAGE_LIMIT),
  });

  return apiRequest<AssetChangesPage>(`/assets/changes?${query.toString()}`);
}

async function applyChangesPage(
  ownerUserId: string,
  page: AssetChangesPage,
): Promise<void> {
  await appDb.transaction(
    "rw",
    appDb.remote_assets_by_user,
    appDb.remote_sync_state_by_user,
    async () => {
      for (const item of page.items) {
        if (item.changeType === "delete" || item.asset === null) {
          await deleteRemoteAsset(ownerUserId, item.assetId);
          continue;
        }

        const row = toRemoteAssetRow(ownerUserId, item.asset, item.changeId);
        await putRemoteAsset(ownerUserId, row);
      }

      await appDb.remote_sync_state_by_user.put({
        ownerUserId,
        key: ASSET_METADATA_STATE_KEY,
        value: page.nextCursor,
        updatedAt: new Date().toISOString(),
      });
    },
  );
}

export async function syncAssetsChanges(
  ownerUserId: string,
  options: SyncAssetsChangesOptions = {},
): Promise<number> {
  if (options.full) {
    await appDb.transaction(
      "rw",
      appDb.remote_assets_by_user,
      appDb.remote_sync_state_by_user,
      async () => {
        await clearRemoteAssets(ownerUserId);
        await appDb.remote_sync_state_by_user.put({
          ownerUserId,
          key: ASSET_METADATA_STATE_KEY,
          value: 0,
          updatedAt: new Date().toISOString(),
        });
      },
    );
  }

  let cursor = await getLastCommittedCursor(ownerUserId);
  let completedCount = 0;

  while (true) {
    const page = await fetchChangesPage(cursor);

    await applyChangesPage(ownerUserId, page);
    cursor = page.nextCursor;
    completedCount += page.items.length;
    options.onProgress?.(
      calculateAssetSyncProgress(completedCount, page.remainingCount),
    );

    if (!page.hasMore) {
      break;
    }
  }

  return cursor;
}

export async function clearAssetsReplicationCache(ownerUserId: string): Promise<void> {
  await appDb.transaction(
    "rw",
    appDb.remote_assets_by_user,
    appDb.remote_sync_state_by_user,
    async () => {
      await clearRemoteAssets(ownerUserId);
      await appDb.remote_sync_state_by_user.delete([
        ownerUserId,
        ASSET_METADATA_STATE_KEY,
      ]);
    },
  );
}

export async function getAssetReplicaReadUrl(
  assetId: string,
  variant: AssetReadUrlVariant,
): Promise<string> {
  const query = new URLSearchParams({ variant });
  const response = await apiRequest<ReadUrlResponse>(
    `/assets/${assetId}/read-url?${query.toString()}`,
  );
  return response.url;
}

export async function refreshAssetReplicaUrl(
  ownerUserId: string,
  assetId: string,
  variant: AssetReadUrlVariant,
): Promise<void> {
  const nextUrl = await getAssetReplicaReadUrl(assetId, variant);

  if (variant === "thumbnail") {
    await updateRemoteAsset(ownerUserId, assetId, { thumbnailUrl: nextUrl });
    return;
  }

  await updateRemoteAsset(ownerUserId, assetId, { previewUrl: nextUrl });
}
