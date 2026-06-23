import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

const MIN_GALLERY_COLUMNS = 2;
const MAX_GALLERY_COLUMNS = 6;
const DEFAULT_GALLERY_COLUMNS = 3;

function clampColumns(columns: number): number {
  if (!Number.isFinite(columns)) {
    return DEFAULT_GALLERY_COLUMNS;
  }
  return Math.min(MAX_GALLERY_COLUMNS, Math.max(MIN_GALLERY_COLUMNS, Math.round(columns)));
}

interface GalleryPreferencesState {
  columns: number;
  setColumns: (columns: number) => void;
}

export const useGalleryPreferencesStore = create<GalleryPreferencesState>()(
  persist(
    (set) => ({
      columns: DEFAULT_GALLERY_COLUMNS,
      setColumns: (columns) => {
        set({ columns: clampColumns(columns) });
      },
    }),
    {
      name: "photo-map-web-gallery-preferences",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({ columns: state.columns }),
    },
  ),
);

export { DEFAULT_GALLERY_COLUMNS, MAX_GALLERY_COLUMNS, MIN_GALLERY_COLUMNS };
