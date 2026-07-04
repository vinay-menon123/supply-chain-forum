import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { MEMBER_TYPES } from "../memberTypes";
import { TAGS } from "../tags";
import type { Leader, QuestionList } from "../types";

function useCountUp(target: number, durationMs = 1600): number {
  const [value, setValue] = useState(0);
  const started = useRef(false);

  useEffect(() => {
    if (target === 0 || started.current) return;
    started.current = true;
    const start = performance.now();
    let frame: number;
    const tick = (now: number) => {
      const progress = Math.min(1, (now - start) / durationMs);
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(Math.round(target * eased));
      if (progress < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [target, durationMs]);

  return value;
}

function Stat({ value, label, suffix = "" }: { value: number; label: string; suffix?: string }) {
  const display = useCountUp(value);
  return (
    <div className="text-center">
      <div className="gradient-text text-4xl font-extrabold sm:text-5xl">
        {display}
        {suffix}
      </div>
      <div className="mt-1 text-sm font-medium text-slate-500 dark:text-slate-400">{label}</div>
    </div>
  );
}

const TOGETHER = [
  { emoji: "🔗", title: "Connect", text: "Build meaningful connections across the ecosystem." },
  { emoji: "🤝", title: "Collaborate", text: "Work together to solve real-world supply chain challenges." },
  { emoji: "💡", title: "Contribute", text: "Share knowledge, insights and best practices." },
  { emoji: "🚀", title: "Innovate", text: "Drive innovation through ideas, technology and collective intelligence." },
  { emoji: "⭐", title: "Lead", text: "Develop leaders and shape the future of supply chains." },
];

const FEATURES = [
  {
    emoji: "💬",
    title: "Ask & answer",
    text: "Get practical answers from practitioners on demand planning, procurement, logistics and more.",
    to: "/questions",
    accent: "from-indigo-500 to-violet-500",
  },
  {
    emoji: "🏬",
    title: "Logistics Marketplace",
    text: "List or find warehouse space, freight capacity, equipment and services — deal directly with members.",
    to: "/marketplace",
    accent: "from-emerald-500 to-teal-500",
  },
  {
    emoji: "🤝",
    title: "Find a mentor",
    text: "Match with experienced professionals — or give back by mentoring the next generation.",
    to: "/mentorship",
    accent: "from-amber-500 to-orange-500",
  },
  {
    emoji: "📅",
    title: "Events & webinars",
    text: "Join live sessions and webinars, RSVP in a tap, and never miss what matters.",
    to: "/events",
    accent: "from-fuchsia-500 to-pink-500",
  },
];

export default function Landing() {
  const [questionCount, setQuestionCount] = useState(0);
  const [contributorCount, setContributorCount] = useState(0);

  useEffect(() => {
    api<QuestionList>("/questions").then((d) => setQuestionCount(d.total)).catch(() => {});
    api<{ leaders: Leader[] }>("/leaderboard")
      .then((d) => setContributorCount(d.leaders.length))
      .catch(() => {});
  }, []);

  return (
    <div className="relative -mx-4 -my-8 overflow-hidden px-4">
      {/* Floating gradient orbs */}
      <div className="blob left-[-8rem] top-[-6rem] h-96 w-96 animate-float bg-indigo-500/30 dark:bg-indigo-600/20" />
      <div className="blob right-[-10rem] top-40 h-[28rem] w-[28rem] animate-float-slow bg-fuchsia-500/25 dark:bg-fuchsia-600/15" />
      <div className="blob bottom-10 left-1/4 h-80 w-80 animate-float bg-cyan-400/25 dark:bg-cyan-500/15 [animation-delay:2s]" />

      {/* Hero */}
      <section className="relative mx-auto max-w-4xl pb-16 pt-20 text-center sm:pt-28">
        <span className="animate-fade-in-up inline-flex items-center gap-2 rounded-full border border-indigo-200 bg-white/70 px-4 py-1.5 text-xs font-semibold text-indigo-700 backdrop-blur dark:border-indigo-800 dark:bg-slate-900/70 dark:text-indigo-300">
          ✨ Centre for Supply Chain Excellence
        </span>
        <h1 className="animate-fade-in-up mt-6 text-4xl font-extrabold leading-tight tracking-tight text-slate-900 [animation-delay:100ms] dark:text-slate-100 sm:text-6xl">
          Where Supply Chain
          <br />
          <span className="gradient-text">Minds Connect</span>
        </h1>
        <p className="animate-fade-in-up mx-auto mt-6 max-w-2xl text-lg text-slate-600 [animation-delay:200ms] dark:text-slate-400">
          CSCE Nexus is the collaborative platform for the supply chain ecosystem — ask questions,
          share expertise, find mentors, attend events, and build your reputation among peers who
          get it.
        </p>
        <div className="animate-fade-in-up mt-10 flex flex-wrap items-center justify-center gap-4 [animation-delay:300ms]">
          <Link
            to="/login"
            className="btn-primary animate-pulse-glow px-8 py-3 text-base"
          >
            🚀 Join the community
          </Link>
          <Link to="/questions" className="btn-secondary px-8 py-3 text-base">
            Explore questions →
          </Link>
        </div>

        <div className="animate-fade-in-up mx-auto mt-16 grid max-w-lg grid-cols-3 gap-6 [animation-delay:450ms]">
          <Stat value={questionCount} label="Questions asked" />
          <Stat value={contributorCount} label="Contributors" />
          <Stat value={TAGS.length - 1} label="Expert topics" />
        </div>
      </section>

      {/* Member types */}
      <section className="relative mx-auto max-w-4xl pb-16">
        <h2 className="animate-fade-in-up text-center text-sm font-bold uppercase tracking-widest text-slate-500 dark:text-slate-400">
          One ecosystem · six ways to participate
        </h2>
        <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {MEMBER_TYPES.map((type, index) => (
            <div
              key={type.value}
              className="card card-lift animate-fade-in-up border-t-4 border-t-transparent hover:border-t-indigo-500"
              style={{ animationDelay: `${index * 120}ms` }}
            >
              <span className="text-3xl">{type.emoji}</span>
              <p className="mt-3 font-bold text-slate-900 dark:text-slate-100">{type.label}</p>
              <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{type.description}</p>
            </div>
          ))}
        </div>
      </section>

      {/* What you can do */}
      <section className="relative mx-auto max-w-5xl pb-16">
        <h2 className="animate-fade-in-up text-center text-3xl font-extrabold text-slate-900 dark:text-slate-100">
          Everything the ecosystem needs, <span className="gradient-text">in one place</span>
        </h2>
        <p className="animate-fade-in-up mx-auto mt-3 max-w-2xl text-center text-sm text-slate-500 [animation-delay:80ms] dark:text-slate-400">
          A verified, supply-chain-only community — so every conversation, connection and deal is
          with people who actually work in the field.
        </p>
        <div className="mt-10 grid gap-4 sm:grid-cols-2">
          {FEATURES.map((f, index) => (
            <Link
              key={f.title}
              to={f.to}
              className="card card-lift animate-fade-in-up group flex items-start gap-4"
              style={{ animationDelay: `${index * 100}ms` }}
            >
              <span
                className={`grid h-12 w-12 flex-none place-items-center rounded-2xl bg-gradient-to-br ${f.accent} text-2xl shadow-sm transition-transform group-hover:scale-110`}
              >
                {f.emoji}
              </span>
              <div>
                <p className="font-bold text-slate-900 dark:text-slate-100">
                  {f.title}
                  <span className="ml-1 inline-block transition-transform group-hover:translate-x-1">
                    →
                  </span>
                </p>
                <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{f.text}</p>
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* Together we */}
      <section className="relative mx-auto max-w-5xl pb-16">
        <h2 className="animate-fade-in-up text-center text-3xl font-extrabold text-slate-900 dark:text-slate-100">
          Together we <span className="gradient-text">go further</span>
        </h2>
        <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          {TOGETHER.map((item, index) => (
            <div
              key={item.title}
              className="card card-lift animate-fade-in-up text-center"
              style={{ animationDelay: `${index * 120}ms` }}
            >
              <span className="inline-block text-3xl transition-transform duration-300 hover:animate-wiggle">
                {item.emoji}
              </span>
              <p className="mt-2 font-bold text-slate-900 dark:text-slate-100">{item.title}</p>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{item.text}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Final CTA */}
      <section className="relative mx-auto mb-16 max-w-4xl">
        <div className="animate-fade-in-up overflow-hidden rounded-3xl bg-gradient-to-r from-indigo-600 via-violet-600 to-fuchsia-600 bg-[length:200%_200%] p-10 text-center text-white animate-gradient-x">
          <h2 className="text-2xl font-extrabold sm:text-3xl">
            One Mission. One Ecosystem. Limitless Impact.
          </h2>
          <p className="mx-auto mt-3 max-w-xl text-sm text-indigo-100">
            CSCE advances the profession. CSCEN connects the ecosystem. Together, we build the
            future of supply chains.
          </p>
          <Link
            to="/login"
            className="mt-6 inline-flex items-center gap-2 rounded-xl bg-white px-8 py-3 text-base font-bold text-indigo-700 shadow-lg transition hover:scale-105 hover:shadow-2xl"
          >
            Get started — it's free
          </Link>
        </div>
      </section>
    </div>
  );
}
