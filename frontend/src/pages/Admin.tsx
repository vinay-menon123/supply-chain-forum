import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { timeAgo } from "../time";
import type { FlaggedUser } from "../types";

export default function Admin() {
  const { user } = useAuth();
  const [users, setUsers] = useState<FlaggedUser[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (user?.role !== "ADMIN") return;
    api<{ users: FlaggedUser[] }>("/admin/flagged")
      .then((data) => setUsers(data.users))
      .catch((err) => setError(err.message));
  }, [user]);

  async function setBan(target: FlaggedUser, banned: boolean) {
    if (banned && !window.confirm(`Ban @${target.username}? They will no longer be able to post.`)) {
      return;
    }
    try {
      const updated = await api<{ id: string; isBanned: boolean; flagCount: number }>(
        `/admin/users/${target.id}/ban`,
        { method: "POST", body: JSON.stringify({ banned }) }
      );
      setUsers((prev) =>
        prev
          ? prev.map((u) =>
              u.id === updated.id
                ? { ...u, isBanned: updated.isBanned, flagCount: updated.flagCount }
                : u
            )
          : prev
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action failed");
    }
  }

  if (user && user.role !== "ADMIN") {
    return (
      <div className="card text-center text-slate-500 dark:text-slate-400">
        This page is for administrators only.
      </div>
    );
  }

  return (
    <div>
      <h1 className="heading mb-2">🛡️ Moderation Dashboard</h1>
      <p className="meta mb-6">
        Content containing profanity is auto-removed and the author is flagged. Accounts are
        suspended automatically after 5 flags — you can also ban or unban manually here.
      </p>

      {error && <p className="card mb-4 text-sm text-red-600 dark:text-red-400">{error}</p>}
      {!error && !users && (
        <p className="py-8 text-center text-slate-500 dark:text-slate-400">Loading…</p>
      )}
      {users && users.length === 0 && (
        <div className="card text-center text-slate-500 dark:text-slate-400">
          No flagged users. All clear! ✨
        </div>
      )}

      <div className="space-y-4">
        {users?.map((flagged) => (
          <div key={flagged.id} className="card">
            <div className="flex flex-wrap items-center gap-3">
              {flagged.avatarUrl ? (
                <img
                  src={flagged.avatarUrl}
                  alt=""
                  referrerPolicy="no-referrer"
                  className="h-10 w-10 rounded-full border border-slate-200 dark:border-slate-700"
                />
              ) : (
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-300 font-bold text-slate-700 dark:bg-slate-700 dark:text-slate-200">
                  {flagged.username[0].toUpperCase()}
                </div>
              )}
              <div className="min-w-0 flex-1">
                <Link to={`/users/${flagged.username}`} className="username-link font-semibold">
                  @{flagged.username}
                </Link>
                <div className="meta">
                  {flagged.flagCount} {flagged.flagCount === 1 ? "flag" : "flags"}
                  {flagged.isBanned && (
                    <span className="ml-2 rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-700 dark:bg-red-950 dark:text-red-300">
                      SUSPENDED
                    </span>
                  )}
                </div>
              </div>
              {flagged.isBanned ? (
                <button onClick={() => setBan(flagged, false)} className="btn-secondary">
                  Unban
                </button>
              ) : (
                <button
                  onClick={() => setBan(flagged, true)}
                  className="btn-secondary text-red-600 dark:text-red-400"
                >
                  Ban
                </button>
              )}
            </div>
            {flagged.moderationEvents.length > 0 && (
              <div className="mt-3 space-y-1.5 border-t border-slate-100 pt-3 dark:border-slate-800">
                {flagged.moderationEvents.map((event, i) => (
                  <p key={i} className="meta">
                    <span className="font-medium uppercase">{event.kind}</span> ·{" "}
                    {timeAgo(event.createdAt)} —{" "}
                    <span className="italic">"{event.content.slice(0, 120)}"</span>
                  </p>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
