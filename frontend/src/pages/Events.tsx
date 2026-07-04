import { FormEvent, useEffect, useState } from "react";
import { api } from "../api";
import { useAuth } from "../auth";
import type { EventItem } from "../types";

interface EventsResponse {
  upcoming: EventItem[];
  past: EventItem[];
}

function formatWhen(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

function EventCard({
  event,
  isPast,
  isAdmin,
  onRsvp,
  onDelete,
  index,
}: {
  event: EventItem;
  isPast: boolean;
  isAdmin: boolean;
  onRsvp: (id: string) => void;
  onDelete: (id: string) => void;
  index: number;
}) {
  return (
    <div
      className={`card card-lift animate-fade-in-up ${isPast ? "opacity-70" : ""}`}
      style={{ animationDelay: `${index * 100}ms` }}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-bold uppercase tracking-wide text-indigo-600 dark:text-indigo-400">
            📅 {formatWhen(event.startsAt)}
          </p>
          <h3 className="mt-1 text-lg font-bold text-slate-900 dark:text-slate-100">
            {event.title}
          </h3>
        </div>
        {isAdmin && (
          <button onClick={() => onDelete(event.id)} className="btn-danger flex-none" title="Delete event">
            🗑
          </button>
        )}
      </div>
      <p className="mt-2 whitespace-pre-wrap text-sm text-slate-600 dark:text-slate-400">
        {event.description}
      </p>
      <div className="mt-4 flex flex-wrap items-center gap-3">
        {!isPast && (
          <button
            onClick={() => onRsvp(event.id)}
            className={
              event.viewerRsvped
                ? "btn-primary"
                : "btn-secondary hover:border-indigo-400 hover:text-indigo-600"
            }
          >
            {event.viewerRsvped ? "✓ You're going" : "🙋 I'll attend"}
          </button>
        )}
        <span className="meta">
          {event.rsvpCount} {event.rsvpCount === 1 ? "attendee" : "attendees"}
        </span>
        {event.link && (
          <a
            href={event.link}
            target="_blank"
            rel="noreferrer"
            className="text-sm font-medium text-indigo-600 hover:underline dark:text-indigo-400"
          >
            🔗 Event link
          </a>
        )}
      </div>
    </div>
  );
}

export default function Events() {
  const { user } = useAuth();
  const isAdmin = user?.role === "ADMIN";
  const [data, setData] = useState<EventsResponse | null>(null);
  const [error, setError] = useState("");

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [link, setLink] = useState("");
  const [startsAt, setStartsAt] = useState("");
  const [formError, setFormError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  function load() {
    api<EventsResponse>("/events")
      .then(setData)
      .catch((err) => setError(err.message));
  }

  useEffect(load, []);

  async function handleRsvp(id: string) {
    if (!user) return;
    try {
      const result = await api<{ rsvpCount: number; viewerRsvped: boolean }>(
        `/events/${id}/rsvp`,
        { method: "POST" }
      );
      setData((prev) =>
        prev
          ? {
              upcoming: prev.upcoming.map((e) => (e.id === id ? { ...e, ...result } : e)),
              past: prev.past,
            }
          : prev
      );
    } catch {
      // leave state on failure
    }
  }

  async function handleDelete(id: string) {
    if (!window.confirm("Delete this event?")) return;
    try {
      await api(`/events/${id}`, { method: "DELETE" });
      setData((prev) =>
        prev
          ? {
              upcoming: prev.upcoming.filter((e) => e.id !== id),
              past: prev.past.filter((e) => e.id !== id),
            }
          : prev
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete event");
    }
  }

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setFormError("");
    setSubmitting(true);
    try {
      await api("/events", {
        method: "POST",
        body: JSON.stringify({
          title,
          description,
          link,
          startsAt: startsAt ? new Date(startsAt).toISOString() : "",
        }),
      });
      setTitle("");
      setDescription("");
      setLink("");
      setStartsAt("");
      load();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Failed to create event");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <h1 className="heading animate-fade-in-up mb-1">
        📅 Events & <span className="gradient-text">Webinars</span>
      </h1>
      <p className="meta animate-fade-in-up mb-6 [animation-delay:100ms]">
        Learning sessions, webinars and meetups from the CSCE community.
      </p>

      {isAdmin && (
        <form onSubmit={handleCreate} className="card animate-fade-in-up mb-8 space-y-3">
          <p className="font-semibold text-slate-900 dark:text-slate-100">🛡️ Create an event</p>
          <input
            className="input"
            placeholder="Event title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            minLength={4}
          />
          <textarea
            className="input min-h-20"
            placeholder="What's it about? Who should attend?"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            required
          />
          <div className="grid gap-3 sm:grid-cols-2">
            <input
              type="datetime-local"
              className="input"
              value={startsAt}
              onChange={(e) => setStartsAt(e.target.value)}
              required
            />
            <input
              className="input"
              placeholder="Meeting link (optional)"
              value={link}
              onChange={(e) => setLink(e.target.value)}
            />
          </div>
          {formError && <p className="text-sm text-red-600 dark:text-red-400">{formError}</p>}
          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting ? "Creating…" : "Create event"}
          </button>
        </form>
      )}

      {error && <p className="card text-sm text-red-600 dark:text-red-400">{error}</p>}
      {!error && !data && (
        <p className="py-8 text-center text-slate-500 dark:text-slate-400">Loading…</p>
      )}

      {data && (
        <>
          <div className="space-y-4">
            {data.upcoming.map((event, i) => (
              <EventCard
                key={event.id}
                event={event}
                isPast={false}
                isAdmin={isAdmin}
                onRsvp={handleRsvp}
                onDelete={handleDelete}
                index={i}
              />
            ))}
            {data.upcoming.length === 0 && (
              <div className="card text-center text-slate-500 dark:text-slate-400">
                No upcoming events yet — check back soon! 🌱
              </div>
            )}
          </div>

          {data.past.length > 0 && (
            <>
              <h2 className="mb-3 mt-10 text-lg font-semibold text-slate-900 dark:text-slate-100">
                Past events
              </h2>
              <div className="space-y-4">
                {data.past.map((event, i) => (
                  <EventCard
                    key={event.id}
                    event={event}
                    isPast
                    isAdmin={isAdmin}
                    onRsvp={handleRsvp}
                    onDelete={handleDelete}
                    index={i}
                  />
                ))}
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
}
