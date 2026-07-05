import { FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import TopicPicker, { formatTopics, parseTopics } from "../components/TopicPicker";
import { MEMBER_TYPES } from "../memberTypes";
import type { User } from "../types";

export default function Settings() {
  const { user, updateUser } = useAuth();

  const [memberType, setMemberType] = useState(user?.memberType ?? "");
  const [headline, setHeadline] = useState(user?.headline ?? "");
  const [organization, setOrganization] = useState(user?.organization ?? "");
  const [phone, setPhone] = useState(user?.phone ?? "");
  const [linkedinUrl, setLinkedinUrl] = useState(user?.linkedinUrl ?? "");
  const [bio, setBio] = useState(user?.bio ?? "");
  const [topics, setTopics] = useState<string[]>(parseTopics(user?.topics));
  const [openToMentor, setOpenToMentor] = useState(user?.openToMentor ?? false);
  const [seekingMentor, setSeekingMentor] = useState(user?.seekingMentor ?? false);
  const [notifyAllQuestions, setNotifyAllQuestions] = useState(user?.notifyAllQuestions ?? false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  function toggleTopic(value: string) {
    setSaved(false);
    setTopics((prev) =>
      prev.includes(value) ? prev.filter((t) => t !== value) : [...prev, value]
    );
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!memberType) {
      setError("Please choose how you participate in the community");
      return;
    }
    setError("");
    setSaved(false);
    setSubmitting(true);
    try {
      const data = await api<{ user: User }>("/auth/profile", {
        method: "POST",
        body: JSON.stringify({
          memberType,
          headline,
          organization,
          phone,
          linkedinUrl,
          bio,
          topics: formatTopics(topics),
          openToMentor,
          seekingMentor,
          notifyAllQuestions,
        }),
      });
      updateUser(data.user);
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save");
    } finally {
      setSubmitting(false);
    }
  }

  const verify = user?.verifyStatus ?? "PENDING";

  return (
    <div className="mx-auto max-w-2xl">
      <div className="animate-fade-in-up mb-6">
        <h1 className="heading">
          Profile <span className="gradient-text">settings</span>
        </h1>
        <p className="meta mt-1">Update how you show up in the community and what you hear about.</p>
      </div>

      <form onSubmit={handleSubmit} className="animate-fade-in-up space-y-6 [animation-delay:80ms]">
        {/* Verification status */}
        <div className="card">
          <span className="section-title">Membership verification</span>
          <div className="mt-2 flex flex-wrap items-center gap-3">
            {verify === "APPROVED" ? (
              <span className="badge bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">
                ✅ Verified supply chain member
              </span>
            ) : verify === "REJECTED" ? (
              <span className="badge bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300">
                ⏳ Under review
              </span>
            ) : (
              <span className="badge bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                ⏳ Pending review
              </span>
            )}
            <p className="meta">
              Add your headline and LinkedIn below — we use them to confirm members are in the
              supply chain field.
            </p>
          </div>
        </div>

        {/* Role / member type */}
        <div className="card">
          <span className="label">How you participate</span>
          <div className="grid gap-2 sm:grid-cols-2">
            {MEMBER_TYPES.map((type) => (
              <button
                type="button"
                key={type.value}
                onClick={() => {
                  setMemberType(type.value);
                  setSaved(false);
                }}
                className={`rounded-xl border p-3 text-left transition ${
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

        {/* Professional details */}
        <div className="card space-y-4">
          <span className="section-title">Professional details</span>
          <div>
            <label className="label" htmlFor="headline">
              Professional headline
            </label>
            <input
              id="headline"
              className="input"
              placeholder="e.g. Demand Planning Lead at Acme Logistics"
              value={headline}
              onChange={(e) => setHeadline(e.target.value)}
              maxLength={160}
            />
          </div>
          <div>
            <label className="label" htmlFor="linkedin">
              LinkedIn profile <span className="font-normal text-slate-400">(recommended)</span>
            </label>
            <input
              id="linkedin"
              className="input"
              placeholder="https://www.linkedin.com/in/your-name"
              value={linkedinUrl}
              onChange={(e) => setLinkedinUrl(e.target.value)}
            />
            <p className="meta mt-1">
              We check that new members are in the supply chain field to keep the community focused.
            </p>
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
            <label className="label" htmlFor="bio">
              About you <span className="font-normal text-slate-400">(optional)</span>
            </label>
            <textarea
              id="bio"
              className="input min-h-[90px]"
              placeholder="A sentence or two about your work in the supply chain."
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              maxLength={600}
            />
          </div>
        </div>

        {/* Topics */}
        <div className="card">
          <span className="label">🔔 Topics you follow</span>
          <p className="meta mb-3">
            Get an email when a new question is posted in these topics.
          </p>
          <TopicPicker selected={topics} onToggle={toggleTopic} />
        </div>

        {/* Email notification preference */}
        <div className="card">
          <span className="label">📬 Email notification preference</span>
          <button
            type="button"
            onClick={() => {
              setNotifyAllQuestions(!notifyAllQuestions);
              setSaved(false);
            }}
            className={`mt-2 w-full rounded-xl border p-4 text-left text-sm transition ${
              notifyAllQuestions
                ? "border-indigo-500 bg-indigo-50 ring-2 ring-indigo-200 dark:bg-indigo-950 dark:ring-indigo-800"
                : "border-slate-200 hover:border-indigo-300 dark:border-slate-700 dark:hover:border-indigo-700"
            }`}
          >
            <span className="font-semibold text-slate-900 dark:text-slate-100">
              🔔 Notify me for every new question
            </span>
            <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
              Receive an email whenever a new question is posted — regardless of topic. Leave off to only get emails for your followed topics above.
            </p>
          </button>
        </div>

        {/* Mentorship */}
        <div className="card">
          <span className="label">Mentorship 🤝</span>
          <div className="grid gap-2 sm:grid-cols-2">
            <button
              type="button"
              onClick={() => {
                setOpenToMentor(!openToMentor);
                setSaved(false);
              }}
              className={`rounded-xl border p-3 text-left text-sm transition ${
                openToMentor
                  ? "border-emerald-500 bg-emerald-50 ring-2 ring-emerald-200 dark:bg-emerald-950 dark:ring-emerald-800"
                  : "border-slate-200 hover:border-emerald-300 dark:border-slate-700"
              }`}
            >
              <span className="font-semibold text-slate-900 dark:text-slate-100">
                🎓 Open to mentoring others
              </span>
            </button>
            <button
              type="button"
              onClick={() => {
                setSeekingMentor(!seekingMentor);
                setSaved(false);
              }}
              className={`rounded-xl border p-3 text-left text-sm transition ${
                seekingMentor
                  ? "border-amber-500 bg-amber-50 ring-2 ring-amber-200 dark:bg-amber-950 dark:ring-amber-800"
                  : "border-slate-200 hover:border-amber-300 dark:border-slate-700"
              }`}
            >
              <span className="font-semibold text-slate-900 dark:text-slate-100">
                🌱 Looking for a mentor
              </span>
            </button>
          </div>
        </div>

        {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

        <div className="flex items-center gap-3">
          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting ? "Saving…" : "Save changes"}
          </button>
          {saved && (
            <span className="animate-fade-in text-sm font-medium text-emerald-600 dark:text-emerald-400">
              ✓ Saved
            </span>
          )}
          <Link to={`/users/${user?.username}`} className="btn-ghost ml-auto">
            View profile →
          </Link>
        </div>
      </form>
    </div>
  );
}
