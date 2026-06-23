export interface BackendErrorBody {
  error: {
    code: string;
    message: string;
  };
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(params: { status: number; code: string; message: string }) {
    super(params.message);
    this.name = "ApiError";
    this.status = params.status;
    this.code = params.code;
  }
}

export function isBackendErrorBody(value: unknown): value is BackendErrorBody {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const maybeError = (value as { error?: unknown }).error;
  if (typeof maybeError !== "object" || maybeError === null) {
    return false;
  }

  const code = (maybeError as { code?: unknown }).code;
  const message = (maybeError as { message?: unknown }).message;

  return typeof code === "string" && typeof message === "string";
}
