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
      className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium transition ${
        voted
          ? "border-indigo-600 bg-indigo-600 text-white hover:bg-indigo-700"
          : "border-slate-300 bg-white text-slate-600 hover:border-indigo-400 hover:text-indigo-600"
      }`}
    >
      <span aria-hidden>▲</span> {count}
    </button>
  );
}
