import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import MemberTypeBadge from "../components/MemberTypeBadge";
import QuestionCard from "../components/QuestionCard";
import type { Profile as ProfileData } from "../types";

function Stat({ value, label }: { value: number; label: string }) {
  return (
    <div className="text-center">
      <div className="text-xl font-bold text-slate-900 dark:text-slate-100">{value}</div>
      <div className="meta">{label}</div>
    </div>
  );
}

export default function Profile() {
  const { username } = useParams<{ username: string }>();
  const { user: viewer } = useAuth();
  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [tab, setTab] = useState<"questions" | "commented">("questions");
  const [error, setError] = useState("");

  useEffect(() => {
    setProfile(null);
    setTab("questions");
    setError("");
    api<ProfileData>(`/users/${username}`)
      .then(setProfile)
      .catch((err) => setError(err.message));
  }, [username]);

  function removeQuestion(id: string) {
    setProfile((prev) =>
      prev
        ? {
            ...prev,
            questions: prev.questions.filter((q) => q.id !== id),
            commented: prev.commented.filter((q) => q.id !== id),
          }
        : prev
    );
  }

  if (error) {
    return (
      <div className="card text-center">
        <p className="text-red-600 dark:text-red-400">{error}</p>
        <Link
          to="/"
          className="mt-3 inline-block text-sm text-indigo-600 hover:underline dark:text-indigo-400"
        >
          ← Back to questions
        </Link>
      </div>
    );
  }
  if (!profile) {
    return <p className="py-8 text-center text-slate-500 dark:text-slate-400">Loading…</p>;
  }

  const { user, stats, questions } = profile;
  const joined = new Date(user.createdAt).toLocaleDateString(undefined, {
    year: "numeric",
    month: "long",
  });
  const isSelf = viewer?.id === user.id;

  return (
    <div className="space-y-6">
      <div className="card">
        <div className="flex flex-wrap items-center gap-4">
          {user.avatarUrl ? (
            <img
              src={user.avatarUrl}
              alt=""
              referrerPolicy="no-referrer"
              className="h-20 w-20 rounded-full border-2 border-indigo-200 shadow-sm dark:border-indigo-800"
            />
          ) : (
            <div className="flex h-20 w-20 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-3xl font-bold text-white">
              {user.username[0].toUpperCase()}
            </div>
          )}
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl font-bold text-slate-900 dark:text-slate-100">
                {user.name || `@${user.username}`}
              </h1>
              <MemberTypeBadge memberType={user.memberType} />
              {user.role === "ADMIN" && (
                <span className="rounded-full bg-gradient-to-r from-amber-400 to-orange-500 px-2 py-0.5 text-xs font-bold text-white shadow-sm">
                  🛡️ ADMIN
                </span>
              )}
            </div>
            {user.name && <p className="meta">@{user.username}</p>}
            <p className="meta mt-1">
              {user.organization ? `${user.organization} · ` : ""}Member since {joined}
            </p>
          </div>
          <div className="flex flex-col items-end gap-2">
            <div className="text-right">
              <div className="text-2xl font-bold text-indigo-600 dark:text-indigo-400">
                {user.reputation ?? 0}
              </div>
              <div className="meta">reputation</div>
            </div>
            {viewer && !isSelf && (
              <Link to={`/messages/${user.username}`} className="btn-primary">
                💬 Message
              </Link>
            )}
          </div>
        </div>
        <div className="mt-5 grid grid-cols-2 gap-4 rounded-lg bg-slate-50 p-4 dark:bg-slate-800/50 sm:grid-cols-4">
          <Stat value={stats.questions} label="Questions" />
          <Stat value={stats.comments} label="Comments" />
          <Stat value={stats.upvotesReceived} label="Upvotes received" />
          <Stat value={stats.accepted} label="Accepted answers" />
        </div>
      </div>

      <section>
        <div className="mb-4 flex items-center gap-1 rounded-full bg-slate-100 p-1 dark:bg-slate-900 w-fit">
          <button
            onClick={() => setTab("questions")}
            className={`pill ${tab === "questions" ? "pill-active" : "pill-inactive"}`}
          >
            ❓ Questions ({profile.questions.length})
          </button>
          <button
            onClick={() => setTab("commented")}
            className={`pill ${tab === "commented" ? "pill-active" : "pill-inactive"}`}
          >
            💬 Commented on ({profile.commented.length})
          </button>
        </div>
        <div className="space-y-4">
          {(tab === "questions" ? questions : profile.commented).map((question) => (
            <QuestionCard key={question.id} question={question} onDeleted={removeQuestion} />
          ))}
          {tab === "questions" && questions.length === 0 && (
            <p className="card text-center text-slate-500 dark:text-slate-400">
              No questions yet.
            </p>
          )}
          {tab === "commented" && profile.commented.length === 0 && (
            <p className="card text-center text-slate-500 dark:text-slate-400">
              {isSelf ? "You haven't" : `@${user.username} hasn't`} commented on any posts yet.
            </p>
          )}
        </div>
      </section>
    </div>
  );
}
