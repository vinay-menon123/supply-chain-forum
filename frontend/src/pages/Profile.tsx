import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import MemberTypeBadge from "../components/MemberTypeBadge";
import QuestionCard from "../components/QuestionCard";
import type { Profile as ProfileData, User } from "../types";
import TopicPicker, { formatTopics, parseTopics } from "../components/TopicPicker";
import { tagMeta } from "../tags";

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
  const { user: viewer, updateUser } = useAuth();
  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [tab, setTab] = useState<"questions" | "commented">("questions");
  const [error, setError] = useState("");

  const [editingTopics, setEditingTopics] = useState(false);
  const [selfTopics, setSelfTopics] = useState<string[]>([]);
  const [savingSelfTopics, setSavingSelfTopics] = useState(false);

  useEffect(() => {
    if (profile?.user) {
      setSelfTopics(parseTopics(profile.user.topics));
    }
  }, [profile]);

  function toggleSelfTopic(value: string) {
    setSelfTopics((prev) =>
      prev.includes(value) ? prev.filter((t) => t !== value) : [...prev, value]
    );
  }

  async function saveSelfTopics() {
    if (!profile?.user) return;
    setSavingSelfTopics(true);
    try {
      const data = await api<{ user: User }>("/auth/profile", {
        method: "POST",
        body: JSON.stringify({
          memberType: profile.user.memberType,
          headline: profile.user.headline ?? "",
          linkedinUrl: profile.user.linkedinUrl ?? "",
          phone: profile.user.phone ?? "",
          organization: profile.user.organization ?? "",
          bio: profile.user.bio ?? "",
          topics: formatTopics(selfTopics),
          openToMentor: profile.user.openToMentor,
          seekingMentor: profile.user.seekingMentor,
        }),
      });
      setProfile(prev => prev ? { ...prev, user: data.user } : prev);
      if (viewer && viewer.id === data.user.id) {
        updateUser(data.user);
      }
      setEditingTopics(false);
    } catch (err) {
      alert("Failed to save topics: " + (err instanceof Error ? err.message : String(err)));
    } finally {
      setSavingSelfTopics(false);
    }
  }

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
              {user.verifyStatus === "APPROVED" && (
                <span className="badge bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">
                  ✅ Verified
                </span>
              )}
              {user.pro && (
                <span className="badge bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300">
                  ⭐ Pro Supplier
                </span>
              )}
              {user.role === "ADMIN" && (
                <span className="rounded-full bg-gradient-to-r from-amber-400 to-orange-500 px-2 py-0.5 text-xs font-bold text-white shadow-sm">
                  🛡️ ADMIN
                </span>
              )}
            </div>
            {user.headline && (
              <p className="mt-0.5 text-sm font-medium text-slate-700 dark:text-slate-300">
                {user.headline}
              </p>
            )}
            {user.name && <p className="meta">@{user.username}</p>}
            <p className="meta mt-1">
              {user.organization ? `${user.organization} · ` : ""}Member since {joined}
            </p>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              {user.openToMentor && (
                <span className="badge bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">
                  🎓 Mentor
                </span>
              )}
              {user.seekingMentor && (
                <span className="badge bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300">
                  🌱 Seeking mentor
                </span>
              )}
              {user.linkedinUrl && (
                <a
                  href={user.linkedinUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="badge bg-sky-100 text-sky-700 hover:bg-sky-200 dark:bg-sky-950 dark:text-sky-300"
                >
                  in · LinkedIn ↗
                </a>
              )}
            </div>
          </div>
          <div className="flex flex-col items-end gap-2">
            <div className="text-right">
              <div className="text-2xl font-bold text-indigo-600 dark:text-indigo-400">
                {user.reputation ?? 0}
              </div>
              <div className="meta">reputation</div>
            </div>
            {isSelf ? (
              <Link to="/settings" className="btn-secondary">
                ⚙️ Edit profile
              </Link>
            ) : (
              viewer && (
                <Link to={`/messages/${user.username}`} className="btn-primary">
                  💬 Message
                </Link>
              )
            )}
          </div>
        </div>
        {user.bio && (
          <p className="mt-4 rounded-xl bg-slate-50 p-3 text-sm text-slate-600 dark:bg-slate-800/50 dark:text-slate-300">
            {user.bio}
          </p>
        )}
        
        {/* Favored SCM Domains / Followed Topics Section */}
        <div className="mt-4 pt-4 border-t border-slate-200 dark:border-white/[0.06] text-left">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">
              🔔 Favored SCM Domains (Email Notifications)
            </span>
            {isSelf && (
              <button
                type="button"
                onClick={() => setEditingTopics(!editingTopics)}
                className="text-[11px] text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300 font-semibold"
              >
                {editingTopics ? "Cancel" : "Edit Topics"}
              </button>
            )}
          </div>

          {isSelf && editingTopics ? (
            <div className="space-y-3">
              <TopicPicker selected={selfTopics} onToggle={toggleSelfTopic} />
              <button
                type="button"
                onClick={saveSelfTopics}
                className="btn-primary text-xs py-1.5 px-3 rounded-lg font-semibold"
                disabled={savingSelfTopics}
              >
                {savingSelfTopics ? "Saving..." : "Save Favored Domains"}
              </button>
            </div>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {selfTopics.length === 0 ? (
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {isSelf 
                    ? "No domains followed. Click Edit to subscribe to real-time email notifications."
                    : "No SCM domains followed yet."}
                </p>
              ) : (
                selfTopics.map((t) => {
                  const meta = tagMeta(t);
                  return (
                    <span
                      key={t}
                      className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-800 dark:bg-white/5 dark:text-indigo-300 border border-slate-200 dark:border-white/[0.04]"
                    >
                      {meta.emoji} {meta.label}
                    </span>
                  );
                })
              )}
            </div>
          )}
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
