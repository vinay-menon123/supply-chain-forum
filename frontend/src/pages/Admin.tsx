import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import MemberTypeBadge from "../components/MemberTypeBadge";
import { timeAgo } from "../time";
import type { FlaggedUser, User } from "../types";

export default function Admin() {
  const { user } = useAuth();
  const [users, setUsers] = useState<FlaggedUser[] | null>(null);
  const [pending, setPending] = useState<User[] | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    if (user?.role !== "ADMIN") return;
    api<{ users: FlaggedUser[] }>("/admin/flagged")
      .then((data) => setUsers(data.users))
      .catch((err) => setError(err.message));
    api<{ users: User[] }>("/admin/pending")
      .then((data) => setPending(data.users))
      .catch(() => {});
  }, [user]);

  async function runTool(label: string, path: string) {
    setBusy(label);
    setNotice("");
    try {
      const data = await api<{ result: string }>(path, { method: "POST" });
      setNotice(`${label}: ${data.result}`);
    } catch (err) {
      setNotice(`${label} failed: ${err instanceof Error ? err.message : "error"}`);
    } finally {
      setBusy("");
    }
  }

  async function verify(target: User, status: "APPROVED" | "REJECTED") {
    try {
      await api(`/admin/users/${target.id}/verify`, {
        method: "POST",
        body: JSON.stringify({ status }),
      });
      setPending((prev) => prev?.filter((u) => u.id !== target.id) ?? prev);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action failed");
    }
  }

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
      <h1 className="heading mb-2">🛡️ Admin Dashboard</h1>
      <p className="meta mb-6">
        Content containing profanity is auto-removed and the author is flagged. Accounts are
        suspended automatically after 5 flags — you can also ban or unban manually here.
      </p>

      {/* Community tools */}
      <div className="card mb-6">
        <span className="section-title">Community tools</span>
        <div className="mt-3 flex flex-wrap gap-2">
          <button
            onClick={() => runTool("Seed", "/admin/seed")}
            disabled={busy !== ""}
            className="btn-secondary"
          >
            {busy === "Seed" ? "Seeding…" : "🌱 Seed starter discussions"}
          </button>
          <button
            onClick={() => runTool("Digest", "/admin/digest/test")}
            disabled={busy !== ""}
            className="btn-secondary"
          >
            {busy === "Digest" ? "Sending…" : "📧 Send test digest"}
          </button>
        </div>
        {notice && (
          <p className="mt-3 rounded-lg bg-slate-50 p-2 text-sm text-slate-600 dark:bg-slate-800/50 dark:text-slate-300">
            {notice}
          </p>
        )}
      </div>

      {/* Supply-chain verification review */}
      <div className="mb-8">
        <h2 className="mb-1 text-lg font-bold text-slate-900 dark:text-slate-100">
          Membership verification
        </h2>
        <p className="meta mb-3">
          Members whose supply-chain relevance needs a human decision. Approve to add a verified
          badge, or reject to keep them flagged for review.
        </p>
        {pending && pending.length === 0 && (
          <div className="card text-center text-slate-500 dark:text-slate-400">
            Nobody awaiting review. ✨
          </div>
        )}
        <div className="space-y-3">
          {pending?.map((p) => (
            <div key={p.id} className="card flex flex-wrap items-center gap-3">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <Link to={`/users/${p.username}`} className="username-link font-semibold">
                    {p.name ?? `@${p.username}`}
                  </Link>
                  <MemberTypeBadge memberType={p.memberType} small />
                  <span
                    className={`badge ${
                      p.verifyStatus === "REJECTED"
                        ? "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300"
                        : "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300"
                    }`}
                  >
                    {p.verifyStatus === "REJECTED" ? "AI: not sure" : "pending"}
                  </span>
                </div>
                {p.headline && <p className="meta mt-0.5">{p.headline}</p>}
                {p.linkedinUrl && (
                  <a
                    href={p.linkedinUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="text-xs text-sky-600 hover:underline dark:text-sky-400"
                  >
                    {p.linkedinUrl} ↗
                  </a>
                )}
              </div>
              <div className="flex gap-2">
                <button onClick={() => verify(p, "APPROVED")} className="btn-primary text-sm">
                  ✅ Approve
                </button>
                <button
                  onClick={() => verify(p, "REJECTED")}
                  className="btn-secondary text-sm text-red-600 dark:text-red-400"
                >
                  Reject
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>

      <h2 className="mb-3 text-lg font-bold text-slate-900 dark:text-slate-100">Flagged members</h2>

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
