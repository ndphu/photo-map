import "fake-indexeddb/auto";

import Dexie from "dexie";
import { afterAll, beforeEach, describe, expect, it, vi } from "vitest";
import { AppDb, appDb, type RemoteAssetRow } from "../../db/appDb";
import { useAuthStore } from "../../store/authStore";
import { clearAssetsReplicationCache, syncAssetsChanges } from "./assetChanges";
import {
  getRemoteAsset,
  listRemoteAssets,
  putRemoteAsset,
  updateRemoteAsset,
} from "./assetReplica";

const USER_A = "user-a";
const USER_B = "user-b";
const ASSET_ID = "asset-1";

function createAsset(
  id: string,
  originalFilename: string,
): Omit<RemoteAssetRow, "ownerUserId"> {
  return {
    id,
    mediaType: "image",
    mimeType: "image/jpeg",
    originalFilename,
    fileSizeBytes: 1,
    checksumSha256: "checksum",
    thumbnailKey: null,
    previewKey: null,
    posterFrameKey: null,
    takenAt: null,
    takenAtSource: null,
    timezoneOffsetMinutes: null,
    width: null,
    height: null,
    durationMs: null,
    orientation: null,
    latitude: null,
    longitude: null,
    country: null,
    region: null,
    city: null,
    placeName: null,
    cameraMake: null,
    cameraModel: null,
    software: null,
    isFavorite: false,
    isArchived: false,
    isTrashed: false,
    uploadedAt: "2026-08-28T00:00:00.000Z",
    updatedAt: "2026-08-28T00:00:00.000Z",
    thumbnailUrl: null,
    previewUrl: null,
    lastChangeId: 1,
  };
}

beforeEach(async () => {
  vi.restoreAllMocks();
  await appDb.transaction(
    "rw",
    appDb.remote_assets_by_user,
    appDb.remote_sync_state_by_user,
    async () => {
      await appDb.remote_assets_by_user.clear();
      await appDb.remote_sync_state_by_user.clear();
    },
  );
  useAuthStore.setState({
    accessToken: null,
    user: null,
    isAuthenticated: false,
  });
});

afterAll(() => {
  appDb.close();
});

describe("user-scoped asset replica", () => {
  it("discards unscoped version 2 rows during the version 3 upgrade", async () => {
    const databaseName = "photo-map-web-db-upgrade-test";
    const legacyDb = new Dexie(databaseName);
    legacyDb.version(2).stores({
      remote_assets: "&id, updatedAt, isTrashed, isFavorite, isArchived",
      remote_sync_state: "&key, updatedAt",
    });
    await legacyDb.table("remote_assets").put({
      ...createAsset(ASSET_ID, "legacy.jpg"),
    });
    await legacyDb.table("remote_sync_state").put({
      key: "asset_metadata",
      value: 50,
      updatedAt: "2026-08-28T00:00:00.000Z",
    });
    legacyDb.close();

    const upgradedDb = new AppDb(databaseName);
    await upgradedDb.open();

    expect(await upgradedDb.remote_assets_by_user.count()).toBe(0);
    expect(await upgradedDb.remote_sync_state_by_user.count()).toBe(0);

    upgradedDb.close();
    await Dexie.delete(databaseName);
  });

  it("isolates rows with the same asset id between users", async () => {
    await putRemoteAsset(USER_A, createAsset(ASSET_ID, "a.jpg"));
    await putRemoteAsset(USER_B, createAsset(ASSET_ID, "b.jpg"));

    await updateRemoteAsset(USER_A, ASSET_ID, { isFavorite: true });

    expect((await getRemoteAsset(USER_A, ASSET_ID))?.originalFilename).toBe(
      "a.jpg",
    );
    expect((await getRemoteAsset(USER_A, ASSET_ID))?.isFavorite).toBe(true);
    expect((await getRemoteAsset(USER_B, ASSET_ID))?.originalFilename).toBe(
      "b.jpg",
    );
    expect((await getRemoteAsset(USER_B, ASSET_ID))?.isFavorite).toBe(false);
    expect(await listRemoteAssets(USER_A)).toHaveLength(1);
    expect(await listRemoteAssets(USER_B)).toHaveLength(1);
  });

  it("clears only the requested user partition", async () => {
    await putRemoteAsset(USER_A, createAsset("asset-a", "a.jpg"));
    await putRemoteAsset(USER_B, createAsset("asset-b", "b.jpg"));
    await appDb.remote_sync_state_by_user.bulkPut([
      {
        ownerUserId: USER_A,
        key: "asset_metadata",
        value: 10,
        updatedAt: "2026-08-28T00:00:00.000Z",
      },
      {
        ownerUserId: USER_B,
        key: "asset_metadata",
        value: 20,
        updatedAt: "2026-08-28T00:00:00.000Z",
      },
    ]);

    await clearAssetsReplicationCache(USER_A);

    expect(await listRemoteAssets(USER_A)).toEqual([]);
    expect(await listRemoteAssets(USER_B)).toHaveLength(1);
    expect(
      await appDb.remote_sync_state_by_user.get([USER_A, "asset_metadata"]),
    ).toBeUndefined();
    expect(
      (await appDb.remote_sync_state_by_user.get([USER_B, "asset_metadata"]))
        ?.value,
    ).toBe(20);
  });

  it("keeps the replica when the auth store logs out", async () => {
    await putRemoteAsset(USER_A, createAsset(ASSET_ID, "a.jpg"));
    useAuthStore.getState().setSession("access-token", {
      id: USER_A,
      email: "a@example.com",
      displayName: "User A",
    });

    useAuthStore.getState().logout();

    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(await getRemoteAsset(USER_A, ASSET_ID)).toBeDefined();
  });

  it("resumes sync from the current user's committed cursor", async () => {
    await appDb.remote_sync_state_by_user.put({
      ownerUserId: USER_A,
      key: "asset_metadata",
      value: 100,
      updatedAt: "2026-08-28T00:00:00.000Z",
    });
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [],
          nextCursor: 100,
          hasMore: false,
          serverCursor: 100,
          serverTime: "2026-08-28T00:00:00.000Z",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );

    await syncAssetsChanges(USER_A);

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain("cursor=100");
  });
});
