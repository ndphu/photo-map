const PRESIGNED_DATE_PATTERN =
  /^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})Z$/;

export const PRESIGNED_URL_REFRESH_WINDOW_MS = 30_000;

export function getPresignedUrlExpiresAt(url: string): number | null {
  try {
    const parsedUrl = new URL(url);
    const signedAtValue = parsedUrl.searchParams.get("X-Amz-Date");
    const expiresValue = parsedUrl.searchParams.get("X-Amz-Expires");
    if (!signedAtValue || !expiresValue || !/^\d+$/.test(expiresValue)) {
      return null;
    }

    const match = PRESIGNED_DATE_PATTERN.exec(signedAtValue);
    if (!match) {
      return null;
    }

    const year = Number(match[1]);
    const month = Number(match[2]);
    const day = Number(match[3]);
    const hour = Number(match[4]);
    const minute = Number(match[5]);
    const second = Number(match[6]);
    const signedAt = Date.UTC(year, month - 1, day, hour, minute, second);
    const signedAtDate = new Date(signedAt);

    if (
      signedAtDate.getUTCFullYear() !== year ||
      signedAtDate.getUTCMonth() !== month - 1 ||
      signedAtDate.getUTCDate() !== day ||
      signedAtDate.getUTCHours() !== hour ||
      signedAtDate.getUTCMinutes() !== minute ||
      signedAtDate.getUTCSeconds() !== second
    ) {
      return null;
    }

    const expiresSeconds = Number(expiresValue);
    if (!Number.isSafeInteger(expiresSeconds) || expiresSeconds <= 0) {
      return null;
    }

    const expiresAt = signedAt + expiresSeconds * 1000;
    return Number.isSafeInteger(expiresAt) ? expiresAt : null;
  } catch {
    return null;
  }
}

export function isPresignedUrlUsable(
  url: string,
  nowMs = Date.now(),
): boolean | null {
  const expiresAt = getPresignedUrlExpiresAt(url);
  if (expiresAt === null) {
    return null;
  }

  return nowMs + PRESIGNED_URL_REFRESH_WINDOW_MS < expiresAt;
}
