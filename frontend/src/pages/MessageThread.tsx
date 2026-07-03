import { FormEvent, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api";
import type { ChatMessage, User } from "../types";

interface ThreadResponse {
  partner: User;
  messages: ChatMessage[];
}

export default function MessageThread() {
  const { username } = useParams<{ username: string }>();
  const [partner, setPartner] = useState<User | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [error, setError] = useState("");
  const [sending, setSending] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const loadedOnce = useRef(false);

  useEffect(() => {
    loadedOnce.current = false;
    setMessages([]);
    setPartner(null);
    setError("");

    let active = true;
    const load = () =>
      api<ThreadResponse>(`/messages/${username}`)
        .then((data) => {
          if (!active) return;
          setPartner(data.partner);
          setMessages(data.messages);
          loadedOnce.current = true;
        })
        .catch((err) => {
          if (active && !loadedOnce.current) setError(err.message);
        });

    load();
    const timer = setInterval(load, 4000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [username]);

  const messageCount = messages.length;
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "nearest" });
  }, [messageCount]);

  async function send(e: FormEvent) {
    e.preventDefault();
    if (!draft.trim() || sending) return;
    setSending(true);
    setError("");
    try {
      const created = await api<ChatMessage>(`/messages/${username}`, {
        method: "POST",
        body: JSON.stringify({ body: draft }),
      });
      setMessages((prev) => [...prev, created]);
      setDraft("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to send");
    } finally {
      setSending(false);
    }
  }

  if (error && !partner) {
    return (
      <div className="card text-center">
        <p className="text-red-600 dark:text-red-400">{error}</p>
        <Link
          to="/messages"
          className="mt-3 inline-block text-sm text-indigo-600 hover:underline dark:text-indigo-400"
        >
          ← Back to messages
        </Link>
      </div>
    );
  }

  return (
    <div className="flex h-[calc(100vh-8.5rem)] flex-col">
      <div className="mb-3 flex items-center gap-3">
        <Link
          to="/messages"
          className="rounded-full p-1.5 transition hover:bg-slate-100 dark:hover:bg-slate-800"
          title="All conversations"
        >
          ←
        </Link>
        {partner ? (
          <Link to={`/users/${partner.username}`} className="flex items-center gap-2">
            {partner.avatarUrl ? (
              <img
                src={partner.avatarUrl}
                alt=""
                referrerPolicy="no-referrer"
                className="h-9 w-9 rounded-full border border-slate-200 dark:border-slate-700"
              />
            ) : (
              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-sm font-bold text-white">
                {partner.username[0].toUpperCase()}
              </div>
            )}
            <span className="font-semibold text-slate-900 dark:text-slate-100">
              {partner.name || `@${partner.username}`}
            </span>
          </Link>
        ) : (
          <span className="text-slate-500 dark:text-slate-400">Loading…</span>
        )}
      </div>

      <div className="card flex-1 space-y-2 overflow-y-auto">
        {messages.map((msg) => (
          <div key={msg.id} className={`flex ${msg.fromMe ? "justify-end" : "justify-start"}`}>
            <div
              className={`max-w-[75%] whitespace-pre-wrap rounded-2xl px-3.5 py-2 text-sm ${
                msg.fromMe
                  ? "rounded-br-sm bg-gradient-to-r from-indigo-600 to-violet-600 text-white"
                  : "rounded-bl-sm bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-200"
              }`}
            >
              {msg.body}
            </div>
          </div>
        ))}
        {partner && messages.length === 0 && (
          <p className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
            Say hi to {partner.name || `@${partner.username}`} 👋
          </p>
        )}
        <div ref={bottomRef} />
      </div>

      <form onSubmit={send} className="mt-3 flex gap-2">
        <input
          className="input"
          placeholder="Type a message…"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          maxLength={2000}
        />
        <button type="submit" className="btn-primary flex-none" disabled={sending || !draft.trim()}>
          Send
        </button>
      </form>
      {error && partner && (
        <p className="mt-2 text-sm text-red-600 dark:text-red-400">{error}</p>
      )}
    </div>
  );
}
