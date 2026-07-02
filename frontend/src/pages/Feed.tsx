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
  const [search, setSearch] = useState(q);
  const [data, setData] = useState<QuestionList | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    setData(null);
    setError("");
    api<QuestionList>(`/questions${q ? `?q=${encodeURIComponent(q)}` : ""}`)
      .then(setData)
      .catch((err) => setError(err.message));
  }, [q]);

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    setParams(search.trim() ? { q: search.trim() } : {});
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-slate-900">
          {q ? `Results for "${q}"` : "Latest Questions"}
        </h1>
        <Link to={user ? "/ask" : "/login"} className="btn-primary flex-none">
          Ask Question
        </Link>
      </div>

      <form onSubmit={handleSearch} className="mb-6 flex gap-2">
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

      {error && <p className="card text-sm text-red-600">{error}</p>}
      {!error && !data && <p className="py-8 text-center text-slate-500">Loading…</p>}

      {data && data.questions.length === 0 && (
        <div className="card text-center text-slate-500">
          {q ? "No questions match your search." : "No questions yet — be the first to ask one!"}
        </div>
      )}

      <div className="space-y-4">
        {data?.questions.map((question) => (
          <QuestionCard key={question.id} question={question} />
        ))}
      </div>
    </div>
  );
}
