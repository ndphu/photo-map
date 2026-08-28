import { apiRequest } from "../../lib/apiClient";
import { getRemoteAsset, putRemoteAsset } from "./assetReplica";

export interface AssetDetailResponse {
  id: string;
  mediaType: "image" | "video";
  mimeType: string;
  objectKey: string;
  thumbnailKey: string | null;
  previewKey: string | null;
  posterFrameKey: string | null;
  originalFilename: string | null;
  fileSizeBytes: number;
  checksumSha256: string;
  takenAt: string | null;
  takenAtSource: string | null;
  timezoneOffsetMinutes: number | null;
  width: number | null;
  height: number | null;
  orientation: number | null;
  durationMs: number | null;
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
  isHidden: boolean;
  isTrashed: boolean;
  trashedAt: string | null;
  uploadedAt: string;
  createdAt: string;
  updatedAt: string;
}

interface ReadUrlResponse {
  url: string;
}

export type ReadUrlVariant = "preview" | "original";

export function getAssetDetail(assetId: string): Promise<AssetDetailResponse> {
  return apiRequest<AssetDetailResponse>(`/assets/${assetId}`);
}

export function getAssetReadUrl(
  assetId: string,
  variant: ReadUrlVariant,
): Promise<ReadUrlResponse> {
  const query = new URLSearchParams({ variant });
  return apiRequest<ReadUrlResponse>(`/assets/${assetId}/read-url?${query.toString()}`);
}

export async function enrichRemoteAssetFromDetail(
  ownerUserId: string,
  assetId: string,
): Promise<void> {
  const detail = await getAssetDetail(assetId);

  const existing = await getRemoteAsset(ownerUserId, assetId);

  await putRemoteAsset(ownerUserId, {
    id: detail.id,
    mediaType: detail.mediaType,
    mimeType: detail.mimeType,
    originalFilename: detail.originalFilename,
    fileSizeBytes: detail.fileSizeBytes,
    checksumSha256: detail.checksumSha256,
    thumbnailKey: detail.thumbnailKey,
    previewKey: detail.previewKey,
    posterFrameKey: detail.posterFrameKey,
    takenAt: detail.takenAt,
    takenAtSource: detail.takenAtSource,
    timezoneOffsetMinutes: detail.timezoneOffsetMinutes,
    width: detail.width,
    height: detail.height,
    durationMs: detail.durationMs,
    orientation: detail.orientation,
    latitude: detail.latitude,
    longitude: detail.longitude,
    country: detail.country,
    region: detail.region,
    city: detail.city,
    placeName: detail.placeName,
    cameraMake: detail.cameraMake,
    cameraModel: detail.cameraModel,
    software: detail.software,
    isFavorite: detail.isFavorite,
    isArchived: detail.isArchived,
    isTrashed: detail.isTrashed,
    uploadedAt: detail.uploadedAt,
    updatedAt: detail.updatedAt,
    thumbnailUrl: existing?.thumbnailUrl ?? null,
    previewUrl: existing?.previewUrl ?? null,
    lastChangeId: existing?.lastChangeId ?? 0,
  });
}
