import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { TAGS, tagMeta } from "../tags";
import { timeAgo } from "../time";
import type { Job } from "../types";

const TYPES: { value: Job["employmentType"]; label: string }[] = [
  { value: "FULL_TIME", label: "Full-time" },
  { value: "PART_TIME", label: "Part-time" },
  { value: "CONTRACT", label: "Contract" },
  { value: "INTERNSHIP", label: "Internship" },
];

function typeLabel(v: string): string {
  return TYPES.find((t) => t.value === v)?.label ?? "Full-time";
}

const EMPTY_FORM = {
  title: "",
  company: "",
  location: "",
  employmentType: "FULL_TIME",
  tag: "GENERAL",
  salary: "",
  applyUrl: "",
  description: "",
};

function JobMeta({ job }: { job: Job }) {
  const meta = tagMeta(job.tag);
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <span className="pill pill-inactive text-[10px] py-0.5 px-2.5 select-none">
        {meta.emoji} {meta.label}
      </span>
      <span className="badge border-accent/20 bg-accent/10 text-accent">{typeLabel(job.employmentType)}</span>
      {job.salary && (
        <span className="badge border-emerald-500/20 bg-emerald-500/10 text-emerald-400">💰 {job.salary}</span>
      )}
    </div>
  );
}

function ApplyAction({ job, size = "sm" }: { job: Job; size?: "sm" | "lg" }) {
  const { user } = useAuth();
  const cls = size === "lg" ? "py-2 px-5 text-sm" : "py-1.5 px-4 text-xs";
  if (job.applyUrl) {
    return (
      <a href={job.applyUrl} target="_blank" rel="noreferrer" className={`btn-primary ${cls}`}>
        Apply →
      </a>
    );
  }
  if (user && user.username !== job.author.username) {
    return (
      <Link to={`/messages/${job.author.username}`} className={`btn-primary ${cls}`}>
        Message poster →
      </Link>
    );
  }
  if (!user) {
    return (
      <Link to="/login" className={`btn-secondary ${cls}`}>
        Log in to apply
      </Link>
    );
  }
  return <span className="text-xs text-[#8A8F98]">Your posting</span>;
}

function JobCard({
  job,
  canDelete,
  onOpen,
  onDelete,
  index,
}: {
  job: Job;
  canDelete: boolean;
  onOpen: (j: Job) => void;
  onDelete: (id: string) => void;
  index: number;
}) {
  const handleCardClick = (e: React.MouseEvent) => {
    const target = e.target as HTMLElement;
    if (target.closest("button") || target.closest("a")) return;
    onOpen(job);
  };
  return (
    <div
      onClick={handleCardClick}
      className="card card-lift animate-fade-in-up flex cursor-pointer flex-col"
      style={{ animationDelay: `${Math.min(index, 8) * 60}ms` }}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-base font-bold leading-snug text-white break-words hover:text-accent">{job.title}</h3>
          <p className="mt-0.5 text-sm text-[#8A8F98]">
            <span className="font-medium text-white/80">{job.company}</span>
            {job.location && <> · {job.location}</>}
          </p>
        </div>
        {canDelete && (
          <button onClick={() => onDelete(job.id)} className="btn-danger flex-none py-1 px-2.5 text-xs" title="Delete job">
            🗑
          </button>
        )}
      </div>

      <div className="mt-3">
        <JobMeta job={job} />
      </div>

      <p className="mt-3 line-clamp-3 whitespace-pre-wrap break-words text-sm text-[#8A8F98] leading-relaxed">
        {job.description}
      </p>

      <div className="mt-auto flex flex-wrap items-center justify-between gap-x-3 gap-y-2 border-t border-white/[0.04] pt-3">
        <ApplyAction job={job} />
        <span className="meta text-[11px]">
          <Link to={`/users/${job.author.username}`} className="username-link">
            @{job.author.username}
          </Link>{" "}
          · {timeAgo(job.createdAt)}
        </span>
      </div>
    </div>
  );
}

