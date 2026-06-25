import type { RemoteAssetRow } from "../../db/appDb";

export type GalleryNavView = "photos" | "favorites" | "archive" | "trash";

export type GalleryQuickFilter = "all" | "photos" | "videos";

export interface DateGroup {
  key: string;
  label: string;
  monthKey: string;
  monthLabel: string;
  assets: RemoteAssetRow[];
}

const DAY_MS = 24 * 60 * 60 * 1000;

function parseTime(value: string | null): number | null {
  if (!value) {
    return null;
  }

  const epoch = Date.parse(value);
  if (Number.isNaN(epoch)) {
    return null;
  }

  return epoch;
}

export function sortAssetsForTimeline(assets: RemoteAssetRow[]): RemoteAssetRow[] {
  return [...assets].sort((left, right) => {
    const leftEpoch = parseTime(left.takenAt);
    const rightEpoch = parseTime(right.takenAt);

    if (leftEpoch === null && rightEpoch === null) {
      return right.id.localeCompare(left.id);
    }

    if (leftEpoch === null) {
      return 1;
    }

    if (rightEpoch === null) {
      return -1;
    }

    if (leftEpoch !== rightEpoch) {
      return rightEpoch - leftEpoch;
    }

    return right.id.localeCompare(left.id);
  });
}

export function applyNavViewFilter(
  assets: RemoteAssetRow[],
  view: GalleryNavView,
): RemoteAssetRow[] {
  switch (view) {
    case "favorites":
      return assets.filter((asset) => asset.isFavorite && !asset.isTrashed);
    case "archive":
      return assets.filter((asset) => asset.isArchived && !asset.isTrashed);
    case "trash":
      return assets.filter((asset) => asset.isTrashed);
    case "photos":
    default:
      return assets.filter((asset) => !asset.isArchived && !asset.isTrashed);
  }
}

export function applyQuickFilter(
  assets: RemoteAssetRow[],
  quickFilter: GalleryQuickFilter,
): RemoteAssetRow[] {
  switch (quickFilter) {
    case "photos":
      return assets.filter((asset) => asset.mediaType === "image");
    case "videos":
      return assets.filter((asset) => asset.mediaType === "video");
    case "all":
    default:
      return assets;
  }
}

function startOfLocalDay(epoch: number): number {
  const date = new Date(epoch);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

function toDateGroupLabel(epoch: number): string {
  const dayStart = startOfLocalDay(epoch);
  const nowStart = startOfLocalDay(Date.now());
  const dayDelta = Math.floor((nowStart - dayStart) / DAY_MS);

  if (dayDelta === 0) {
    return "Today";
  }

  if (dayDelta === 1) {
    return "Yesterday";
  }

  return new Date(epoch).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

function toDateGroupKey(epoch: number): string {
  const date = new Date(epoch);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(
    date.getDate(),
  ).padStart(2, "0")}`;
}

function toMonthKey(epoch: number): string {
  const date = new Date(epoch);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

function toMonthLabel(epoch: number): string {
  return new Date(epoch).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
  });
}

export function groupAssetsByDate(assets: RemoteAssetRow[]): DateGroup[] {
  const grouped: DateGroup[] = [];
  const byKey = new Map<string, DateGroup>();

  for (const asset of assets) {
    const epoch = parseTime(asset.takenAt);

    if (epoch === null) {
      const unknownKey = "unknown";
      const existingUnknown = byKey.get(unknownKey);
      if (existingUnknown) {
        existingUnknown.assets.push(asset);
      } else {
        const unknownGroup: DateGroup = {
          key: unknownKey,
          label: "Date unknown",
          monthKey: "unknown",
          monthLabel: "Unknown",
          assets: [asset],
        };
        byKey.set(unknownKey, unknownGroup);
        grouped.push(unknownGroup);
      }
      continue;
    }

    const key = toDateGroupKey(epoch);
    const existing = byKey.get(key);
    if (existing) {
      existing.assets.push(asset);
      continue;
    }

    const group: DateGroup = {
      key,
      label: toDateGroupLabel(epoch),
      monthKey: toMonthKey(epoch),
      monthLabel: toMonthLabel(epoch),
      assets: [asset],
    };

    byKey.set(key, group);
    grouped.push(group);
  }

  return grouped;
}

export interface MonthIndexItem {
  monthKey: string;
  monthLabel: string;
  firstGroupKey: string;
}

export function buildMonthIndex(groups: DateGroup[]): MonthIndexItem[] {
  const months: MonthIndexItem[] = [];
  const seen = new Set<string>();

  for (const group of groups) {
    if (seen.has(group.monthKey)) {
      continue;
    }
    seen.add(group.monthKey);
    months.push({
      monthKey: group.monthKey,
      monthLabel: group.monthLabel,
      firstGroupKey: group.key,
    });
  }

  return months;
}

export function getRangeSelectionIds(
  orderedIds: string[],
  previousAnchorId: string | null,
  targetId: string,
): string[] {
  if (!previousAnchorId) {
    return [targetId];
  }

  const fromIndex = orderedIds.indexOf(previousAnchorId);
  const toIndex = orderedIds.indexOf(targetId);

  if (fromIndex === -1 || toIndex === -1) {
    return [targetId];
  }

  const [start, end] = fromIndex <= toIndex ? [fromIndex, toIndex] : [toIndex, fromIndex];
  return orderedIds.slice(start, end + 1);
}
