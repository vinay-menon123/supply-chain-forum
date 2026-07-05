import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import MemberTypeBadge from "../components/MemberTypeBadge";
import type { User } from "../types";

interface MentorshipBoard {
  mentors: User[];
  mentees: User[];
}

function PersonCard({ person, cta, index }: { person: User; cta: string; index: number }) {
  const { user: viewer } = useAuth();
  const isSelf = viewer?.id === person.id;
  return (
    <div
      className="card card-lift animate-fade-in-up flex items-center gap-3 py-4"
      style={{ animationDelay: `${index * 80}ms` }}
    >
      {person.avatarUrl ? (
        <img
          src={person.avatarUrl}
          alt=""
          referrerPolicy="no-referrer"
          className="h-12 w-12 flex-none rounded-full border-2 border-indigo-200 dark:border-indigo-800"
        />
      ) : (
        <div className="flex h-12 w-12 flex-none items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-lg font-bold text-white">
          {person.username[0].toUpperCase()}
        </div>
      )}
      <div className="min-w-0 flex-1">
        <Link to={`/users/${person.username}`} className="username-link font-semibold">
          {person.name || `@${person.username}`}
        </Link>
        <div className="mt-0.5 flex flex-wrap items-center gap-1.5">
          <MemberTypeBadge memberType={person.memberType} small />
          {person.organization && <span className="meta">{person.organization}</span>}
        </div>
      </div>
      {viewer && !isSelf && (
        <Link to={`/messages/${person.username}`} className="btn-primary flex-none text-xs">
          {cta}
        </Link>
      )}
    </div>
  );
}

export default function Mentorship() {
  const { user } = useAuth();
  const [board, setBoard] = useState<MentorshipBoard | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api<MentorshipBoard>("/mentorship")
      .then(setBoard)
      .catch((err) => setError(err.message));
  }, []);

  return (
    <div>
      <h1 className="heading animate-fade-in-up mb-1">
        🤝 Mentorship <span className="gradient-text">Match</span>
      </h1>
      <p className="meta animate-fade-in-up mb-6 [animation-delay:100ms]">
        Professionals and academicians guiding the next generation of supply chain talent. Update
        your preference on the{" "}
        <Link to="/welcome" className="text-indigo-600 hover:underline dark:text-indigo-400">
          profile setup page
        </Link>
        .
      </p>

      {error && <p className="card text-sm text-red-600 dark:text-red-400">{error}</p>}
      {!error && !board && (
        <p className="py-8 text-center text-slate-500 dark:text-slate-400">Loading…</p>
      )}

      {board && (
        <div className="grid gap-8 lg:grid-cols-2">
          <section>
            <h2 className="mb-3 text-lg font-semibold text-slate-900 dark:text-slate-100">
              🎓 Available mentors ({board.mentors.length})
            </h2>
            <div className="space-y-3">
              {board.mentors.map((person, i) => (
                <PersonCard key={person.id} person={person} cta="💬 Ask to mentor me" index={i} />
              ))}
              {board.mentors.length === 0 && (
                <div className="card text-center text-sm text-slate-500 dark:text-slate-400">
                  No mentors yet — be the first to raise your hand!
                </div>
              )}
            </div>
          </section>

          <section>
            <h2 className="mb-3 text-lg font-semibold text-slate-900 dark:text-slate-100">
              🌱 Looking for mentors ({board.mentees.length})
            </h2>
            <div className="space-y-3">
              {board.mentees.map((person, i) => (
                <PersonCard key={person.id} person={person} cta="💬 Offer to help" index={i} />
              ))}
              {board.mentees.length === 0 && (
                <div className="card text-center text-sm text-slate-500 dark:text-slate-400">
                  Nobody waiting right now.
                </div>
              )}
            </div>
          </section>
        </div>
      )}

      {!user && (
        <p className="animate-fade-in-up mt-8 text-center text-sm text-slate-600 dark:text-slate-400">
          <Link to="/login" className="font-medium text-indigo-600 hover:underline dark:text-indigo-400">
            Log in
          </Link>{" "}
          to join the mentorship program.
        </p>
      )}
    </div>
  );
}
