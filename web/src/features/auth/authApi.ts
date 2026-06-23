import { apiRequest } from "../../lib/apiClient";
import type { AuthResponse, LoginRequest } from "../../types/auth";

export function login(request: LoginRequest): Promise<AuthResponse> {
  return apiRequest<AuthResponse, LoginRequest>("/auth/login", {
    method: "POST",
    body: request,
    auth: false,
  });
}
