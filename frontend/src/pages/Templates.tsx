import { FormEvent, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, apiForm } from "../api";
import { useAuth } from "../auth";
import Calculators from "../components/Calculators";
import { timeAgo } from "../time";
import type { Template } from "../types";

const CATEGORIES: { value: string; label: string }[] = [
  { value: "GENERAL", label: "General" },
  { value: "PLANNING", label: "Planning & S&OP" },
  { value: "PROCUREMENT", label: "Procurement & RFQ" },
  { value: "WAREHOUSING", label: "Warehousing" },
  { value: "LOGISTICS", label: "Logistics & Transport" },
  { value: "INVENTORY", label: "Inventory" },
  { value: "ANALYTICS", label: "Analytics & Models" },
];

function catLabel(v: string): string {
  return CATEGORIES.find((c) => c.value === v)?.label ?? v;
}

function VoteButton({ tpl, onVote }: { tpl: Template; onVote: (t: Template) => void }) {
  return (
    <button
      onClick={() => onVote(tpl)}
      className={`inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-mono transition ${
        tpl.viewerHasVoted
          ? "border border-accent/40 bg-accent/20 text-accent"
          : "border border-white/10 bg-white/5 text-[#8A8F98] hover:text-white"
      }`}
      title={tpl.viewerHasVoted ? "Remove upvote" : "Upvote this template"}
    >
      ▲ {tpl.voteCount}
    </button>
  );
}

function TemplateCard({
  tpl,
  canDelete,
  onOpen,
  onVote,
  onDownload,
  onDelete,
  index,
}: {
  tpl: Template;
  canDelete: boolean;
  onOpen: (t: Template) => void;
  onVote: (t: Template) => void;
  onDownload: (t: Template) => void;
  onDelete: (id: string) => void;
  index: number;
}) {
  const handleCardClick = (e: React.MouseEvent) => {
    const target = e.target as HTMLElement;
    if (target.closest("button") || target.closest("a")) return;
    onOpen(tpl);
  };
  return (
    <div
      onClick={handleCardClick}
      className="card card-lift animate-fade-in-up flex cursor-pointer flex-col"
      style={{ animationDelay: `${Math.min(index, 8) * 60}ms` }}
    >
      <div className="flex items-start justify-between gap-3">
        <span className="grid h-10 w-10 flex-none place-items-center rounded-lg border border-white/10 bg-white/[0.03] text-[10px] font-bold text-accent">
          {tpl.fileType ?? "FILE"}
        </span>
        {canDelete && (
          <button onClick={() => onDelete(tpl.id)} className="btn-danger py-1 px-2.5 text-xs" title="Delete template">
            🗑
          </button>
        )}
      </div>
      <h3 className="mt-3 text-sm font-bold leading-snug text-white break-words hover:text-accent">{tpl.title}</h3>
      {tpl.description && (
        <p className="mt-1 line-clamp-2 whitespace-pre-wrap break-words text-xs text-[#8A8F98] leading-relaxed">
          {tpl.description}
        </p>
      )}
      <div className="mt-3 flex flex-wrap items-center gap-1.5">
        <span className="pill pill-inactive text-[10px] py-0.5 px-2.5 select-none">{catLabel(tpl.category)}</span>
        <span className="badge border-white/10 bg-white/[0.03] text-[#8A8F98]">⬇ {tpl.downloadCount}</span>
      </div>
      <div className="mt-auto flex flex-wrap items-center justify-between gap-2 border-t border-white/[0.04] pt-3">
        <div className="flex items-center gap-2">
          <VoteButton tpl={tpl} onVote={onVote} />
          <button onClick={() => onDownload(tpl)} className="btn-primary py-1.5 px-4 text-xs">
            ⬇ Download
          </button>
        </div>
        <span className="meta text-[11px]">
          <Link to={`/users/${tpl.author.username}`} className="username-link">
            @{tpl.author.username}
          </Link>{" "}
          · {timeAgo(tpl.createdAt)}
        </span>
      </div>
    </div>
  );
}

