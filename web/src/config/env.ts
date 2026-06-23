const DEFAULT_API_BASE_URL = "http://localhost:8080";

function normalizeBaseUrl(baseUrl: string): string {
  const trimmed = baseUrl.trim();
  return trimmed.endsWith("/") ? trimmed.slice(0, -1) : trimmed;
}

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();

export const env = {
  apiBaseUrl: normalizeBaseUrl(
    configuredApiBaseUrl && configuredApiBaseUrl.length > 0
      ? configuredApiBaseUrl
      : DEFAULT_API_BASE_URL,
  ),
};
