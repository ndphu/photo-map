import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import { useAssetSyncStore } from "../features/assets/assetSyncStore";
import type { AuthUser } from "../types/auth";

interface AuthState {
  accessToken: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  setSession: (accessToken: string, user: AuthUser) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      user: null,
      isAuthenticated: false,
      setSession: (accessToken, user) => {
        set({
          accessToken,
          user,
          isAuthenticated: true,
        });
      },
      logout: () => {
        set({
          accessToken: null,
          user: null,
          isAuthenticated: false,
        });
        useAssetSyncStore.getState().resetSyncState();
      },
    }),
    {
      name: "photo-map-web-auth",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        accessToken: state.accessToken,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    },
  ),
);
