import { liveQuery } from "dexie";
import { useEffect, useState } from "react";
import type { RemoteAssetRow } from "../../db/appDb";
import { useAuthStore } from "../../store/authStore";
import { listRemoteAssets } from "./assetReplica";

interface RemoteAssetsReplicaState {
  assets: RemoteAssetRow[];
  isLoading: boolean;
  errorMessage: string | null;
}

interface RemoteAssetsReplicaSnapshot {
  ownerUserId: string;
  assets: RemoteAssetRow[];
  errorMessage: string | null;
}

export function useRemoteAssetsReplica(): RemoteAssetsReplicaState {
  const ownerUserId = useAuthStore((state) => state.user?.id ?? null);
  const [snapshot, setSnapshot] =
    useState<RemoteAssetsReplicaSnapshot | null>(null);

  useEffect(() => {
    if (!ownerUserId) {
      return;
    }

    const subscription = liveQuery(() => listRemoteAssets(ownerUserId)).subscribe({
      next: (rows) => {
        setSnapshot({ ownerUserId, assets: rows, errorMessage: null });
      },
      error: (error) => {
        setSnapshot({
          ownerUserId,
          assets: [],
          errorMessage:
            error instanceof Error
              ? error.message
              : "Failed to read gallery replica from IndexedDB",
        });
      },
    });

    return () => {
      subscription.unsubscribe();
    };
  }, [ownerUserId]);

  const hasActiveSnapshot =
    ownerUserId !== null && snapshot?.ownerUserId === ownerUserId;

  return {
    assets: hasActiveSnapshot ? snapshot.assets : [],
    isLoading: ownerUserId !== null && !hasActiveSnapshot,
    errorMessage: hasActiveSnapshot ? snapshot.errorMessage : null,
  };
}
