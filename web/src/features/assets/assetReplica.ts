import { appDb, type RemoteAssetRow } from "../../db/appDb";

export type RemoteAssetPatch = Partial<
  Omit<RemoteAssetRow, "ownerUserId" | "id">
>;

export function remoteAssetKey(
  ownerUserId: string,
  assetId: string,
): [string, string] {
  return [ownerUserId, assetId];
}

export function listRemoteAssets(ownerUserId: string): Promise<RemoteAssetRow[]> {
  return appDb.remote_assets_by_user
    .where("ownerUserId")
    .equals(ownerUserId)
    .toArray();
}

export function getRemoteAsset(
  ownerUserId: string,
  assetId: string,
): Promise<RemoteAssetRow | undefined> {
  return appDb.remote_assets_by_user.get(remoteAssetKey(ownerUserId, assetId));
}

export function putRemoteAsset(
  ownerUserId: string,
  asset: Omit<RemoteAssetRow, "ownerUserId">,
): Promise<[string, string]> {
  return appDb.remote_assets_by_user.put({
    ...asset,
    ownerUserId,
  });
}

export function updateRemoteAsset(
  ownerUserId: string,
  assetId: string,
  patch: RemoteAssetPatch,
): Promise<number> {
  return appDb.remote_assets_by_user.update(
    remoteAssetKey(ownerUserId, assetId),
    patch,
  );
}

export function deleteRemoteAsset(
  ownerUserId: string,
  assetId: string,
): Promise<void> {
  return appDb.remote_assets_by_user.delete(remoteAssetKey(ownerUserId, assetId));
}

export function clearRemoteAssets(ownerUserId: string): Promise<number> {
  return appDb.remote_assets_by_user
    .where("ownerUserId")
    .equals(ownerUserId)
    .delete();
}
