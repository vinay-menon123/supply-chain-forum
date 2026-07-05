import { Link, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { tagMeta } from "../tags";
import { timeAgo } from "../time";
import type { Question } from "../types";
import ShareButton from "./ShareButton";
import VoteButton from "./VoteButton";

interface Props {
  question: Question;
  onDeleted?: (id: string) => void;
}

export default function QuestionCard({ question, onDeleted }: Props) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const canDelete =
    onDeleted && user && (user.id === question.author.id || user.role === "ADMIN");

  async function handleDelete() {
    if (!window.confirm("Delete this question and all its comments?")) return;
    try {
      await api(`/questions/${question.id}`, { method: "DELETE" });
      onDeleted?.(question.id);
    } catch {
      // keep the card if the request failed
    }
  }

  const handleCardClick = (e: React.MouseEvent) => {
    const target = e.target as HTMLElement;
    if (target.closest("button") || target.closest("a") || target.closest("input")) {
      return;
    }
    navigate(`/questions/${question.id}`);
  };

  return (
    <article 
      onClick={handleCardClick}
      className="card card-hover flex gap-4 bg-gradient-to-b from-white/[0.04] to-white/[0.01] border-white/[0.06] p-5 rounded-xl cursor-pointer"
    >
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <Link
            to={`/questions/${question.id}`}
            className="text-base font-semibold text-white transition hover:text-accent leading-snug tracking-wide"
          >
            {question.title}
          </Link>
          {canDelete && (
            <button onClick={handleDelete} className="btn-danger flex-none py-1 px-2.5 text-xs" title="Delete question">
              🗑
            </button>
          )}
        </div>
        <p className="mt-1.5 line-clamp-2 text-xs text-[#8A8F98] leading-relaxed">
          {question.body}
        </p>
        <div className="mt-3.5 flex flex-wrap items-center gap-1.5">
          <span className="pill pill-inactive text-[10px] py-0.5 px-2.5 select-none">
            {tagMeta(question.tag).emoji} {tagMeta(question.tag).label}
          </span>
          {question.acceptedCommentId && (
            <span className="badge border-emerald-500/20 bg-emerald-500/10 text-emerald-400">
              ✓ Answered
            </span>
          )}
        </div>
        <div className="meta mt-3.5 flex flex-wrap items-center gap-x-3 gap-y-1.5 border-t border-white/[0.04] pt-3 text-[11px] text-[#8A8F98]">
          <VoteButton question={question} />
          <span className="text-white/10">|</span>
          <Link to={`/users/${question.author.username}`} className="username-link font-medium">
            @{question.author.username}
          </Link>
          <span className="text-white/10">•</span>
          <span>{timeAgo(question.createdAt)}</span>
          <span className="text-white/10">•</span>
          <span>
            💬 {question._count.comments}
          </span>
          <span className="text-white/10">|</span>
          <ShareButton question={question} />
        </div>
      </div>
      {question.imageUrl && (
        <img
          src={question.imageUrl}
          alt=""
          className="hidden h-16 w-16 flex-none rounded-lg border border-white/10 object-cover sm:block align-self-start"
        />
      )}
    </article>
  );
}
