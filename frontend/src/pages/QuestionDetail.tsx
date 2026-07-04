import { ChangeEvent, FormEvent, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, apiForm } from "../api";
import { useAuth } from "../auth";
import ShareButton from "../components/ShareButton";
import VoteButton from "../components/VoteButton";
import { tagMeta } from "../tags";
import { timeAgo } from "../time";
import type { Comment, Question } from "../types";

export default function QuestionDetail() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [question, setQuestion] = useState<Question | null>(null);
  const [error, setError] = useState("");
  const [commentBody, setCommentBody] = useState("");
  const [commentImage, setCommentImage] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState("");
  const [commentError, setCommentError] = useState("");
  const [posting, setPosting] = useState(false);

  const isAdmin = user?.role === "ADMIN";

  useEffect(() => {
    setQuestion(null);
    setError("");
    api<Question>(`/questions/${id}`)
      .then(setQuestion)
      .catch((err) => setError(err.message));
  }, [id]);

  useEffect(() => {
    if (!commentImage) {
      setImagePreview("");
      return;
    }
    const url = URL.createObjectURL(commentImage);
    setImagePreview(url);
    return () => URL.revokeObjectURL(url);
  }, [commentImage]);

  function handleImageChange(e: ChangeEvent<HTMLInputElement>) {
    setCommentImage(e.target.files?.[0] ?? null);
    e.target.value = "";
  }

  async function submitComment(e: FormEvent) {
    e.preventDefault();
    if (!commentBody.trim()) return;
    setPosting(true);
    setCommentError("");
    try {
      const form = new FormData();
      form.append("body", commentBody);
      if (commentImage) form.append("image", commentImage);
      const created = await apiForm<Comment>(`/questions/${id}/comments`, form);
      setQuestion((prev) =>
        prev ? { ...prev, comments: [...(prev.comments ?? []), created] } : prev
      );
      setCommentBody("");
      setCommentImage(null);
    } catch (err) {
      setCommentError(err instanceof Error ? err.message : "Failed to post comment");
    } finally {
      setPosting(false);
    }
  }

  async function deleteQuestion() {
    if (!question) return;
    if (!window.confirm("Delete this question and all its comments?")) return;
    try {
      await api(`/questions/${question.id}`, { method: "DELETE" });
      navigate("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete");
    }
  }

  async function toggleAccept(commentId: string) {
    try {
      const data = await api<{ acceptedCommentId: string | null }>(
        `/questions/${id}/comments/${commentId}/accept`,
        { method: "POST" }
      );
      setQuestion((prev) => (prev ? { ...prev, acceptedCommentId: data.acceptedCommentId } : prev));
    } catch (err) {
      setCommentError(err instanceof Error ? err.message : "Failed to update accepted answer");
    }
  }

  async function deleteComment(commentId: string) {
    if (!window.confirm("Delete this comment?")) return;
    try {
      await api(`/questions/${id}/comments/${commentId}`, { method: "DELETE" });
      setQuestion((prev) =>
        prev
          ? { ...prev, comments: (prev.comments ?? []).filter((c) => c.id !== commentId) }
          : prev
      );
    } catch (err) {
      setCommentError(err instanceof Error ? err.message : "Failed to delete comment");
    }
  }

  if (error) {
    return (
      <div className="card text-center">
        <p className="text-red-600 dark:text-red-400">{error}</p>
        <Link
          to="/"
          className="mt-3 inline-block text-sm text-indigo-600 hover:underline dark:text-indigo-400"
        >
          ← Back to questions
        </Link>
      </div>
    );
  }
  if (!question) {
    return <p className="py-8 text-center text-slate-500 dark:text-slate-400">Loading…</p>;
  }

  const acceptedId = question.acceptedCommentId;
  const comments = [...(question.comments ?? [])].sort((a, b) =>
    a.id === acceptedId ? -1 : b.id === acceptedId ? 1 : 0
  );
  const canDeleteQuestion = user && (user.id === question.author.id || isAdmin);
  const canAccept = user && (user.id === question.author.id || isAdmin);

  return (
    <div className="space-y-6">
      <article className="card">
        <div className="flex items-start justify-between gap-3">
          <h1 className="heading">{question.title}</h1>
          {canDeleteQuestion && (
            <button onClick={deleteQuestion} className="btn-danger flex-none" title="Delete question">
              🗑 Delete
            </button>
          )}
        </div>
        <div className="mt-2 flex flex-wrap items-center gap-1.5">
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">
            {tagMeta(question.tag).emoji} {tagMeta(question.tag).label}
          </span>
          {acceptedId && (
            <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">
              ✓ Answered
            </span>
          )}
        </div>
        <div className="meta mt-2 flex flex-wrap items-center gap-x-4 gap-y-1.5">
          <VoteButton question={question} />
          <Link to={`/users/${question.author.username}`} className="username-link">
            @{question.author.username}
          </Link>
          <span>{timeAgo(question.createdAt)}</span>
          <ShareButton question={question} />
        </div>
        <p className="mt-4 whitespace-pre-wrap text-slate-700 dark:text-slate-300">
          {question.body}
        </p>
        {question.imageUrl && (
          <a href={question.imageUrl} target="_blank" rel="noreferrer">
            <img
              src={question.imageUrl}
              alt="Question attachment"
              className="mt-4 max-h-96 rounded-lg border border-slate-200 object-contain dark:border-slate-700"
            />
          </a>
        )}
      </article>

      <section>
        <h2 className="mb-3 text-lg font-semibold text-slate-900 dark:text-slate-100">
          {comments.length} {comments.length === 1 ? "Comment" : "Comments"}
        </h2>

        <div className="space-y-3">
          {comments.map((comment) => (
            <div
              key={comment.id}
              className={`card py-4 ${
                comment.id === acceptedId
                  ? "border-emerald-400 ring-1 ring-emerald-300 dark:border-emerald-700 dark:ring-emerald-800"
                  : ""
              }`}
            >
              {comment.id === acceptedId && (
                <p className="mb-2 text-xs font-bold text-emerald-600 dark:text-emerald-400">
                  ✓ Accepted Answer
                </p>
              )}
              <div className="meta mb-1 flex items-center gap-3">
                <Link to={`/users/${comment.author.username}`} className="username-link">
                  @{comment.author.username}
                </Link>
                <span>{timeAgo(comment.createdAt)}</span>
                <span className="ml-auto flex items-center gap-2">
                  {canAccept && (
                    <button
                      onClick={() => toggleAccept(comment.id)}
                      className={`rounded-full px-2 py-0.5 text-xs font-medium transition ${
                        comment.id === acceptedId
                          ? "bg-emerald-100 text-emerald-700 hover:bg-emerald-200 dark:bg-emerald-950 dark:text-emerald-300"
                          : "text-slate-400 hover:bg-emerald-50 hover:text-emerald-600 dark:hover:bg-emerald-950"
                      }`}
                      title={comment.id === acceptedId ? "Unmark accepted answer" : "Mark as accepted answer"}
                    >
                      {comment.id === acceptedId ? "✓ Accepted" : "✓ Accept"}
                    </button>
                  )}
                  {user && (user.id === comment.author.id || isAdmin) && (
                    <button
                      onClick={() => deleteComment(comment.id)}
                      className="btn-danger"
                      title="Delete comment"
                    >
                      🗑
                    </button>
                  )}
                </span>
              </div>
              <p className="whitespace-pre-wrap text-sm text-slate-700 dark:text-slate-300">
                {comment.body}
              </p>
              {comment.imageUrl && (
                <a href={comment.imageUrl} target="_blank" rel="noreferrer">
                  <img
                    src={comment.imageUrl}
                    alt="Comment attachment"
                    className="mt-2 max-h-64 rounded-lg border border-slate-200 object-contain dark:border-slate-700"
                  />
                </a>
              )}
            </div>
          ))}
          {comments.length === 0 && (
            <p className="text-sm text-slate-500 dark:text-slate-400">
              No comments yet — start the discussion! 💡
            </p>
          )}
        </div>

        {user ? (
          <form onSubmit={submitComment} className="card mt-4">
            <label className="label" htmlFor="comment">
              Add a comment
            </label>
            <textarea
              id="comment"
              className="input min-h-24"
              rows={3}
              placeholder="Share your answer or thoughts…"
              value={commentBody}
              onChange={(e) => setCommentBody(e.target.value)}
            />
            <div className="mt-2 flex items-center gap-3">
              <label className="btn-secondary cursor-pointer text-xs">
                📎 Attach image
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/gif,image/webp"
                  onChange={handleImageChange}
                  className="hidden"
                />
              </label>
              {commentImage && (
                <button
                  type="button"
                  onClick={() => setCommentImage(null)}
                  className="text-xs text-slate-500 hover:text-red-600 dark:text-slate-400"
                >
                  Remove image
                </button>
              )}
            </div>
            {imagePreview && (
              <img
                src={imagePreview}
                alt="Preview"
                className="mt-3 max-h-40 rounded-lg border border-slate-200 object-contain dark:border-slate-700"
              />
            )}
            {commentError && (
              <p className="mt-2 text-sm text-red-600 dark:text-red-400">{commentError}</p>
            )}
            <button
              type="submit"
              className="btn-primary mt-3"
              disabled={posting || !commentBody.trim()}
            >
              {posting ? "Posting…" : "Post Comment"}
            </button>
          </form>
        ) : (
          <p className="mt-4 text-sm text-slate-600 dark:text-slate-400">
            <Link
              to="/login"
              state={{ from: `/questions/${question.id}` }}
              className="font-medium text-indigo-600 hover:underline dark:text-indigo-400"
            >
              Sign in with Google
            </Link>{" "}
            to join the discussion.
          </p>
        )}
      </section>
    </div>
  );
}
