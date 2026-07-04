import { ChangeEvent, FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, apiForm } from "../api";
import { useAuth } from "../auth";
import { CATEGORIES, categoryMeta, KINDS, kindMeta } from "../marketplace";
import { timeAgo } from "../time";
import type { Listing } from "../types";

export default function Marketplace() {
  const { user } = useAuth();
  const [listings, setListings] = useState<Listing[] | null>(null);
  const [error, setError] = useState("");
  const [kindFilter, setKindFilter] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [showForm, setShowForm] = useState(false);

  function load() {
    setListings(null);
    const params = new URLSearchParams();
    if (kindFilter) params.set("kind", kindFilter);
    if (categoryFilter) params.set("category", categoryFilter);
    const qs = params.toString();
    api<{ listings: Listing[] }>(`/listings${qs ? `?${qs}` : ""}`)
      .then((d) => setListings(d.listings))
      .catch((err) => setError(err.message));
  }

  useEffect(load, [kindFilter, categoryFilter]);

  async function handleDelete(id: string) {
    if (!window.confirm("Remove this listing?")) return;
    try {
      await api(`/listings/${id}`, { method: "DELETE" });
      setListings((prev) => prev?.filter((l) => l.id !== id) ?? prev);
    } catch {
      /* keep it if the request failed */
    }
  }

  return (
    <div>
      <div className="animate-fade-in-up mb-2 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="heading">
            Logistics <span className="gradient-text">Marketplace</span>
          </h1>
          <p className="meta mt-1">
            Warehouse space, freight capacity, equipment & services — offered and sought by the
            community.
          </p>
        </div>
        {user ? (
          <button onClick={() => setShowForm((v) => !v)} className="btn-primary flex-none">
            {showForm ? "Close" : "➕ Post a listing"}
          </button>
        ) : (
          <Link to="/login" className="btn-primary flex-none">
            Sign in to post
          </Link>
        )}
      </div>

      {showForm && user && (
        <ListingForm
          onPosted={(listing) => {
            setListings((prev) => (prev ? [listing, ...prev] : [listing]));
            setShowForm(false);
          }}
        />
      )}

      {/* Filters */}
      <div className="mb-3 mt-6 flex flex-wrap items-center gap-1.5">
        <FilterPill active={kindFilter === ""} onClick={() => setKindFilter("")}>
          Everything
        </FilterPill>
        {KINDS.map((k) => (
          <FilterPill
            key={k.value}
            active={kindFilter === k.value}
            onClick={() => setKindFilter(k.value)}
          >
            {k.value === "OFFER" ? "🟢 Offering" : "🟡 Wanted"}
          </FilterPill>
        ))}
      </div>
      <div className="mb-6 flex gap-1.5 overflow-x-auto pb-1">
        <FilterPill active={categoryFilter === ""} onClick={() => setCategoryFilter("")} nowrap>
          All categories
        </FilterPill>
        {CATEGORIES.map((c) => (
          <FilterPill
            key={c.value}
            active={categoryFilter === c.value}
            onClick={() => setCategoryFilter(c.value)}
            nowrap
          >
            {c.emoji} {c.label}
          </FilterPill>
        ))}
      </div>

      {error && <p className="card text-sm text-red-600 dark:text-red-400">{error}</p>}
      {!error && !listings && (
        <p className="py-8 text-center text-slate-500 dark:text-slate-400">Loading…</p>
      )}
      {listings && listings.length === 0 && (
        <div className="card text-center text-slate-500 dark:text-slate-400">
          No listings here yet — {user ? "post the first one! 📦" : "sign in to post the first one 📦"}
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        {listings?.map((listing, index) => (
          <div
            key={listing.id}
            className="animate-fade-in-up"
            style={{ animationDelay: `${Math.min(index, 8) * 60}ms` }}
          >
            <ListingCard
              listing={listing}
              canDelete={!!user && (user.id === listing.authorId || user.role === "ADMIN")}
              isOwner={user?.id === listing.authorId}
              onDelete={() => handleDelete(listing.id)}
            />
          </div>
        ))}
      </div>
    </div>
  );
}

function FilterPill({
  active,
  onClick,
  children,
  nowrap = false,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
  nowrap?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      className={`pill text-xs ${nowrap ? "flex-none" : ""} ${
        active ? "pill-active" : "pill-inactive bg-slate-100 dark:bg-slate-900"
      }`}
    >
      {children}
    </button>
  );
}

function ListingCard({
  listing,
  canDelete,
  isOwner,
  onDelete,
}: {
  listing: Listing;
  canDelete: boolean;
  isOwner: boolean;
  onDelete: () => void;
}) {
  const kind = kindMeta(listing.kind);
  const cat = categoryMeta(listing.category);
  return (
    <article className="card card-lift flex h-full flex-col">
      {listing.imageUrl && (
        <img
          src={listing.imageUrl}
          alt=""
          className="mb-3 h-40 w-full rounded-xl border border-slate-200 object-cover dark:border-slate-700"
        />
      )}
      <div className="mb-2 flex flex-wrap items-center gap-1.5">
        <span className={`badge ${kind.badgeClass}`}>
          {listing.kind === "OFFER" ? "🟢 Offering" : "🟡 Wanted"}
        </span>
        <span className="badge bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300">
          {cat.emoji} {cat.label}
        </span>
      </div>
      <h3 className="font-semibold text-slate-900 dark:text-slate-100">{listing.title}</h3>
      <p className="mt-1 line-clamp-3 flex-1 text-sm text-slate-600 dark:text-slate-400">
        {listing.description}
      </p>
      <div className="mt-3 flex flex-wrap gap-1.5">
        {listing.location && <SpecChip>📍 {listing.location}</SpecChip>}
        {listing.size && <SpecChip>📐 {listing.size}</SpecChip>}
        {listing.price && <SpecChip>💰 {listing.price}</SpecChip>}
      </div>
      <div className="meta mt-3 flex items-center justify-between gap-2 border-t border-slate-100 pt-3 dark:border-slate-800">
        <Link to={`/users/${listing.author.username}`} className="username-link truncate">
          {listing.author.name ?? `@${listing.author.username}`}
        </Link>
        <span>{timeAgo(listing.createdAt)}</span>
      </div>
      <div className="mt-3 flex items-center gap-2">
        {isOwner ? (
          <span className="btn-secondary flex-1 cursor-default opacity-70">Your listing</span>
        ) : (
          <Link to={`/messages/${listing.author.username}`} className="btn-primary flex-1">
            💬 Contact {listing.author.name?.split(" ")[0] ?? "member"}
          </Link>
        )}
        {canDelete && (
          <button onClick={onDelete} className="btn-danger flex-none" title="Remove listing">
            🗑
          </button>
        )}
      </div>
    </article>
  );
}

function SpecChip({ children }: { children: React.ReactNode }) {
  return (
    <span className="rounded-lg bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">
      {children}
    </span>
  );
}

function ListingForm({ onPosted }: { onPosted: (listing: Listing) => void }) {
  const [kind, setKind] = useState<"OFFER" | "SEEK">("OFFER");
  const [category, setCategory] = useState("WAREHOUSE");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [location, setLocation] = useState("");
  const [price, setPrice] = useState("");
  const [size, setSize] = useState("");
  const [image, setImage] = useState<File | null>(null);
  const [preview, setPreview] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!image) {
      setPreview("");
      return;
    }
    const url = URL.createObjectURL(image);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [image]);

  function handleImageChange(e: ChangeEvent<HTMLInputElement>) {
    setImage(e.target.files?.[0] ?? null);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      const form = new FormData();
      form.append("kind", kind);
      form.append("category", category);
      form.append("title", title);
      form.append("description", description);
      form.append("location", location);
      form.append("price", price);
      form.append("size", size);
      if (image) form.append("image", image);
      const listing = await apiForm<Listing>("/listings", form);
      onPosted(listing);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to post listing");
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="card animate-fade-in-up mt-4 space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <span className="label">I want to…</span>
          <div className="flex gap-2">
            {KINDS.map((k) => (
              <button
                type="button"
                key={k.value}
                onClick={() => setKind(k.value)}
                className={`flex-1 rounded-xl border p-2.5 text-sm font-semibold transition ${
                  kind === k.value
                    ? "border-indigo-500 bg-indigo-50 text-indigo-700 ring-2 ring-indigo-200 dark:bg-indigo-950 dark:text-indigo-300 dark:ring-indigo-800"
                    : "border-slate-200 text-slate-600 hover:border-indigo-300 dark:border-slate-700 dark:text-slate-300"
                }`}
              >
                {k.value === "OFFER" ? "🟢 Offer" : "🟡 Look for"}
              </button>
            ))}
          </div>
        </div>
        <div>
          <label className="label" htmlFor="category">
            Category
          </label>
          <select
            id="category"
            className="input"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            {CATEGORIES.map((c) => (
              <option key={c.value} value={c.value}>
                {c.emoji} {c.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div>
        <label className="label" htmlFor="ltitle">
          Title
        </label>
        <input
          id="ltitle"
          className="input"
          placeholder="e.g. 12,000 sq ft bonded warehouse near JNPT"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
          minLength={6}
          maxLength={160}
        />
      </div>

      <div>
        <label className="label" htmlFor="ldesc">
          Details
        </label>
        <textarea
          id="ldesc"
          className="input min-h-[100px]"
          placeholder="Describe what you're offering or looking for — specs, terms, timeline."
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          required
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <div>
          <label className="label" htmlFor="lloc">
            Location
          </label>
          <input
            id="lloc"
            className="input"
            placeholder="City / region"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
          />
        </div>
        <div>
          <label className="label" htmlFor="lsize">
            Size / capacity
          </label>
          <input
            id="lsize"
            className="input"
            placeholder="e.g. 12,000 sq ft"
            value={size}
            onChange={(e) => setSize(e.target.value)}
          />
        </div>
        <div>
          <label className="label" htmlFor="lprice">
            Price / rate
          </label>
          <input
            id="lprice"
            className="input"
            placeholder="e.g. ₹28/sq ft/mo"
            value={price}
            onChange={(e) => setPrice(e.target.value)}
          />
        </div>
      </div>

      <div>
        <label className="label" htmlFor="limage">
          Photo <span className="font-normal text-slate-400">(optional, max 5 MB)</span>
        </label>
        <input
          id="limage"
          type="file"
          accept="image/jpeg,image/png,image/gif,image/webp"
          onChange={handleImageChange}
          className="block w-full text-sm text-slate-500 file:mr-3 file:rounded-lg file:border-0 file:bg-indigo-50 file:px-4 file:py-2 file:text-sm file:font-medium file:text-indigo-700 hover:file:bg-indigo-100 dark:text-slate-400 dark:file:bg-indigo-950 dark:file:text-indigo-300"
        />
        {preview && (
          <img
            src={preview}
            alt="Preview"
            className="mt-3 max-h-44 rounded-xl border border-slate-200 object-contain dark:border-slate-700"
          />
        )}
      </div>

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      <button type="submit" className="btn-primary" disabled={submitting}>
        {submitting ? "Posting…" : "Publish listing"}
      </button>
    </form>
  );
}
