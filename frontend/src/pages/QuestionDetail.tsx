import { ChangeEvent, FormEvent, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, apiForm } from "../api";
import { useAuth } from "../auth";
import ShareButton from "../components/ShareButton";
import VoteButton from "../components/VoteButton";
import { timeAgo } from "../time";
import type { Comment, Question } from "../types";

export default function QuestionDetail() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const [question, setQuestion] = useState<Question | null>(null);
  const [error, setError] = useState("");
  const [commentBody, setCommentBody] = useState("");
  const [commentImage, setCommentImage] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState("");
  const [commentError, setCommentError] = useState("");
  const [posting, setPosting] = useState(false);

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

  if (error) {
    return (
      <div className="card text-center">
        <p className="text-red-600">{error}</p>
        <Link to="/" className="mt-3 inline-block text-sm text-indigo-600 hover:underline">
          ← Back to questions
        </Link>
      </div>
    );
  }
  if (!question) {
    return <p className="py-8 text-center text-slate-500">Loading…</p>;
  }

  const comments = question.comments ?? [];

  return (
    <div className="space-y-6">
      <article className="card">
        <h1 className="text-2xl font-bold text-slate-900">{question.title}</h1>
        <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
          <VoteButton question={question} />
          <span className="font-medium text-slate-700">@{question.author.username}</span>
          <span>{timeAgo(question.createdAt)}</span>
          <ShareButton question={question} />
        </div>
        <p className="mt-4 whitespace-pre-wrap text-slate-700">{question.body}</p>
        {question.imageUrl && (
          <a href={question.imageUrl} target="_blank" rel="noreferrer">
            <img
              src={question.imageUrl}
              alt="Question attachment"
              className="mt-4 max-h-96 rounded-lg border border-slate-200 object-contain"
            />
          </a>
        )}
      </article>

      <section>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">
          {comments.length} {comments.length === 1 ? "Comment" : "Comments"}
        </h2>

        <div className="space-y-3">
          {comments.map((comment) => (
            <div key={comment.id} className="card py-4">
              <div className="mb-1 flex items-center gap-3 text-xs text-slate-500">
                <span className="font-medium text-slate-700">@{comment.author.username}</span>
                <span>{timeAgo(comment.createdAt)}</span>
              </div>
              <p className="whitespace-pre-wrap text-sm text-slate-700">{comment.body}</p>
              {comment.imageUrl && (
                <a href={comment.imageUrl} target="_blank" rel="noreferrer">
                  <img
                    src={comment.imageUrl}
                    alt="Comment attachment"
                    className="mt-2 max-h-64 rounded-lg border border-slate-200 object-contain"
                  />
                </a>
              )}
            </div>
          ))}
          {comments.length === 0 && (
            <p className="text-sm text-slate-500">No comments yet.</p>
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
                  className="text-xs text-slate-500 hover:text-red-600"
                >
                  Remove image
                </button>
              )}
            </div>
            {imagePreview && (
              <img
                src={imagePreview}
                alt="Preview"
                className="mt-3 max-h-40 rounded-lg border border-slate-200 object-contain"
              />
            )}
            {commentError && <p className="mt-2 text-sm text-red-600">{commentError}</p>}
            <button
              type="submit"
              className="btn-primary mt-3"
              disabled={posting || !commentBody.trim()}
            >
              {posting ? "Posting…" : "Post Comment"}
            </button>
          </form>
        ) : (
          <p className="mt-4 text-sm text-slate-600">
            <Link
              to="/login"
              state={{ from: `/questions/${question.id}` }}
              className="font-medium text-indigo-600 hover:underline"
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
