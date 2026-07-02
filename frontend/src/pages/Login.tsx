import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import type { User } from "../types";

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: {
            client_id: string;
            callback: (response: { credential: string }) => void;
          }) => void;
          renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void;
        };
      };
    };
  }
}

let gsiScript: Promise<void> | null = null;

function loadGoogleScript(): Promise<void> {
  if (window.google?.accounts) return Promise.resolve();
  if (!gsiScript) {
    gsiScript = new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = "https://accounts.google.com/gsi/client";
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error("Failed to load Google Sign-In"));
      document.head.appendChild(script);
    });
  }
  return gsiScript;
}

type Status = "loading" | "ready" | "unconfigured" | "error";

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/";

  const buttonRef = useRef<HTMLDivElement>(null);
  const [status, setStatus] = useState<Status>("loading");
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;

    async function setup() {
      try {
        const { googleClientId } = await api<{ googleClientId: string }>("/auth/config");
        if (cancelled) return;
        if (!googleClientId) {
          setStatus("unconfigured");
          return;
        }
        await loadGoogleScript();
        if (cancelled || !window.google || !buttonRef.current) return;

        window.google.accounts.id.initialize({
          client_id: googleClientId,
          callback: async (response) => {
            try {
              const data = await api<{ token: string; user: User }>("/auth/google", {
                method: "POST",
                body: JSON.stringify({ credential: response.credential }),
              });
              login(data.token, data.user);
              navigate(from, { replace: true });
            } catch (err) {
              setError(err instanceof Error ? err.message : "Sign-in failed");
            }
          },
        });
        buttonRef.current.innerHTML = "";
        window.google.accounts.id.renderButton(buttonRef.current, {
          theme: "outline",
          size: "large",
          text: "signin_with",
          width: 280,
        });
        setStatus("ready");
      } catch {
        if (!cancelled) setStatus("error");
      }
    }

    setup();
    return () => {
      cancelled = true;
    };
  }, [from, login, navigate]);

  return (
    <div className="mx-auto max-w-md">
      <h1 className="mb-2 text-center text-2xl font-bold text-slate-900">Welcome</h1>
      <p className="mb-6 text-center text-sm text-slate-600">
        Sign in with Google — we use your verified Google email, so no passwords to remember.
      </p>
      <div className="card flex flex-col items-center gap-4 py-8">
        {status === "loading" && <p className="text-sm text-slate-500">Loading sign-in…</p>}
        {status === "error" && (
          <p className="text-sm text-red-600">
            Could not load Google Sign-In. Check your connection and refresh.
          </p>
        )}
        {status === "unconfigured" && (
          <div className="px-4 text-sm text-slate-600">
            <p className="font-medium text-slate-800">Google Sign-In is not configured.</p>
            <p className="mt-2">
              Create an OAuth 2.0 Web Client ID in the Google Cloud Console, add this site's
              origin to its authorized JavaScript origins, and set{" "}
              <code className="rounded bg-slate-100 px-1">GOOGLE_CLIENT_ID</code> on the
              backend.
            </p>
          </div>
        )}
        <div ref={buttonRef} className={status === "ready" ? "" : "hidden"} />
        {error && <p className="px-4 text-sm text-red-600">{error}</p>}
      </div>
    </div>
  );
}
