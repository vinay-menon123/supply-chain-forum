import { FormEvent, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import TopicPicker, { formatTopics, parseTopics } from "../components/TopicPicker";
import type { User } from "../types";

export default function Welcome() {
  const { user, updateUser } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/";

  const [topics, setTopics] = useState<string[]>(parseTopics(user?.topics));
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  function toggleTopic(value: string) {
    setTopics((prev) =>
      prev.includes(value) ? prev.filter((t) => t !== value) : [...prev, value]
    );
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!user) return;

    setError("");
    setSubmitting(true);
    try {
      const data = await api<{ user: User }>("/auth/profile", {
        method: "POST",
        body: JSON.stringify({
          memberType: user.memberType || "PROFESSIONAL",
          headline: user.headline || "",
          linkedinUrl: user.linkedinUrl || "",
          phone: user.phone || "",
          organization: user.organization || "",
          bio: user.bio || "",
          topics: formatTopics(topics),
          openToMentor: user.openToMentor,
          seekingMentor: user.seekingMentor,
        }),
      });
      updateUser(data.user);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save selected domains");
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl pt-10 pb-16">
      <div className="text-center mb-8">
        <h1 className="text-3xl font-bold tracking-tight text-white">
          Welcome to <span className="text-transparent bg-clip-text bg-gradient-to-r from-accent via-indigo-300 to-accent">CSCE Nexus</span>! 🎉
        </h1>
        <p className="text-sm text-[#8A8F98] mt-2 max-w-md mx-auto">
          Choose which supply chain domains you want to follow. We'll email you a notification each time a new question is posted in these areas.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="card bg-gradient-to-b from-white/[0.06] to-white/[0.01] border-white/[0.06] p-8 rounded-2xl space-y-6 text-left">
        <div>
          <span className="block text-xs font-semibold text-white uppercase tracking-wider mb-2">
            🔔 Select your SCM Domains of Interest
          </span>
          <p className="text-xs text-[#8A8F98] mb-4">
            You can modify your followed domains at any time directly from your Profile screen or Settings page.
          </p>
          <TopicPicker selected={topics} onToggle={toggleTopic} />
        </div>

        {error && (
          <div className="bg-red-500/10 border border-red-500/20 text-red-400 text-xs p-3 rounded-lg">
            ⚠️ {error}
          </div>
        )}

        <button
          type="submit"
          className="btn-primary w-full py-2.5 text-xs font-semibold shadow-[0_4px_12px_rgba(94,106,210,0.3)] mt-4"
          disabled={submitting}
        >
          {submitting ? "Saving..." : "Save Domains & Continue to Nexus →"}
        </button>
      </form>
    </div>
  );
}