function TemplateModal({
  tpl,
  canDelete,
  onClose,
  onVote,
  onDownload,
  onDelete,
}: {
  tpl: Template;
  canDelete: boolean;
  onClose: () => void;
  onVote: (t: Template) => void;
  onDownload: (t: Template) => void;
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

        <div className="flex items-start gap-3 pr-8">
          <span className="grid h-12 w-12 flex-none place-items-center rounded-lg border border-white/10 bg-white/[0.03] text-xs font-bold text-accent">
            {tpl.fileType ?? "FILE"}
          </span>
          <div className="min-w-0">
            <h2 className="text-lg font-bold leading-snug text-white break-words">{tpl.title}</h2>
            <p className="meta mt-0.5 text-[11px]">
              Shared by{" "}
              <Link to={`/users/${tpl.author.username}`} className="username-link">
                @{tpl.author.username}
              </Link>{" "}
              · {timeAgo(tpl.createdAt)}
            </p>
          </div>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-1.5">
          <span className="pill pill-inactive text-[10px] py-0.5 px-2.5 select-none">{catLabel(tpl.category)}</span>
          <span className="badge border-white/10 bg-white/[0.03] text-[#8A8F98]">⬇ {tpl.downloadCount} downloads</span>
          <span className="badge border-white/10 bg-white/[0.03] text-[#8A8F98]">▲ {tpl.voteCount} upvotes</span>
        </div>

        {tpl.description && (
          <p className="mt-4 whitespace-pre-wrap break-words text-sm leading-relaxed text-white/90">
            {tpl.description}
          </p>
        )}

        <div className="mt-4 flex items-center gap-2 rounded-lg border border-white/[0.06] bg-white/[0.02] px-3 py-2 text-xs text-[#8A8F98]">
          <span>📎</span>
          <span className="truncate">{tpl.fileName}</span>
        </div>

        <div className="mt-5 flex flex-wrap items-center gap-2 border-t border-white/[0.06] pt-4">
          <button onClick={() => onDownload(tpl)} className="btn-primary py-2 px-5 text-sm">
            ⬇ Download
          </button>
          <VoteButton tpl={tpl} onVote={onVote} />
          {canDelete && (
            <button onClick={() => onDelete(tpl.id)} className="btn-danger ml-auto py-2 px-4 text-xs">
              🗑 Delete
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export default function Templates() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [templates, setTemplates] = useState<Template[] | null>(null);
  const [selected, setSelected] = useState<Template | null>(null);
  const [view, setView] = useState<"files" | "tools">("files");
  const [error, setError] = useState("");

  const [q, setQ] = useState("");
  const [category, setCategory] = useState("");

  const [showForm, setShowForm] = useState(false);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [uploadCat, setUploadCat] = useState("GENERAL");
  const [file, setFile] = useState<File | null>(null);
  const [formError, setFormError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  function load() {
    const params = new URLSearchParams();
    if (q.trim()) params.set("q", q.trim());
    if (category) params.set("category", category);
    api<{ templates: Template[] }>(`/templates?${params.toString()}`)
      .then((d) => setTemplates(d.templates))
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load templates"));
  }

  useEffect(() => {
    const t = setTimeout(load, q ? 300 : 0);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, category]);

  // Close the modal with Escape.
  useEffect(() => {
    if (!selected) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setSelected(null);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [selected]);

  function patch(id: string, changes: Partial<Template>) {
    setTemplates((prev) => (prev ? prev.map((t) => (t.id === id ? { ...t, ...changes } : t)) : prev));
    setSelected((prev) => (prev && prev.id === id ? { ...prev, ...changes } : prev));
  }

  async function handleVote(tpl: Template) {
    if (!user) {
      navigate("/login");
      return;
    }
    try {
      const res = await api<{ voteCount: number; viewerHasVoted: boolean }>(`/templates/${tpl.id}/vote`, {
        method: "POST",
      });
      patch(tpl.id, { voteCount: res.voteCount, viewerHasVoted: res.viewerHasVoted });
    } catch {
      // leave state as-is on failure
    }
  }

  async function handleDownload(tpl: Template) {
    patch(tpl.id, { downloadCount: tpl.downloadCount + 1 });
    try {
      const res = await api<{ fileUrl: string; fileName: string }>(`/templates/${tpl.id}/download`, { method: "POST" });
      const a = document.createElement("a");
      a.href = res.fileUrl;
      a.download = res.fileName || "";
      a.target = "_blank";
      a.rel = "noreferrer";
      document.body.appendChild(a);
      a.click();
      a.remove();
    } catch {
      window.open(tpl.fileUrl, "_blank", "noreferrer");
    }
  }

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setFormError("");
    if (!file) {
      setFormError("Choose a file to upload.");
      return;
    }
    setSubmitting(true);
    try {
      const fd = new FormData();
      fd.append("title", title);
      fd.append("description", description);
      fd.append("category", uploadCat);
      fd.append("file", file);
      await apiForm("/templates", fd);
      setTitle("");
      setDescription("");
      setUploadCat("GENERAL");
      setFile(null);
      setShowForm(false);
      load();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Failed to upload");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(id: string) {
    if (!window.confirm("Delete this template?")) return;
    try {
      await api(`/templates/${id}`, { method: "DELETE" });
      setTemplates((prev) => (prev ? prev.filter((t) => t.id !== id) : prev));
      setSelected((prev) => (prev && prev.id === id ? null : prev));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete template");
    }
  }

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="heading animate-fade-in-up mb-1">
            📚 Templates & <span className="gradient-text">Resources</span>
          </h1>
          <p className="meta animate-fade-in-up [animation-delay:100ms]">
            Practitioner-built S&amp;OP decks, RFQ sheets, planning models and cheat-sheets. Download or share your own.
          </p>
        </div>
        {user && view === "files" && (
          <button onClick={() => setShowForm((v) => !v)} className="btn-primary flex-none">
            {showForm ? "Close" : "＋ Share a template"}
          </button>
        )}
      </div>

      {/* Downloads / Calculators tabs */}
      <div className="mb-6 flex w-fit gap-1 rounded-xl border border-white/[0.06] bg-white/[0.02] p-1">
        {([["files", "📥 Downloads"], ["tools", "🧮 Calculators"]] as const).map(([v, label]) => (
          <button
            key={v}
            onClick={() => setView(v)}
            className={`rounded-lg px-4 py-1.5 text-xs font-semibold transition ${
              view === v
                ? "bg-accent text-white shadow-[0_0_12px_rgba(94,106,210,0.4)]"
                : "text-[#8A8F98] hover:text-white"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {view === "tools" && <Calculators />}

      {view === "files" && (
        <>
      {showForm && user && (
        <form onSubmit={handleCreate} className="card animate-fade-in-up mb-6 space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <input className="input" placeholder="Title *" value={title} onChange={(e) => setTitle(e.target.value)} required minLength={4} />
            <select className="input" value={uploadCat} onChange={(e) => setUploadCat(e.target.value)}>
              {CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>{c.label}</option>
              ))}
            </select>
          </div>
          <textarea className="input min-h-20" placeholder="What is it and how should people use it? (optional)" value={description} onChange={(e) => setDescription(e.target.value)} />
          <div>
            <input
              type="file"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="block w-full text-sm text-[#8A8F98] file:mr-3 file:rounded-lg file:border-0 file:bg-accent file:px-4 file:py-2 file:text-xs file:font-semibold file:text-white hover:file:bg-accent/90"
              required
            />
            <p className="meta mt-1 text-[11px]">PDF, Excel, Word, PowerPoint, CSV, ZIP or image · up to 15&nbsp;MB.</p>
          </div>
          {formError && <p className="text-sm text-red-400">{formError}</p>}
          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting ? "Uploading…" : "Upload template"}
          </button>
        </form>
      )}

      {/* Filters */}
      <div className="mb-5 flex flex-wrap items-center gap-2">
        <input
          className="input w-full py-2 sm:w-56"
          placeholder="🔍 Search templates…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <select className="input w-full py-2 sm:w-auto" value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="">All categories</option>
          {CATEGORIES.map((c) => (
            <option key={c.value} value={c.value}>{c.label}</option>
          ))}
        </select>
      </div>

      {error && <p className="card text-sm text-red-400">{error}</p>}
      {!error && !templates && <p className="py-10 text-center text-[#8A8F98]">Loading…</p>}
      {templates && templates.length === 0 && (
        <div className="card text-center text-[#8A8F98]">
          No templates yet.{user ? " Share the first one!" : " Check back soon."}
        </div>
      )}

      {templates && templates.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {templates.map((tpl, i) => (
            <TemplateCard
              key={tpl.id}
              tpl={tpl}
              index={i}
              canDelete={!!user && (user.id === tpl.authorId || user.role === "ADMIN")}
              onOpen={setSelected}
              onVote={handleVote}
              onDownload={handleDownload}
              onDelete={handleDelete}
            />
          ))}
        </div>
      )}
        </>
      )}

      {selected && (
        <TemplateModal
          tpl={selected}
          canDelete={!!user && (user.id === selected.authorId || user.role === "ADMIN")}
          onClose={() => setSelected(null)}
          onVote={handleVote}
          onDownload={handleDownload}
          onDelete={handleDelete}
        />
      )}
    </div>
  );
}
