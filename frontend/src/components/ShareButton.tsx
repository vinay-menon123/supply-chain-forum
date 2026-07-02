import { useState } from "react";
import { api } from "../api";
import type { Question } from "../types";

interface Props {
  question: Pick<Question, "id" | "title" | "shareCount">;
}

export default function ShareButton({ question }: Props) {
  const [count, setCount] = useState(question.shareCount);
  const [copied, setCopied] = useState(false);

  async function handleShare() {
    const url = `${window.location.origin}/questions/${question.id}`;
    try {
      if (navigator.share) {
        await navigator.share({ title: question.title, url });
      } else {
        await navigator.clipboard.writeText(url);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }
    } catch {
      return; // user dismissed the share sheet
    }
    api<{ shareCount: number }>(`/questions/${question.id}/share`, { method: "POST" })
      .then((data) => setCount(data.shareCount))
      .catch(() => {});
  }

  return (
    <button
      onClick={handleShare}
      className="inline-flex items-center gap-1 text-xs font-medium text-slate-500 transition hover:text-indigo-600"
      title="Share this question"
    >
      {copied ? (
        <span className="text-emerald-600">Link copied!</span>
      ) : (
        <>
          <span aria-hidden>↗</span> Share{count > 0 ? ` · ${count}` : ""}
        </>
      )}
    </button>
  );
}
