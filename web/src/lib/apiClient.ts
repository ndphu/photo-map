import { env } from "../config/env";
import { useAuthStore } from "../store/authStore";
import { ApiError, isBackendErrorBody } from "../types/api";

export interface RequestOptions<TBody> {
  method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE";
  headers?: HeadersInit;
  body?: TBody;
  signal?: AbortSignal;
  auth?: boolean;
}

let unauthorizedHandler: (() => void) | null = null;

function redirectToLoginFallback(): void {
  if (typeof window === "undefined") {
    return;
  }

  if (window.location.pathname !== "/login") {
    window.location.assign("/login");
  }
}

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

function buildUrl(path: string): string {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${env.apiBaseUrl}${normalizedPath}`;
}

async function parseApiError(response: Response): Promise<ApiError> {
  let fallbackMessage = response.statusText || "Request failed";
  let fallbackCode = "http_error";

  try {
    const data: unknown = await response.json();
    if (isBackendErrorBody(data)) {
      return new ApiError({
        status: response.status,
        code: data.error.code,
        message: data.error.message,
      });
    }
  } catch {
    // Ignore parse errors and use fallback values.
  }

  if (response.status === 401) {
    fallbackCode = "unauthorized";
    fallbackMessage = "Your session has expired. Please log in again.";
  }

  return new ApiError({
    status: response.status,
    code: fallbackCode,
    message: fallbackMessage,
  });
}

export async function apiRequest<TResponse, TBody = never>(
  path: string,
  options: RequestOptions<TBody> = {},
): Promise<TResponse> {
  const { accessToken, logout } = useAuthStore.getState();

  const headers = new Headers(options.headers);
  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  if (options.auth !== false && accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  const response = await fetch(buildUrl(path), {
    method: options.method ?? "GET",
    headers,
    signal: options.signal,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (!response.ok) {
    const error = await parseApiError(response);

    if (response.status === 401) {
      logout();
      if (unauthorizedHandler) {
        unauthorizedHandler();
      } else {
        redirectToLoginFallback();
      }
    }

    throw error;
  }

  if (response.status === 204) {
    return undefined as TResponse;
  }

  const responseText = await response.text();
  if (!responseText) {
    return undefined as TResponse;
  }

  return JSON.parse(responseText) as TResponse;
}
