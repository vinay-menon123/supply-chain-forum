import { ChangeEvent, FormEvent, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { apiForm } from "../api";
import { TAGS } from "../tags";
import { useAuth } from "../auth";
import type { Question } from "../types";

export default function Ask() {
  const navigate = useNavigate();
  const { user } = useAuth();
  
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [tag, setTag] = useState("GENERAL");
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
      form.append("title", title);
      form.append("body", body);
      form.append("tag", tag);
      if (image) form.append("image", image);
      const question = await apiForm<Question>("/questions", form);
      navigate(`/questions/${question.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to post question");
      setSubmitting(false);
    }
  }

  const selectedTagObject = TAGS.find((t) => t.value === tag);

  return (
    <div className="relative">
      <div className="mb-8">
        <span className="text-xs font-semibold uppercase tracking-widest font-mono text-accent">
          Broadcaster Dashboard
        </span>
        <h1 className="heading mt-1">Ask a Question</h1>
        <p className="text-xs text-[#8A8F98] mt-1.5 leading-relaxed">
          Ask a practical operation question to verify your reputation.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        
        {/* Left column: Input Form Questionnaire */}
        <form onSubmit={handleSubmit} className="lg:col-span-7 card p-6 space-y-6">
          
          <div>
            <label className="label text-[10px]" htmlFor="title">
              Question Title
            </label>
            <input
              id="title"
              className="input"
              placeholder="Be specific — e.g. How do I forecast demand with seasonal spikes?"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              minLength={8}
              maxLength={200}
            />
            <span className="text-[10px] text-[#8A8F98]/60 block mt-1.5">
              Min 8 characters. Formulate a precise, descriptive supply chain inquiry.
            </span>
          </div>

          <div>
            <label className="label text-[10px]">
              Select Operations Topic
            </label>
            <div className="flex flex-wrap gap-2.5">
              {TAGS.slice(1).map((t) => {
                const isSelected = tag === t.value;
                return (
                  <button
                    key={t.value}
                    type="button"
                    onClick={() => setTag(t.value)}
                    className={`pill text-xs font-medium ${
                      isSelected ? "pill-active" : "pill-inactive"
                    }`}
                  >
                    {t.emoji} {t.label}
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <label className="label text-[10px]" htmlFor="body">
              Question Details
            </label>
            <textarea
              id="body"
              className="input min-h-[160px] resize-y leading-relaxed"
              rows={6}
              placeholder="Describe your inquiry in detail. What logistical workflows are active? What metrics are you targeting?"
              value={body}
              onChange={(e) => setBody(e.target.value)}
              required
            />
          </div>

          <div>
            <label className="label text-[10px]" htmlFor="image">
              Attach Asset Photo <span className="font-normal text-[#8A8F98]/55">(optional, max 5 MB)</span>
            </label>
            <div className="relative border border-dashed border-white/10 bg-[#0F0F12]/50 p-6 text-center rounded-lg transition-all duration-200 hover:border-accent/40 hover:bg-accent/5 group">
              <input
                id="image"
                type="file"
                accept="image/jpeg,image/png,image/gif,image/webp"
                onChange={handleImageChange}
                className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10"
              />
              
              <span className="text-xl select-none block mb-1.5">📸</span>
              <span className="text-xs font-semibold text-white/80 group-hover:text-accent transition-colors block truncate max-w-xs mx-auto">
                {image ? image.name : "Select or drag file"}
              </span>
              <span className="text-[10px] text-[#8A8F98]/55 block mt-1">
                PNG, JPG, GIF or WEBP up to 5MB
              </span>
            </div>
            
            {preview && (
              <div className="mt-3 flex items-center justify-between border border-white/10 bg-[#020203] p-2.5 rounded-lg">
                <span className="text-xs text-[#8A8F98] truncate max-w-[200px] sm:max-w-xs">
                  Attached: {image?.name}
                </span>
                <button
                  type="button"
                  onClick={() => {
                    setImage(null);
                    setPreview("");
                  }}
                  className="text-xs text-accent hover:text-white font-semibold"
                >
                  Clear
                </button>
              </div>
            )}
          </div>

          {error && (
            <p className="text-xs text-red-400 bg-red-950/10 border border-red-500/20 p-3 rounded-lg animate-pulse">
              ⚠️ {error}
            </p>
          )}

          <button
            type="submit"
            className="btn-primary w-full py-3"
            disabled={submitting}
          >
            {submitting ? "Broadcasting..." : "Broadcast Question"}
          </button>
        </form>

        {/* Right column: Live Feed Preview */}
        <div className="lg:col-span-5 space-y-4">
          <div className="text-left">
            <span className="text-xs font-mono uppercase tracking-wider text-[#8A8F98]">
              Live Preview
            </span>
          </div>

          <div className="card relative p-5 flex flex-col justify-between rounded-2xl">
            {/* Author details */}
            <div className="flex items-center justify-between mb-4 border-b border-white/[0.06] pb-3">
              <div className="flex items-center gap-2.5">
                {user?.avatarUrl ? (
                  <img
                    src={user.avatarUrl}
                    alt=""
                    className="h-8 w-8 rounded-full border border-white/10 object-cover"
                  />
                ) : (
                  <span className="grid h-8 w-8 place-items-center rounded-full bg-white/5 border border-white/10 text-xs font-semibold text-white">
                    {user?.username?.charAt(0).toUpperCase() || "M"}
                  </span>
                )}
                <div>
                  <div className="flex items-center gap-1.5">
                    <span className="text-xs font-semibold text-white">
                      {user?.name || user?.username || "Practitioner"}
                    </span>
                    {user?.verifyStatus === "APPROVED" ? (
                      <span className="text-[9px] bg-accent/15 text-accent border border-accent/20 px-1 py-0.2 rounded font-bold">
                        ✓ VERIFIED
                      </span>
                    ) : (
                      <span className="text-[9px] text-[#8A8F98] border border-white/10 px-1 py-0.2 rounded font-semibold font-mono">
                        DRAFT
                      </span>
                    )}
                  </div>
                  <p className="text-[10px] text-[#8A8F98] truncate max-w-[150px] font-mono uppercase tracking-wider mt-0.5">
                    {user?.headline || user?.memberType || "Executive"}
                  </p>
                </div>
              </div>

              <span className="text-[10px] text-accent bg-accent/5 border border-accent/20 px-2 py-0.5 rounded font-mono">
                {selectedTagObject?.emoji} {selectedTagObject?.label || "GENERAL"}
              </span>
            </div>

            {/* Question title */}
            <h3 className="text-sm font-semibold text-white leading-normal tracking-wide">
              {title || "Draft Question Title"}
            </h3>

            {/* Question body */}
            <p className="text-xs text-[#8A8F98] mt-2.5 leading-relaxed line-clamp-4 whitespace-pre-wrap min-h-[80px]">
              {body || "Draft question details will render here..."}
            </p>

            {/* Attached Image Preview */}
            {preview && (
              <div className="mt-4 rounded-lg overflow-hidden border border-white/10 bg-black/40 p-1">
                <img
                  src={preview}
                  alt="Asset Preview"
                  className="w-full max-h-48 object-cover rounded-md"
                />
              </div>
            )}

            {/* Stats footer */}
            <div className="border-t border-white/[0.06] pt-3 mt-4 flex items-center justify-between text-[10px] font-mono text-[#8A8F98]">
              <div className="flex items-center gap-3">
                <span>▲ 0 upvotes</span>
                <span>💬 0 comments</span>
              </div>
              <span className="text-accent hover:underline cursor-pointer">View thread →</span>
            </div>
          </div>

          <div className="p-4 border border-white/[0.06] bg-white/[0.01] rounded-xl text-[10px] text-[#8A8F98] leading-relaxed text-center">
            All operations postings are validated to ensure direct practitioner coordination and maintain high signal-to-noise value across the network.
          </div>
        </div>

      </div>
    </div>
  );
}
