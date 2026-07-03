import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { timeAgo } from "../time";
import type { Conversation } from "../types";

export default function Messages() {
  const [conversations, setConversations] = useState<Conversation[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api<{ conversations: Conversation[] }>("/messages")
      .then((data) => setConversations(data.conversations))
      .catch((err) => setError(err.message));
  }, []);

  return (
    <div>
      <h1 className="heading mb-6">Messages</h1>
      {error && <p className="card text-sm text-red-600 dark:text-red-400">{error}</p>}
      {!error && !conversations && (
        <p className="py-8 text-center text-slate-500 dark:text-slate-400">Loading…</p>
      )}
      {conversations && conversations.length === 0 && (
        <div className="card text-center text-slate-500 dark:text-slate-400">
          No conversations yet. Visit someone's profile and hit{" "}
          <span className="font-medium">💬 Message</span> to start one!
        </div>
      )}
      <div className="space-y-2">
        {conversations?.map(({ partner, lastMessage, unread }) => (
          <Link
            key={partner.id}
            to={`/messages/${partner.username}`}
            className="card card-hover flex items-center gap-3 py-3"
          >
            {partner.avatarUrl ? (
              <img
                src={partner.avatarUrl}
                alt=""
                referrerPolicy="no-referrer"
                className="h-11 w-11 flex-none rounded-full border border-slate-200 dark:border-slate-700"
              />
            ) : (
              <div className="flex h-11 w-11 flex-none items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 font-bold text-white">
                {partner.username[0].toUpperCase()}
              </div>
            )}
            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between gap-2">
                <span className="font-semibold text-slate-900 dark:text-slate-100">
                  {partner.name || `@${partner.username}`}
                </span>
                <span className="meta flex-none">{timeAgo(lastMessage.createdAt)}</span>
              </div>
              <p
                className={`truncate text-sm ${
                  unread > 0
                    ? "font-semibold text-slate-800 dark:text-slate-200"
                    : "text-slate-500 dark:text-slate-400"
                }`}
              >
                {lastMessage.fromMe ? "You: " : ""}
                {lastMessage.body}
              </p>
            </div>
            {unread > 0 && (
              <span className="flex h-6 min-w-6 flex-none items-center justify-center rounded-full bg-indigo-600 px-1.5 text-xs font-bold text-white">
                {unread}
              </span>
            )}
          </Link>
        ))}
      </div>
    </div>
  );
}
