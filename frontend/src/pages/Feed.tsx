import { FormEvent, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import QuestionCard from "../components/QuestionCard";
import TopicPicker, { formatTopics, parseTopics } from "../components/TopicPicker";
import { TAGS, tagMeta } from "../tags";
import type { QuestionList, User } from "../types";

function FollowTopics() {
  const { user, updateUser } = useAuth();
  const [editing, setEditing] = useState(false);
  const [topics, setTopics] = useState<string[]>(parseTopics(user?.topics));
  const [saving, setSaving] = useState(false);

  if (!user) return null;
  const followed = parseTopics(user.topics);

  function toggle(value: string) {
    setTopics((prev) =>
      prev.includes(value) ? prev.filter((t) => t !== value) : [...prev, value]
    );
  }

  async function save() {
    setSaving(true);
    try {
      const data = await api<{ user: User }>("/auth/profile", {
        method: "POST",
        body: JSON.stringify({
          memberType: user!.memberType,
          headline: user!.headline ?? "",
          linkedinUrl: user!.linkedinUrl ?? "",
          phone: user!.phone ?? "",
          organization: user!.organization ?? "",
          bio: user!.bio ?? "",
          topics: formatTopics(topics),
          openToMentor: user!.openToMentor,
          seekingMentor: user!.seekingMentor,
        }),
      });
      updateUser(data.user);
      setEditing(false);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="card animate-fade-in-up mb-5 border-indigo-100 bg-gradient-to-br from-indigo-50/60 to-violet-50/40 dark:border-indigo-950 dark:from-slate-900 dark:to-slate-900">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-semibold text-slate-800 dark:text-slate-100">
          🔔 Topics you follow
        </span>
        <button
          onClick={() => {
            setTopics(parseTopics(user!.topics));
            setEditing((v) => !v);
          }}
          className="btn-ghost text-xs"
        >
          {editing ? "Cancel" : followed.length ? "Edit" : "Choose topics"}
        </button>
      </div>
      {!editing && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {followed.length === 0 ? (
            <p className="meta">
              Follow topics to get an email when new questions are posted in them.
            </p>
          ) : (
            followed.map((t) => (
              <span
                key={t}
                className="badge bg-white text-indigo-700 shadow-sm dark:bg-slate-800 dark:text-indigo-300"
              >
                {tagMeta(t).emoji} {tagMeta(t).label}
              </span>
            ))
          )}
        </div>
      )}
      {editing && (
        <div className="mt-3">
          <TopicPicker selected={topics} onToggle={toggle} />
          <button onClick={save} className="btn-primary mt-3 text-sm" disabled={saving}>
            {saving ? "Saving…" : "Save topics"}
          </button>
        </div>
      )}
    </div>
  );
}

export default function Feed() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const q = params.get("q") ?? "";
  const sort = params.get("sort") === "top" ? "top" : "new";
  const tag = params.get("tag") ?? "";
  const [search, setSearch] = useState(q);
  const [data, setData] = useState<QuestionList | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    setData(null);
    setError("");
    const query = new URLSearchParams();
    if (q) query.set("q", q);
    if (sort === "top") query.set("sort", "top");
    if (tag) query.set("tag", tag);
    const qs = query.toString();
    api<QuestionList>(`/questions${qs ? `?${qs}` : ""}`)
      .then(setData)
      .catch((err) => setError(err.message));
  }, [q, sort, tag]);

  function updateParams(nextQ: string, nextSort: string, nextTag: string) {
    const next: Record<string, string> = {};
    if (nextQ) next.q = nextQ;
    if (nextSort === "top") next.sort = "top";
    if (nextTag) next.tag = nextTag;
    setParams(next);
  }

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    updateParams(search.trim(), sort, tag);
  }

  return (
    <div>
      <div className="animate-fade-in-up mb-6 flex flex-wrap items-center justify-between gap-3">
        <h1 className="heading">
          {q ? `Results for "${q}"` : (
            <>
              Community <span className="gradient-text">Questions</span>
            </>
          )}
        </h1>
        <Link to={user ? "/ask" : "/login"} className="btn-primary flex-none">
          Ask Question
        </Link>
      </div>

      <form onSubmit={handleSearch} className="mb-4 flex gap-2">
        <input
          className="input"
          placeholder="Search questions…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <button type="submit" className="btn-secondary flex-none">
          Search
        </button>
      </form>

      <div className="mb-3 flex items-center gap-1 rounded-full bg-slate-100 p-1 dark:bg-slate-900 w-fit">
        <button
          onClick={() => updateParams(q, "new", tag)}
          className={`pill ${sort === "new" ? "pill-active" : "pill-inactive"}`}
        >
          🕘 Newest
        </button>
        <button
          onClick={() => updateParams(q, "top", tag)}
          className={`pill ${sort === "top" ? "pill-active" : "pill-inactive"}`}
        >
          🔥 Top
        </button>
      </div>

      <div className="mb-5 flex gap-1.5 overflow-x-auto pb-1">
        <button
          onClick={() => updateParams(q, sort, "")}
          className={`pill flex-none text-xs ${tag === "" ? "pill-active" : "pill-inactive bg-slate-100 dark:bg-slate-900"}`}
        >
          All topics
        </button>
        {TAGS.map((t) => (
          <button
            key={t.value}
            onClick={() => updateParams(q, sort, t.value)}
            className={`pill flex-none text-xs ${tag === t.value ? "pill-active" : "pill-inactive bg-slate-100 dark:bg-slate-900"}`}
          >
            {t.emoji} {t.label}
          </button>
        ))}
      </div>

      {user && !q && <FollowTopics />}

      {error && <p className="card text-sm text-red-600 dark:text-red-400">{error}</p>}
      {!error && !data && (
        <p className="py-8 text-center text-slate-500 dark:text-slate-400">Loading…</p>
      )}

      {data && data.questions.length === 0 && (
        <div className="card text-center text-slate-500 dark:text-slate-400">
          {q ? "No questions match your search." : "No questions yet — be the first to ask one! 🚀"}
        </div>
      )}

      <div className="space-y-4">
        {data?.questions.map((question, index) => (
          <div
            key={question.id}
            className="animate-fade-in-up"
            style={{ animationDelay: `${Math.min(index, 8) * 70}ms` }}
          >
          <QuestionCard
            question={question}
            onDeleted={(id) =>
              setData((prev) =>
                prev
                  ? {
                      ...prev,
                      questions: prev.questions.filter((q) => q.id !== id),
                      total: prev.total - 1,
                    }
                  : prev
              )
            }
          />
          </div>
        ))}
      </div>
    </div>
  );
}
