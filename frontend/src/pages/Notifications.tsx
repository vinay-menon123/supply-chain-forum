import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { timeAgo } from "../time";
import type { Notification } from "../types";

const ICON: Record<string, string> = {
  ANSWER: "💬",
  REPLY: "↩️",
  ACCEPT: "✅",
  MENTION: "🔖",
};

export default function Notifications() {
  const [items, setItems] = useState<Notification[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api<{ notifications: Notification[] }>("/notifications")
      .then((d) => setItems(d.notifications))
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load"));
    // Opening the page clears the unread badge.
    api("/notifications/read", { method: "POST" })
      .then(() => window.dispatchEvent(new Event("notif:refresh")))
      .catch(() => {});
  }, []);

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="heading animate-fade-in-up mb-1">🔔 Notifications</h1>
      <p className="meta animate-fade-in-up mb-6 [animation-delay:100ms]">
        Answers, replies, accepted answers, and @mentions.
      </p>

      {error && <p className="card text-sm text-red-400">{error}</p>}
      {!error && !items && (
        <p className="py-10 text-center text-[#8A8F98]">Loading…</p>
      )}
      {items && items.length === 0 && (
        <div className="card text-center text-[#8A8F98]">
          Nothing yet. When someone answers your question, replies to your answer, accepts
          your answer, or @mentions you, it&apos;ll show up here.
        </div>
      )}

      {items && items.length > 0 && (
        <div className="space-y-2">
          {items.map((n) => {
            const inner = (
              <div
                className={`card card-hover flex items-start gap-3 p-4 ${
                  n.read ? "opacity-70" : "border-accent/30 bg-accent/[0.04]"
                }`}
              >
                {n.actor?.avatarUrl ? (
                  <img
                    src={n.actor.avatarUrl}
                    alt=""
                    referrerPolicy="no-referrer"
                    className="h-9 w-9 flex-none rounded-full border border-white/10 object-cover"
                  />
                ) : (
                  <span className="grid h-9 w-9 flex-none place-items-center rounded-full border border-white/10 bg-white/5 text-base">
                    {ICON[n.type] ?? "🔔"}
                  </span>
                )}
                <div className="min-w-0 flex-1">
                  <p className="text-sm leading-snug text-white/90">{n.text}</p>
                  <p className="meta mt-0.5 text-[11px]">{timeAgo(n.createdAt)}</p>
                </div>
                {!n.read && <span className="mt-1.5 h-2 w-2 flex-none rounded-full bg-accent" />}
              </div>
            );
            return n.questionId ? (
              <Link key={n.id} to={`/questions/${n.questionId}`} className="block">
                {inner}
              </Link>
            ) : (
              <div key={n.id}>{inner}</div>
            );
          })}
        </div>
      )}
    </div>
  );
}
