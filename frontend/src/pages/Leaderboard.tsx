import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import MemberTypeBadge from "../components/MemberTypeBadge";
import type { Leader } from "../types";

const MEDALS = ["🥇", "🥈", "🥉"];

export default function Leaderboard() {
  const [leaders, setLeaders] = useState<Leader[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api<{ leaders: Leader[] }>("/leaderboard")
      .then((data) => setLeaders(data.leaders))
      .catch((err) => setError(err.message));
  }, []);

  return (
    <div>
      <h1 className="heading mb-1">🏆 Community Leaderboard</h1>
      <p className="meta mb-6">
        Reputation: 10 points per upvote received · 15 per accepted answer · 5 per question · 2
        per comment
      </p>

      {error && <p className="card text-sm text-red-600 dark:text-red-400">{error}</p>}
      {!error && !leaders && (
        <p className="py-8 text-center text-slate-500 dark:text-slate-400">Loading…</p>
      )}
      {leaders && leaders.length === 0 && (
        <div className="card text-center text-slate-500 dark:text-slate-400">
          No contributors yet — be the first on the board!
        </div>
      )}

      <div className="space-y-2">
        {leaders?.map((leader, index) => (
          <Link
            key={leader.user.id}
            to={`/users/${leader.user.username}`}
            className={`card card-hover flex items-center gap-3 py-3 ${
              index === 0 ? "border-amber-300 dark:border-amber-700" : ""
            }`}
          >
            <span className="w-8 flex-none text-center text-lg font-bold text-slate-400">
              {MEDALS[index] ?? index + 1}
            </span>
            {leader.user.avatarUrl ? (
              <img
                src={leader.user.avatarUrl}
                alt=""
                referrerPolicy="no-referrer"
                className="h-10 w-10 flex-none rounded-full border border-slate-200 dark:border-slate-700"
              />
            ) : (
              <div className="flex h-10 w-10 flex-none items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 font-bold text-white">
                {leader.user.username[0].toUpperCase()}
              </div>
            )}
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-semibold text-slate-900 dark:text-slate-100">
                  {leader.user.name || `@${leader.user.username}`}
                </span>
                <MemberTypeBadge memberType={leader.user.memberType} small />
              </div>
              <p className="meta">
                {leader.stats.questions} questions · {leader.stats.comments} comments ·{" "}
                {leader.stats.upvotesReceived} upvotes · {leader.stats.accepted} accepted
              </p>
            </div>
            <div className="flex-none text-right">
              <div className="text-lg font-bold text-indigo-600 dark:text-indigo-400">
                {leader.reputation}
              </div>
              <div className="meta">points</div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
