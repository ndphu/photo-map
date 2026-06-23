import { liveQuery } from "dexie";
import { useEffect, useState } from "react";
import { appDb, type RemoteAssetRow } from "../../db/appDb";

interface RemoteAssetsReplicaState {
  assets: RemoteAssetRow[];
  isLoading: boolean;
  errorMessage: string | null;
}

export function useRemoteAssetsReplica(): RemoteAssetsReplicaState {
  const [assets, setAssets] = useState<RemoteAssetRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    const subscription = liveQuery(() => appDb.remote_assets.toArray()).subscribe({
      next: (rows) => {
        setAssets(rows);
        setIsLoading(false);
        setErrorMessage(null);
      },
      error: (error) => {
        setIsLoading(false);
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "Failed to read gallery replica from IndexedDB",
        );
      },
    });

    return () => {
      subscription.unsubscribe();
    };
  }, []);

  return {
    assets,
    isLoading,
    errorMessage,
  };
}
