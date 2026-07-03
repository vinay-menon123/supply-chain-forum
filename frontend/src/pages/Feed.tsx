import { FormEvent, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import QuestionCard from "../components/QuestionCard";
import type { QuestionList } from "../types";

export default function Feed() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const q = params.get("q") ?? "";
  const sort = params.get("sort") === "top" ? "top" : "new";
  const [search, setSearch] = useState(q);
  const [data, setData] = useState<QuestionList | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    setData(null);
    setError("");
    const query = new URLSearchParams();
    if (q) query.set("q", q);
    if (sort === "top") query.set("sort", "top");
    const qs = query.toString();
    api<QuestionList>(`/questions${qs ? `?${qs}` : ""}`)
      .then(setData)
      .catch((err) => setError(err.message));
  }, [q, sort]);

  function updateParams(nextQ: string, nextSort: string) {
    const next: Record<string, string> = {};
    if (nextQ) next.q = nextQ;
    if (nextSort === "top") next.sort = "top";
    setParams(next);
  }

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    updateParams(search.trim(), sort);
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between gap-4">
        <h1 className="heading">{q ? `Results for "${q}"` : "Questions"}</h1>
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

      <div className="mb-5 flex items-center gap-1 rounded-full bg-slate-100 p-1 dark:bg-slate-900 w-fit">
        <button
          onClick={() => updateParams(q, "new")}
          className={`pill ${sort === "new" ? "pill-active" : "pill-inactive"}`}
        >
          🕘 Newest
        </button>
        <button
          onClick={() => updateParams(q, "top")}
          className={`pill ${sort === "top" ? "pill-active" : "pill-inactive"}`}
        >
          🔥 Top
        </button>
      </div>

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
        {data?.questions.map((question) => (
          <QuestionCard
            key={question.id}
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
        ))}
      </div>
    </div>
  );
}
