import { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";

const FREE_FEATURES = [
  "Browse all listings & the community Q&A",
  "Post questions and up to a few listings",
  "Contact up to 5 suppliers / month",
  "Standard placement in search",
];

const PRO_FEATURES = [
  "Unlimited supplier contacts",
  "✅ Verified Pro Supplier badge",
  "Priority / featured placement — your listings rank first",
  "Lead inbox — see exactly who's interested and reply",
  "Share direct contact details in chat",
  "Buyer RFQ (Wanted) alerts",
];

export default function Pricing() {
  const { user } = useAuth();
  const [requesting, setRequesting] = useState(false);
  const [requested, setRequested] = useState(false);
  const [error, setError] = useState("");

  async function requestPro() {
    if (!user) return;
    setRequesting(true);
    setError("");
    try {
      await api("/contact", {
        method: "POST",
        body: JSON.stringify({
          name: user.name ?? user.username,
          email: user.email ?? "",
          message: `Pro upgrade request from @${user.username} (${user.email ?? "no email"}). Please activate a Pro Supplier subscription.`,
        }),
      });
      setRequested(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send your request");
    } finally {
      setRequesting(false);
    }
  }

  return (
    <div className="mx-auto max-w-4xl">
      <div className="animate-fade-in-up text-center mb-10">
        <span className="text-xs font-semibold uppercase tracking-widest font-mono text-accent">
          Membership
        </span>
        <h1 className="mt-1 text-3xl sm:text-4xl font-bold tracking-tight text-white">
          Grow your business on <span className="gradient-text">CSCE Nexus</span>
        </h1>
        <p className="mx-auto mt-3 max-w-xl text-sm text-[#8A8F98] leading-relaxed">
          The community and Q&A are free, forever. Suppliers who want more reach, more leads, and
          direct buyer contact go Pro.
        </p>
      </div>

      <div className="grid gap-5 md:grid-cols-2 items-start">
        {/* Free */}
        <div className="card p-7 rounded-2xl">
          <h2 className="text-lg font-semibold text-white">Member</h2>
          <p className="meta mt-1">For browsing, learning, and getting started.</p>
          <div className="mt-4 text-3xl font-bold text-white">
            ₹0<span className="text-sm font-normal text-[#8A8F98]"> / forever</span>
          </div>
          <ul className="mt-5 space-y-2.5">
            {FREE_FEATURES.map((f) => (
              <li key={f} className="flex items-start gap-2 text-sm text-slate-300">
                <span className="text-emerald-400">✓</span> {f}
              </li>
            ))}
          </ul>
          <div className="mt-6">
            {user ? (
              <span className="btn-secondary w-full cursor-default opacity-70">
                {user.pro ? "Included in Pro" : "Your current plan"}
              </span>
            ) : (
              <Link to="/login" className="btn-secondary w-full text-center">
                Create a free account
              </Link>
            )}
          </div>
        </div>

        {/* Pro */}
        <div className="card p-7 rounded-2xl border-accent/40 ring-1 ring-accent/30 relative overflow-hidden">
          <div className="absolute -top-px right-5 rounded-b-md bg-accent px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider text-white">
            Most value
          </div>
          <h2 className="text-lg font-semibold text-white flex items-center gap-2">
            ⭐ Pro Supplier
          </h2>
          <p className="meta mt-1">For vendors who want to win business.</p>
          <div className="mt-4 text-3xl font-bold text-white">
            ₹1,999<span className="text-sm font-normal text-[#8A8F98]"> / month</span>
          </div>
          <p className="text-[11px] text-[#8A8F98] mt-1">Billed annually, or ₹2,499 monthly. Introductory pricing.</p>
          <ul className="mt-5 space-y-2.5">
            {PRO_FEATURES.map((f) => (
              <li key={f} className="flex items-start gap-2 text-sm text-slate-200">
                <span className="text-accent">✓</span> {f}
              </li>
            ))}
          </ul>
          <div className="mt-6">
            {user?.pro ? (
              <span className="btn-primary w-full cursor-default opacity-80">✓ You're a Pro Supplier</span>
            ) : user ? (
              requested ? (
                <span className="block w-full text-center text-sm font-medium text-emerald-400 bg-emerald-950/20 border border-emerald-500/25 rounded-lg py-2.5">
                  ✓ Request sent — our team will activate Pro shortly.
                </span>
              ) : (
                <button onClick={requestPro} className="btn-primary w-full" disabled={requesting}>
                  {requesting ? "Sending…" : "Request Pro access →"}
                </button>
              )
            ) : (
              <Link to="/login" className="btn-primary w-full text-center">
                Log in to go Pro
              </Link>
            )}
            {error && <p className="mt-2 text-xs text-red-400">{error}</p>}
            <p className="mt-3 text-center text-[11px] text-[#8A8F98]">
              Online checkout is coming soon — for now our team activates Pro within 24 hours.
            </p>
          </div>
        </div>
      </div>

      <p className="mt-8 text-center text-xs text-[#8A8F98]">
        Questions about membership?{" "}
        <Link to="/" className="text-accent hover:underline">
          Reach our Support Desk
        </Link>
        .
      </p>
    </div>
  );
}
