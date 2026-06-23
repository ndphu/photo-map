import { useMutation } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { login } from "../features/auth/authApi";
import { ApiError } from "../types/api";
import { useAuthStore } from "../store/authStore";

interface LoginLocationState {
  from?: string;
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const setSession = useAuthStore((state) => state.setSession);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const fromPath =
    (location.state as LoginLocationState | null)?.from ?? "/gallery";

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (result) => {
      setSession(result.accessToken, result.user);
      navigate(fromPath, { replace: true });
    },
  });

  useEffect(() => {
    if (isAuthenticated) {
      navigate("/gallery", { replace: true });
    }
  }, [isAuthenticated, navigate]);

  const errorMessage = useMemo(() => {
    if (!mutation.error) {
      return null;
    }
    if (mutation.error instanceof ApiError) {
      return mutation.error.message;
    }
    return "Login failed. Please try again.";
  }, [mutation.error]);

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    mutation.mutate({
      email: email.trim(),
      password,
    });
  };

  return (
    <main className="login-page">
      <section className="login-card">
        <h1 className="login-title">Private Cloud Gallery</h1>
        <p className="login-subtitle">
          Sign in to access your personal photo library.
        </p>

        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>

          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>

          {errorMessage ? (
            <div className="error-banner" role="alert">
              {errorMessage}
            </div>
          ) : null}

          <button
            type="submit"
            className="primary-btn"
            disabled={mutation.isPending}
          >
            {mutation.isPending ? "Signing in..." : "Sign in"}
          </button>
        </form>
      </section>
    </main>
  );
}
