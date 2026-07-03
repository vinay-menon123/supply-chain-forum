import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import type { Question } from "../types";

interface Props {
  question: Pick<Question, "id" | "voteCount" | "viewerHasVoted">;
}

export default function VoteButton({ question }: Props) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [count, setCount] = useState(question.voteCount);
  const [voted, setVoted] = useState(question.viewerHasVoted);
  const [busy, setBusy] = useState(false);
  const [pop, setPop] = useState(false);

  async function toggle() {
    if (!user) {
      navigate("/login", { state: { from: location.pathname } });
      return;
    }
    if (busy) return;
    setBusy(true);
    try {
      const data = await api<{ voteCount: number; viewerHasVoted: boolean }>(
        `/questions/${question.id}/vote`,
        { method: "POST" }
      );
      setCount(data.voteCount);
      setVoted(data.viewerHasVoted);
      if (data.viewerHasVoted) {
        setPop(true);
        setTimeout(() => setPop(false), 300);
      }
    } catch {
      // leave the previous state on failure
    } finally {
      setBusy(false);
    }
  }

  return (
    <button
      onClick={toggle}
      disabled={busy}
      title={voted ? "Remove upvote" : "Upvote"}
      className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-all active:scale-90 ${
        pop ? "scale-125" : ""
      } ${
        voted
          ? "border-transparent bg-gradient-to-r from-indigo-600 to-violet-600 text-white shadow-sm"
          : "border-slate-300 bg-white text-slate-600 hover:border-indigo-400 hover:text-indigo-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:border-indigo-500 dark:hover:text-indigo-400"
      }`}
    >
      <span aria-hidden>▲</span> {count}
    </button>
  );
}
