import { ChangeEvent, FormEvent, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, apiForm } from "../api";
import { useAuth } from "../auth";
import RichText from "../components/RichText";
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

  // States for nested replies
  const [replyingToCommentId, setReplyingToCommentId] = useState<string | null>(null);
  const [replyBody, setReplyBody] = useState("");

  const isAdmin = user?.role === "ADMIN";

  useEffect(() => {
    window.scrollTo(0, 0);
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
      // Backend returns initialized upvote info for the new comment
      const newAnswer: Comment = {
        ...created,
        voteCount: 0,
        viewerHasVoted: false,
        comments: []
      };
      setQuestion((prev) =>
        prev ? { ...prev, comments: [...(prev.comments ?? []), newAnswer] } : prev
      );
      setCommentBody("");
      setCommentImage(null);
    } catch (err) {
      setCommentError(err instanceof Error ? err.message : "Failed to post answer");
    } finally {
      setPosting(false);
    }
  }

  async function submitReply(e: FormEvent, answerId: string) {
    e.preventDefault();
    if (!replyBody.trim()) return;
    setCommentError("");
    try {
      const form = new FormData();
      form.append("body", replyBody);
      form.append("parentId", answerId);
      const created = await apiForm<Comment>(`/questions/${id}/comments`, form);
      setQuestion((prev) => {
        if (!prev) return null;
        const updatedComments = (prev.comments ?? []).map((ans) => {
          if (ans.id === answerId) {
            return {
              ...ans,
              comments: [...(ans.comments ?? []), created]
            };
          }
          return ans;
        });
        return { ...prev, comments: updatedComments };
      });
      setReplyBody("");
      setReplyingToCommentId(null);
    } catch (err) {
      setCommentError(err instanceof Error ? err.message : "Failed to post comment");
    }
  }

  async function toggleCommentVote(commentId: string) {
    if (!user) {
      navigate("/login", { state: { from: `/questions/${id}` } });
      return;
    }
    try {
      const data = await api<{ voteCount: number; viewerHasVoted: boolean }>(
        `/questions/${id}/comments/${commentId}/vote`,
        { method: "POST" }
      );
      setQuestion((prev) => {
        if (!prev) return null;
        const updatedComments = (prev.comments ?? []).map((ans) => {
          if (ans.id === commentId) {
            return {
              ...ans,
              voteCount: data.voteCount,
              viewerHasVoted: data.viewerHasVoted
            };
          }
          return ans;
        });
        return { ...prev, comments: updatedComments };
      });
    } catch (err) {
      setCommentError(err instanceof Error ? err.message : "Failed to update vote");
    }
  }

  async function deleteQuestion() {
    if (!question) return;
    if (!window.confirm("Delete this question and all its answers?")) return;
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

  async function deleteComment(commentId: string, parentId?: string | null) {
    if (!window.confirm("Delete this reply?")) return;
    try {
      await api(`/questions/${id}/comments/${commentId}`, { method: "DELETE" });
      setQuestion((prev) => {
        if (!prev) return null;
        if (parentId) {
          // Deleting a child comment (reply)
          const updatedComments = (prev.comments ?? []).map((ans) => {
            if (ans.id === parentId) {
              return {
                ...ans,
                comments: (ans.comments ?? []).filter((r) => r.id !== commentId)
              };
            }
            return ans;
          });
          return { ...prev, comments: updatedComments };
        } else {
          // Deleting an answer
          // Clear accepted mark if matching
          let acceptedId = prev.acceptedCommentId;
          if (commentId === acceptedId) {
            acceptedId = null;
          }
          return {
            ...prev,
            acceptedCommentId: acceptedId,
            comments: (prev.comments ?? []).filter((c) => c.id !== commentId)
          };
        }
      });
    } catch (err) {
      setCommentError(err instanceof Error ? err.message : "Failed to delete comment");
    }
  }

  if (error) {
    return (
      <div className="card text-center bg-bg-elevated border-white/10 p-6 rounded-2xl">
        <p className="text-red-500">{error}</p>
        <Link
          to="/"
          className="mt-3 inline-block text-sm text-accent hover:underline"
        >
          ← Back to questions
        </Link>
      </div>
    );
  }
  if (!question) {
    return <p className="py-8 text-center text-[#8A8F98]">Loading…</p>;
  }

  const acceptedId = question.acceptedCommentId;
  
  // The backend already returns comments pre-sorted by upvotes descending and accepted answers first.
  const answers = question.comments ?? [];
  const canDeleteQuestion = user && (user.id === question.author.id || isAdmin);
  const canAccept = user && (user.id === question.author.id || isAdmin);

  return (
    <div className="space-y-6 max-w-5xl mx-auto py-4">
      {/* Question Card */}
      <article className="card p-6 md:p-8 bg-gradient-to-b from-white/[0.06] to-white/[0.01] border-white/[0.06] rounded-2xl shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <h1 className="min-w-0 break-words text-2xl sm:text-3xl font-semibold tracking-tight text-white leading-snug">
            {question.title}
          </h1>
          {canDeleteQuestion && (
            <button onClick={deleteQuestion} className="btn-danger flex-none py-1.5 px-3" title="Delete question">
              🗑 Delete
            </button>
          )}
        </div>
        
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <span className="pill pill-inactive text-[10px] py-0.5 px-2.5">
            {tagMeta(question.tag).emoji} {tagMeta(question.tag).label}
          </span>
          {acceptedId && (
            <span className="badge border-emerald-500/20 bg-emerald-500/10 text-emerald-400">
              ✓ Answered
            </span>
          )}
        </div>
        
        <div className="meta mt-4 border-t border-white/[0.04] pt-4 flex flex-wrap items-center justify-between gap-4">
          <div className="flex flex-wrap items-center gap-4 text-xs">
            <VoteButton question={question} />
            <span className="text-white/20">|</span>
            <Link to={`/users/${question.author.username}`} className="username-link font-medium">
              @{question.author.username}
            </Link>
            <span className="text-white/20">•</span>
            <span>{timeAgo(question.createdAt)}</span>
          </div>
          <ShareButton question={question} />
        </div>
        
        <p className="mt-6 whitespace-pre-wrap text-white/90 leading-relaxed font-sans text-sm break-words">
          <RichText text={question.body} />
        </p>
        
        {question.imageUrl && (
          <a href={question.imageUrl} target="_blank" rel="noreferrer" className="inline-block mt-5 max-w-full">
            <img
              src={question.imageUrl}
              alt="Question attachment"
              className="max-h-96 rounded-lg border border-white/10 object-contain shadow-md"
            />
          </a>
        )}
      </article>

      {/* Answers Section */}
      <section className="space-y-4">
        <h2 className="text-lg font-semibold text-white tracking-tight">
          {answers.length} {answers.length === 1 ? "Answer" : "Answers"}
        </h2>

        <div className="space-y-4">
          {answers.map((ans) => (
            <div
              key={ans.id}
              className={`card p-5 bg-gradient-to-b from-white/[0.05] to-white/[0.01] rounded-2xl ${
                ans.id === acceptedId
                  ? "border-emerald-500/30 shadow-[0_0_24px_rgba(16,185,129,0.08)] bg-emerald-950/5"
                  : "border-white/[0.06]"
              }`}
            >
              {ans.id === acceptedId && (
                <div className="mb-3.5 inline-flex items-center gap-1.5 rounded-full border border-emerald-500/20 bg-emerald-500/10 px-2.5 py-0.5 text-[10px] font-bold tracking-wide uppercase text-emerald-400">
                  ✓ Accepted Answer
                </div>
              )}
              
              <div className="meta mb-3 flex items-center justify-between border-b border-white/[0.04] pb-2.5 text-xs">
                <div className="flex items-center gap-3">
                  {/* Upvote Button for Answer */}
                  <button
                    onClick={() => toggleCommentVote(ans.id)}
                    className={`inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-md font-mono transition-all duration-200 ${
                      ans.viewerHasVoted
                        ? "bg-accent/20 text-accent border border-accent/40 shadow-[0_0_12px_rgba(94,106,210,0.25)]"
                        : "text-[#8A8F98] bg-white/5 border border-white/8 hover:text-white"
                    }`}
                  >
                    ▲ {ans.voteCount ?? 0}
                  </button>

                  <span className="text-white/20">|</span>

                  <Link to={`/users/${ans.author.username}`} className="username-link font-medium">
                    @{ans.author.username}
                  </Link>
                  <span className="text-white/20">•</span>
                  <span>{timeAgo(ans.createdAt)}</span>
                </div>
                
                <div className="flex items-center gap-2">
                  {canAccept && (
                    <button
                      onClick={() => toggleAccept(ans.id)}
                      className={`rounded-full px-2.5 py-0.5 text-[10px] font-semibold transition ${
                        ans.id === acceptedId
                          ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 hover:bg-emerald-500/20"
                          : "text-[#8A8F98] hover:text-white bg-white/5 border border-white/10"
                      }`}
                      title={ans.id === acceptedId ? "Unmark accepted answer" : "Mark as accepted answer"}
                    >
                      {ans.id === acceptedId ? "✓ Accepted" : "✓ Accept"}
                    </button>
                  )}
                  {user && (user.id === ans.author.id || isAdmin) && (
                    <button
                      onClick={() => deleteComment(ans.id)}
                      className="btn-danger p-1 text-xs"
                      title="Delete answer"
                    >
                      🗑
                    </button>
                  )}
                </div>
              </div>
              
              <p className="whitespace-pre-wrap text-sm text-white/90 leading-relaxed font-sans break-words">
                <RichText text={ans.body} />
              </p>
              
              {ans.imageUrl && (
                <a href={ans.imageUrl} target="_blank" rel="noreferrer" className="inline-block mt-3 max-w-full">
                  <img
                    src={ans.imageUrl}
                    alt="Answer attachment"
                    className="max-h-64 rounded-lg border border-white/10 object-contain shadow"
                  />
                </a>
              )}

              {/* Nested Comments (Replies) List */}
              {ans.comments && ans.comments.length > 0 && (
                <div className="mt-5 ml-6 pl-4 border-l border-white/[0.08] space-y-3.5">
                  {ans.comments.map((reply) => (
                    <div key={reply.id} className="relative group bg-white/[0.01] border border-white/[0.03] p-3.5 rounded-xl">
                      <div className="flex items-center justify-between mb-1.5 text-[11px] text-[#8A8F98]">
                        <div className="flex items-center gap-2">
                          <Link to={`/users/${reply.author.username}`} className="username-link font-medium">
                            @{reply.author.username}
                          </Link>
                          <span>•</span>
                          <span>{timeAgo(reply.createdAt)}</span>
                        </div>
                        {user && (user.id === reply.author.id || isAdmin) && (
                          <button
                            onClick={() => deleteComment(reply.id, ans.id)}
                            className="text-red-400/60 hover:text-red-400 font-mono text-[9px] opacity-0 group-hover:opacity-100 transition-opacity"
                            title="Delete reply"
                          >
                            delete
                          </button>
                        )}
                      </div>
                      <p className="whitespace-pre-wrap break-words text-white/80 leading-relaxed text-xs font-sans">
                        <RichText text={reply.body} />
                      </p>
                    </div>
                  ))}
                </div>
              )}

              {/* Reply trigger button and form */}
              <div className="mt-4 border-t border-white/[0.04] pt-3 flex items-center">
                {replyingToCommentId === ans.id ? (
                  <form onSubmit={(e) => submitReply(e, ans.id)} className="w-full flex gap-2.5 items-stretch animate-fade-in">
                    <input
                      className="input py-1.5 px-3 text-xs flex-1"
                      placeholder="Comment on this answer..."
                      value={replyBody}
                      onChange={(e) => setReplyBody(e.target.value)}
                      required
                      autoFocus
                    />
                    <button type="submit" className="btn-primary py-1.5 px-3.5 text-xs">
                      Post
                    </button>
                    <button
                      type="button"
                      onClick={() => setReplyingToCommentId(null)}
                      className="btn-secondary py-1.5 px-3 text-xs"
                    >
                      Cancel
                    </button>
                  </form>
                ) : (
                  <button
                    onClick={() => {
                      setReplyingToCommentId(ans.id);
                      setReplyBody("");
                    }}
                    className="inline-flex items-center gap-1.5 text-xs text-[#8A8F98] hover:text-accent transition-colors font-medium"
                  >
                    💬 Add Comment
                  </button>
                )}
              </div>
            </div>
          ))}
          {answers.length === 0 && (
            <p className="text-sm text-[#8A8F98] text-center py-6">
              No answers yet — start the discussion! 💡
            </p>
          )}
        </div>

        {/* Post Answer Form */}
        {user ? (
          <form onSubmit={submitComment} className="card mt-6 p-6 bg-gradient-to-b from-white/[0.04] to-white/[0.01] border-white/[0.06] rounded-2xl">
            <label className="label text-[10px]" htmlFor="comment">
              Your Answer
            </label>
            <textarea
              id="comment"
              className="input min-h-[120px] resize-y leading-relaxed"
              rows={4}
              placeholder="Share your experience, solutions, or logistical insights…"
              value={commentBody}
              onChange={(e) => setCommentBody(e.target.value)}
            />
            <div className="mt-3 flex items-center gap-3">
              <label className="btn-secondary cursor-pointer py-1.5 px-3 text-xs">
                📎 Attach asset image
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
                  className="text-xs text-[#8A8F98] hover:text-white font-semibold"
                >
                  Clear
                </button>
              )}
            </div>
            {imagePreview && (
              <div className="mt-3 rounded-lg overflow-hidden border border-white/10 bg-black/40 p-1 max-w-sm">
                <img
                  src={imagePreview}
                  alt="Preview"
                  className="max-h-40 rounded-md object-contain"
                />
              </div>
            )}
            {commentError && (
              <p className="mt-3 text-xs text-red-400 bg-red-950/10 border border-red-500/20 p-2.5 rounded-lg">
                ⚠️ {commentError}
              </p>
            )}
            <button
              type="submit"
              className="btn-primary mt-4 py-2 px-5 text-xs"
              disabled={posting || !commentBody.trim()}
            >
              {posting ? "Posting…" : "Post Answer"}
            </button>
          </form>
        ) : (
          <p className="mt-6 text-sm text-[#8A8F98] text-center border border-white/[0.04] p-4 rounded-xl bg-white/[0.01]">
            <Link
              to="/login"
              state={{ from: `/questions/${question.id}` }}
              className="font-semibold text-accent hover:underline"
            >
              Log in
            </Link>{" "}
            to join this operations discussion.
          </p>
        )}
      </section>
    </div>
  );
}
