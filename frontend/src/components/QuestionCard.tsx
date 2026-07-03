import { Link } from "react-router-dom";
import { timeAgo } from "../time";
import type { Question } from "../types";
import ShareButton from "./ShareButton";
import VoteButton from "./VoteButton";

export default function QuestionCard({ question }: { question: Question }) {
  return (
    <article className="card card-hover flex gap-4">
      <div className="min-w-0 flex-1">
        <Link
          to={`/questions/${question.id}`}
          className="text-lg font-semibold text-slate-900 transition hover:text-indigo-600 dark:text-slate-100 dark:hover:text-indigo-400"
        >
          {question.title}
        </Link>
        <p className="mt-1 line-clamp-2 text-sm text-slate-600 dark:text-slate-400">
          {question.body}
        </p>
        <div className="meta mt-3 flex flex-wrap items-center gap-x-4 gap-y-1.5">
          <VoteButton question={question} />
          <Link to={`/users/${question.author.username}`} className="username-link">
            @{question.author.username}
          </Link>
          <span>{timeAgo(question.createdAt)}</span>
          <span>
            💬 {question._count.comments}
          </span>
          <ShareButton question={question} />
        </div>
      </div>
      {question.imageUrl && (
        <img
          src={question.imageUrl}
          alt=""
          className="hidden h-20 w-20 flex-none rounded-lg border border-slate-200 object-cover dark:border-slate-700 sm:block"
        />
      )}
    </article>
  );
}
