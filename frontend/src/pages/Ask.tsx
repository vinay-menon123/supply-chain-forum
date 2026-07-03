import { ChangeEvent, FormEvent, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { apiForm } from "../api";
import type { Question } from "../types";

export default function Ask() {
  const navigate = useNavigate();
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
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
      if (image) form.append("image", image);
      const question = await apiForm<Question>("/questions", form);
      navigate(`/questions/${question.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to post question");
      setSubmitting(false);
    }
  }

  return (
    <div>
      <h1 className="heading mb-6">Ask a Question</h1>
      <form onSubmit={handleSubmit} className="card space-y-4">
        <div>
          <label className="label" htmlFor="title">
            Title
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
        </div>

        <div>
          <label className="label" htmlFor="body">
            Details
          </label>
          <textarea
            id="body"
            className="input min-h-40"
            rows={8}
            placeholder="Describe your question in detail. What have you tried so far?"
            value={body}
            onChange={(e) => setBody(e.target.value)}
            required
          />
        </div>

        <div>
          <label className="label" htmlFor="image">
            Image <span className="font-normal text-slate-400">(optional, max 5 MB)</span>
          </label>
          <input
            id="image"
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            onChange={handleImageChange}
            className="block w-full text-sm text-slate-500 file:mr-3 file:rounded-lg file:border-0 file:bg-indigo-50 file:px-4 file:py-2 file:text-sm file:font-medium file:text-indigo-700 hover:file:bg-indigo-100 dark:text-slate-400 dark:file:bg-indigo-950 dark:file:text-indigo-300"
          />
          {preview && (
            <div className="mt-3 flex items-start gap-3">
              <img
                src={preview}
                alt="Preview"
                className="max-h-48 rounded-lg border border-slate-200 object-contain dark:border-slate-700"
              />
              <button
                type="button"
                onClick={() => setImage(null)}
                className="text-sm text-slate-500 hover:text-red-600 dark:text-slate-400"
              >
                Remove
              </button>
            </div>
          )}
        </div>

        {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

        <button type="submit" className="btn-primary" disabled={submitting}>
          {submitting ? "Posting…" : "Post Question"}
        </button>
      </form>
    </div>
  );
}
