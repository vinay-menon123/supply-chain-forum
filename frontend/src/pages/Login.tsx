import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { MEMBER_TYPES } from "../memberTypes";
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
              // First sign-in: pick a member type before landing anywhere else
              if (!data.user.memberType) {
                navigate("/welcome", { replace: true, state: { from } });
              } else {
                navigate(from, { replace: true });
              }
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
    <div className="relative mx-auto max-w-2xl">
      <div className="blob left-[-10rem] top-[-4rem] h-72 w-72 animate-float bg-indigo-500/25 dark:bg-indigo-600/15" />
      <div className="blob right-[-8rem] top-32 h-80 w-80 animate-float-slow bg-fuchsia-500/20 dark:bg-fuchsia-600/10" />

      <h1 className="heading animate-fade-in-up mb-1 text-center">
        Welcome to <span className="gradient-text">CSCE Nexus</span>
      </h1>
      <p className="animate-fade-in-up mb-6 text-center text-sm text-slate-600 [animation-delay:100ms] dark:text-slate-400">
        The collaborative platform for the supply chain ecosystem — sign in with your verified
        Google account to connect, contribute and lead.
      </p>

      <div className="card animate-fade-in-up relative mb-6 flex flex-col items-center gap-4 py-8 [animation-delay:200ms]">
        {status === "loading" && (
          <p className="text-sm text-slate-500 dark:text-slate-400">Loading sign-in…</p>
        )}
        {status === "error" && (
          <p className="text-sm text-red-600 dark:text-red-400">
            Could not load Google Sign-In. Check your connection and refresh.
          </p>
        )}
        {status === "unconfigured" && (
          <div className="px-4 text-sm text-slate-600 dark:text-slate-400">
            <p className="font-medium text-slate-800 dark:text-slate-200">
              Google Sign-In is not configured.
            </p>
            <p className="mt-2">
              Create an OAuth 2.0 Web Client ID in the Google Cloud Console, add this site's
              origin to its authorized JavaScript origins, and set{" "}
              <code className="rounded bg-slate-100 px-1 dark:bg-slate-800">GOOGLE_CLIENT_ID</code>{" "}
              on the backend.
            </p>
          </div>
        )}
        <div ref={buttonRef} className={status === "ready" ? "" : "hidden"} />
        {error && <p className="px-4 text-sm text-red-600 dark:text-red-400">{error}</p>}
      </div>

      <h2 className="animate-fade-in-up mb-3 text-center text-sm font-semibold uppercase tracking-wide text-slate-500 [animation-delay:300ms] dark:text-slate-400">
        One ecosystem, six ways to participate
      </h2>
      <div className="grid gap-3 sm:grid-cols-2">
        {MEMBER_TYPES.map((type, index) => (
          <div
            key={type.value}
            className="card card-lift animate-fade-in-up py-4"
            style={{ animationDelay: `${350 + index * 100}ms` }}
          >
            <p className="font-semibold text-slate-900 dark:text-slate-100">
              {type.emoji} {type.label}
            </p>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{type.description}</p>
          </div>
        ))}
      </div>
      <p className="animate-fade-in mt-6 text-center text-xs font-medium text-slate-400 [animation-delay:900ms] dark:text-slate-500">
        One Mission. One Ecosystem. <span className="gradient-text font-bold">Limitless Impact.</span>
      </p>
    </div>
  );
}
