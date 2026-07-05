import { FormEvent, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import QuestionCard from "../components/QuestionCard";
import TopicPicker, { formatTopics, parseTopics } from "../components/TopicPicker";
import { TAGS, tagMeta } from "../tags";
import type { EventItem, QuestionList, User } from "../types";

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
    <div className="card animate-fade-in-up mb-5 border-indigo-950 bg-gradient-to-br from-slate-900 to-slate-900 p-5 rounded-xl">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-semibold text-slate-100">
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
                className="badge bg-slate-800 text-indigo-300"
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
          <button onClick={save} className="btn-primary mt-3 text-xs" disabled={saving}>
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

  // Upcoming events for the sidebar widget
  const [events, setEvents] = useState<EventItem[]>([]);



  useEffect(() => {
    window.scrollTo(0, 0);
    // Load the next few upcoming events for the sidebar widget.
    api<{ upcoming: EventItem[] }>("/events")
      .then((d) => setEvents(d.upcoming.slice(0, 4)))
      .catch(() => {});
  }, []);

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

  async function handleRsvp(id: string) {
    try {
      const data = await api<{ rsvpCount: number; viewerRsvped: boolean }>(`/events/${id}/rsvp`, {
        method: "POST",
      });
      setEvents((prev) =>
        prev.map((e) =>
          e.id === id ? { ...e, rsvpCount: data.rsvpCount, viewerRsvped: data.viewerRsvped } : e
        )
      );
    } catch {
      /* RSVP failures are non-critical for the sidebar widget */
    }
  }

  const eventWhen = (iso: string) =>
    new Date(iso).toLocaleString(undefined, {
      month: "short",
      day: "numeric",
      hour: "numeric",
      minute: "2-digit",
    });



  return (
    <div className="space-y-10">
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-8 items-start">
        {/* Main Feed Content (Left 3 columns on desktop) */}
        <div className="lg:col-span-3 space-y-6">
          <div className="animate-fade-in-up flex flex-wrap items-center justify-between gap-3">
            <h1 className="heading">
              {q ? `Results for "${q}"` : (
                <>
                  Community <span className="gradient-text">Questions</span>
                </>
              )}
            </h1>
            <Link to={user ? "/ask" : "/login"} className="btn-primary flex-none py-1.5 px-4 text-xs font-semibold">
              Ask Question
            </Link>
          </div>

          <form onSubmit={handleSearch} className="flex gap-2.5">
            <input
              className="input py-2 text-sm flex-1"
              placeholder="Search questions…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <button type="submit" className="btn-secondary flex-none px-4 text-xs font-semibold">
              Search
            </button>
          </form>

          <div className="flex items-center gap-1 rounded-full bg-white/5 border border-white/[0.06] p-1 w-fit select-none">
            <button
              onClick={() => updateParams(q, "new", tag)}
              className={`pill text-xs px-3.5 py-1.5 ${sort === "new" ? "pill-active" : "pill-inactive"}`}
            >
              For You
            </button>
            <button
              onClick={() => updateParams(q, "top", tag)}
              className={`pill text-xs px-3.5 py-1.5 ${sort === "top" ? "pill-active" : "pill-inactive"}`}
            >
              🔥 Top
            </button>
          </div>

          <div className="flex flex-wrap gap-2 select-none">
            <button
              onClick={() => updateParams(q, sort, "")}
              className={`pill flex-none text-xs px-3 py-1.5 ${tag === "" ? "pill-active" : "pill-inactive bg-white/[0.02] border-white/[0.04]"}`}
            >
              All topics
            </button>
            {TAGS.map((t) => (
              <button
                key={t.value}
                onClick={() => updateParams(q, sort, t.value)}
                className={`pill flex-none text-xs px-3 py-1.5 ${tag === t.value ? "pill-active" : "pill-inactive bg-white/[0.02] border-white/[0.04]"}`}
              >
                {t.emoji} {t.label}
              </button>
            ))}
          </div>

          {user && !q && <FollowTopics />}

          {error && <p className="card text-sm text-red-500 bg-red-950/10 border border-red-500/20 p-4 rounded-lg">⚠️ {error}</p>}
          {!error && !data && (
            <p className="py-8 text-center text-[#8A8F98]">Loading…</p>
          )}

          {data && data.questions.length === 0 && (
            <div className="card text-center text-[#8A8F98] bg-white/[0.02] border-white/[0.06] p-6 rounded-xl">
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

        {/* Sidebar Widgets (Right 1 column on desktop) */}
        <div className="lg:col-span-1 space-y-6">
          <div className="card bg-gradient-to-b from-white/[0.04] to-white/[0.01] border-white/[0.06] p-5 rounded-xl space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-[10px] font-semibold text-accent uppercase tracking-widest font-mono">
                  Interact
                </span>
                <h2 className="text-sm font-semibold tracking-tight text-white mt-0.5">
                  Upcoming Events
                </h2>
              </div>
              <Link to="/events" className="text-[10px] font-semibold text-accent hover:text-white transition-colors">
                See all →
              </Link>
            </div>

            <div className="space-y-4">
              {events.length === 0 ? (
                <p className="text-[11px] text-[#8A8F98] leading-relaxed">
                  No upcoming events yet — check back soon, or{" "}
                  <Link to="/events" className="text-accent hover:underline">
                    browse past sessions
                  </Link>
                  .
                </p>
              ) : (
                events.map((ev) => (
                  <div
                    key={ev.id}
                    className="border-b border-white/[0.04] pb-3 last:border-b-0 last:pb-0"
                  >
                    <span className="text-[9px] bg-accent/15 text-accent border border-accent/20 px-1.5 py-0.2 rounded font-bold uppercase tracking-wider font-mono">
                      📅 Event
                    </span>
                    <h4 className="text-xs font-semibold text-white mt-1.5 leading-snug">
                      {ev.title}
                    </h4>
                    {ev.host?.username && (
                      <p className="text-[10px] text-[#8A8F98] mt-0.5 font-sans">
                        Hosted by @{ev.host.username}
                      </p>
                    )}
                    <div className="flex items-center justify-between mt-2.5">
                      <span className="text-[9px] text-[#8A8F98] font-mono">
                        {eventWhen(ev.startsAt)}
                        {ev.rsvpCount > 0 && ` • ${ev.rsvpCount} going`}
                      </span>
                      <button
                        onClick={() => handleRsvp(ev.id)}
                        className={`text-[9px] font-semibold py-1 px-2.5 rounded transition ${
                          ev.viewerRsvped
                            ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                            : "bg-white/5 border border-white/10 text-[#EDEDEF] hover:bg-white/8"
                        }`}
                      >
                        {ev.viewerRsvped ? "✓ RSVP'd" : "RSVP"}
                      </button>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>

    </div>
  );
}
