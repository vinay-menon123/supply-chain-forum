import { FormEvent, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { MEMBER_TYPES } from "../memberTypes";
import type { User } from "../types";

export default function Welcome() {
  const { user, updateUser } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/";

  const [memberType, setMemberType] = useState(user?.memberType ?? "");
  const [phone, setPhone] = useState(user?.phone ?? "");
  const [organization, setOrganization] = useState(user?.organization ?? "");
  const [openToMentor, setOpenToMentor] = useState(user?.openToMentor ?? false);
  const [seekingMentor, setSeekingMentor] = useState(user?.seekingMentor ?? false);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!memberType) {
      setError("Please choose how you'll participate in the community");
      return;
    }
    setError("");
    setSubmitting(true);
    try {
      const data = await api<{ user: User }>("/auth/profile", {
        method: "POST",
        body: JSON.stringify({ memberType, phone, organization, openToMentor, seekingMentor }),
      });
      updateUser(data.user);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save profile");
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="heading mb-1 text-center">Welcome to CSCE Nexus! 🎉</h1>
      <p className="mb-6 text-center text-sm text-slate-600 dark:text-slate-400">
        One quick step — tell the community how you participate in the supply chain ecosystem.
      </p>

      <form onSubmit={handleSubmit} className="card space-y-5">
        <div>
          <span className="label">I am joining as a…</span>
          <div className="grid gap-2 sm:grid-cols-2">
            {MEMBER_TYPES.map((type) => (
              <button
                type="button"
                key={type.value}
                onClick={() => setMemberType(type.value)}
                className={`rounded-lg border p-3 text-left transition ${
                  memberType === type.value
                    ? "border-indigo-500 bg-indigo-50 ring-2 ring-indigo-200 dark:bg-indigo-950 dark:ring-indigo-800"
                    : "border-slate-200 hover:border-indigo-300 dark:border-slate-700 dark:hover:border-indigo-700"
                }`}
              >
                <span className="font-semibold text-slate-900 dark:text-slate-100">
                  {type.emoji} {type.label}
                </span>
                <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                  {type.description}
                </p>
              </button>
            ))}
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="organization">
              Organization <span className="font-normal text-slate-400">(optional)</span>
            </label>
            <input
              id="organization"
              className="input"
              placeholder="Company, university or institute"
              value={organization}
              onChange={(e) => setOrganization(e.target.value)}
              maxLength={120}
            />
          </div>
          <div>
            <label className="label" htmlFor="phone">
              Phone <span className="font-normal text-slate-400">(optional)</span>
            </label>
            <input
              id="phone"
              type="tel"
              className="input"
              placeholder="+91 98765 43210"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
            />
          </div>
        </div>

        <div>
          <span className="label">Mentorship 🤝</span>
          <div className="grid gap-2 sm:grid-cols-2">
            <button
              type="button"
              onClick={() => setOpenToMentor(!openToMentor)}
              className={`rounded-lg border p-3 text-left text-sm transition ${
                openToMentor
                  ? "border-emerald-500 bg-emerald-50 ring-2 ring-emerald-200 dark:bg-emerald-950 dark:ring-emerald-800"
                  : "border-slate-200 hover:border-emerald-300 dark:border-slate-700"
              }`}
            >
              <span className="font-semibold text-slate-900 dark:text-slate-100">
                🎓 I'm open to mentoring others
              </span>
              <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                Appear on the mentorship board so members can reach out.
              </p>
            </button>
            <button
              type="button"
              onClick={() => setSeekingMentor(!seekingMentor)}
              className={`rounded-lg border p-3 text-left text-sm transition ${
                seekingMentor
                  ? "border-amber-500 bg-amber-50 ring-2 ring-amber-200 dark:bg-amber-950 dark:ring-amber-800"
                  : "border-slate-200 hover:border-amber-300 dark:border-slate-700"
              }`}
            >
              <span className="font-semibold text-slate-900 dark:text-slate-100">
                🌱 I'm looking for a mentor
              </span>
              <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                Let experienced members know you'd value their guidance.
              </p>
            </button>
          </div>
        </div>

        {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

        <button type="submit" className="btn-primary w-full" disabled={submitting}>
          {submitting ? "Saving…" : "Join the community"}
        </button>
      </form>
    </div>
  );
}
