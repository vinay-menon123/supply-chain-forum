import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
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
  const [error, setError] = useState("");

  useEffect(() => {
    setProfile(null);
    setError("");
    api<ProfileData>(`/users/${username}`)
      .then(setProfile)
      .catch((err) => setError(err.message));
  }, [username]);

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
              {user.role === "ADMIN" && (
                <span className="rounded-full bg-gradient-to-r from-amber-400 to-orange-500 px-2 py-0.5 text-xs font-bold text-white shadow-sm">
                  🛡️ ADMIN
                </span>
              )}
            </div>
            {user.name && <p className="meta">@{user.username}</p>}
            <p className="meta mt-1">Member since {joined}</p>
          </div>
          {viewer && !isSelf && (
            <Link to={`/messages/${user.username}`} className="btn-primary">
              💬 Message
            </Link>
          )}
        </div>
        <div className="mt-5 grid grid-cols-3 gap-4 rounded-lg bg-slate-50 p-4 dark:bg-slate-800/50">
          <Stat value={stats.questions} label="Questions" />
          <Stat value={stats.comments} label="Comments" />
          <Stat value={stats.upvotesReceived} label="Upvotes received" />
        </div>
      </div>

      <section>
        <h2 className="mb-3 text-lg font-semibold text-slate-900 dark:text-slate-100">
          {isSelf ? "Your questions" : `Questions by @${user.username}`}
        </h2>
        <div className="space-y-4">
          {questions.map((question) => (
            <QuestionCard key={question.id} question={question} />
          ))}
          {questions.length === 0 && (
            <p className="card text-center text-slate-500 dark:text-slate-400">
              No questions yet.
            </p>
          )}
        </div>
      </section>
    </div>
  );
}