function JobModal({
  job,
  canDelete,
  onClose,
  onDelete,
}: {
  job: Job;
  canDelete: boolean;
  onClose: () => void;
  onDelete: (id: string) => void;
}) {
  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="card animate-scale-in relative max-h-[85vh] w-full max-w-lg overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          onClick={onClose}
          className="absolute right-3 top-3 grid h-8 w-8 place-items-center rounded-lg text-[#8A8F98] hover:bg-white/[0.06] hover:text-white"
          aria-label="Close"
        >
          ✕
        </button>

        <div className="pr-8">
          <h2 className="text-xl font-bold leading-snug text-white break-words">{job.title}</h2>
          <p className="mt-1 text-sm text-[#8A8F98]">
            <span className="font-medium text-white/80">{job.company}</span>
            {job.location && <> · {job.location}</>}
          </p>
        </div>

        <div className="mt-3">
          <JobMeta job={job} />
        </div>

        <p className="mt-4 whitespace-pre-wrap break-words text-sm leading-relaxed text-white/90">
          {job.description}
        </p>

        <div className="mt-5 flex flex-wrap items-center gap-3 border-t border-white/[0.06] pt-4">
          <ApplyAction job={job} size="lg" />
          <span className="meta text-[11px]">
            Posted by{" "}
            <Link to={`/users/${job.author.username}`} className="username-link">
              @{job.author.username}
            </Link>{" "}
            · {timeAgo(job.createdAt)}
          </span>
          {canDelete && (
            <button onClick={() => onDelete(job.id)} className="btn-danger ml-auto py-2 px-4 text-xs">
              🗑 Delete
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export default function Jobs() {
  const { user } = useAuth();
  const [jobs, setJobs] = useState<Job[] | null>(null);
  const [selected, setSelected] = useState<Job | null>(null);
  const [error, setError] = useState("");

  const [q, setQ] = useState("");
  const [tag, setTag] = useState("");
  const [type, setType] = useState("");

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [formError, setFormError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  function load() {
    const params = new URLSearchParams();
    if (q.trim()) params.set("q", q.trim());
    if (tag) params.set("tag", tag);
    if (type) params.set("type", type);
    api<{ jobs: Job[] }>(`/jobs?${params.toString()}`)
      .then((d) => setJobs(d.jobs))
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load jobs"));
  }

  useEffect(() => {
    const t = setTimeout(load, q ? 300 : 0);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, tag, type]);

  useEffect(() => {
    if (!selected) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setSelected(null);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [selected]);

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setFormError("");
    setSubmitting(true);
    try {
      await api("/jobs", { method: "POST", body: JSON.stringify(form) });
      setForm(EMPTY_FORM);
      setShowForm(false);
      load();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Failed to post job");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(id: string) {
    if (!window.confirm("Delete this job posting?")) return;
    try {
      await api(`/jobs/${id}`, { method: "DELETE" });
      setJobs((prev) => (prev ? prev.filter((j) => j.id !== id) : prev));
      setSelected((prev) => (prev && prev.id === id ? null : prev));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete job");
    }
  }

  const set = (k: keyof typeof EMPTY_FORM) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="heading animate-fade-in-up mb-1">
            💼 Supply-Chain <span className="gradient-text">Jobs</span>
          </h1>
          <p className="meta animate-fade-in-up [animation-delay:100ms]">
            Roles across planning, procurement, warehousing and logistics — posted by the community.
          </p>
        </div>
        {user && (
          <button onClick={() => setShowForm((v) => !v)} className="btn-primary flex-none">
            {showForm ? "Close" : "＋ Post a job"}
          </button>
        )}
      </div>

      {showForm && user && (
        <form onSubmit={handleCreate} className="card animate-fade-in-up mb-6 space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <input className="input" placeholder="Job title *" value={form.title} onChange={set("title")} required minLength={4} />
            <input className="input" placeholder="Company *" value={form.company} onChange={set("company")} required />
            <input className="input" placeholder="Location (e.g. Bengaluru / Remote)" value={form.location} onChange={set("location")} />
            <input className="input" placeholder="Salary / range (optional)" value={form.salary} onChange={set("salary")} />
            <select className="input" value={form.employmentType} onChange={set("employmentType")}>
              {TYPES.map((t) => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>
            <select className="input" value={form.tag} onChange={set("tag")}>
              {TAGS.map((t) => (
                <option key={t.value} value={t.value}>{t.emoji} {t.label}</option>
              ))}
            </select>
          </div>
          <input className="input" placeholder="External apply link (optional — else applicants DM you)" value={form.applyUrl} onChange={set("applyUrl")} />
          <textarea className="input min-h-28" placeholder="Role, responsibilities, requirements… *" value={form.description} onChange={set("description")} required />
          {formError && <p className="text-sm text-red-400">{formError}</p>}
          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting ? "Posting…" : "Post job"}
          </button>
        </form>
      )}

      {/* Filters */}
      <div className="mb-5 flex flex-wrap items-center gap-2">
        <input
          className="input w-full py-2 sm:w-56"
          placeholder="🔍 Search title / company…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <select className="input w-full py-2 sm:w-auto" value={tag} onChange={(e) => setTag(e.target.value)}>
          <option value="">All domains</option>
          {TAGS.map((t) => (
            <option key={t.value} value={t.value}>{t.emoji} {t.label}</option>
          ))}
        </select>
        <select className="input w-full py-2 sm:w-auto" value={type} onChange={(e) => setType(e.target.value)}>
          <option value="">All types</option>
          {TYPES.map((t) => (
            <option key={t.value} value={t.value}>{t.label}</option>
          ))}
        </select>
      </div>

      {error && <p className="card text-sm text-red-400">{error}</p>}
      {!error && !jobs && <p className="py-10 text-center text-[#8A8F98]">Loading…</p>}
      {jobs && jobs.length === 0 && (
        <div className="card text-center text-[#8A8F98]">
          No jobs match your filters yet.{user ? " Be the first to post one!" : " Check back soon."}
        </div>
      )}

      {jobs && jobs.length > 0 && (
        <div className="grid gap-4 md:grid-cols-2">
          {jobs.map((job, i) => (
            <JobCard
              key={job.id}
              job={job}
              index={i}
              canDelete={!!user && (user.id === job.authorId || user.role === "ADMIN")}
              onOpen={setSelected}
              onDelete={handleDelete}
            />
          ))}
        </div>
      )}

      {selected && (
        <JobModal
          job={selected}
          canDelete={!!user && (user.id === selected.authorId || user.role === "ADMIN")}
          onClose={() => setSelected(null)}
          onDelete={handleDelete}
        />
      )}
    </div>
  );
}
