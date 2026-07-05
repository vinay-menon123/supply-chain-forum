import React, { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { MEMBER_TYPES } from "../memberTypes";
import TopicPicker, { formatTopics } from "../components/TopicPicker";
import type { User } from "../types";

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/";

  const [activeTab, setActiveTab] = useState<"signin" | "register">("signin");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  // Registration Fields
  const [regFirstName, setRegFirstName] = useState("");
  const [regLastName, setRegLastName] = useState("");
  const [regIsStudent, setRegIsStudent] = useState(false);
  const [regOrg, setRegOrg] = useState(""); // Company or School
  const [regPos, setRegPos] = useState(""); // Position or Degree
  const [regPhone, setRegPhone] = useState("");
  const [regLinkedin, setRegLinkedin] = useState("");
  const [regUsername, setRegUsername] = useState("");
  const [regEmail, setRegEmail] = useState("");
  const [regPassword, setRegPassword] = useState("");
  const [regRole, setRegRole] = useState("PROFESSIONAL");
  const [regTopics, setRegTopics] = useState<string[]>([]);
  const [regOtp, setRegOtp] = useState("");

  // OTP states
  const [sendingOtp, setSendingOtp] = useState(false);
  const [otpSent, setOtpSent] = useState(false);

  // Login Fields — email-based (email doubles as the OTP destination)
  const [loginEmail, setLoginEmail] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const [loginOtp, setLoginOtp] = useState("");
  const [sendingLoginOtp, setSendingLoginOtp] = useState(false);
  const [loginOtpSent, setLoginOtpSent] = useState(false);

  function toggleRegTopic(value: string) {
    setRegTopics((prev) =>
      prev.includes(value) ? prev.filter((t) => t !== value) : [...prev, value]
    );
  }

  async function handleSendOtp(emailVal: string, isForLogin: boolean) {
    if (!emailVal) {
      setError("Please enter a valid email address first");
      return;
    }
    setError("");
    setSuccess("");
    if (isForLogin) setSendingLoginOtp(true);
    else setSendingOtp(true);

    try {
      const data = await api<{ success: boolean; message: string; devOtp?: string; notRegistered?: boolean }>(
        "/auth/send-otp",
        {
          method: "POST",
          body: JSON.stringify({ email: emailVal, intent: isForLogin ? "login" : "register" }),
        }
      );

      // Login with an unregistered email → bounce to Create Account, prefilled.
      if (isForLogin && data.notRegistered) {
        setActiveTab("register");
        setRegEmail(emailVal);
        setLoginOtpSent(false);
        setSuccess("");
        setError(`No account found for ${emailVal}. Please create an account to continue.`);
        return;
      }

      let msg = data.message;
      if (data.devOtp) {
        msg += `. Dev Mode OTP code: ${data.devOtp} (Auto-filled)`;
        if (isForLogin) setLoginOtp(data.devOtp);
        else setRegOtp(data.devOtp);
      }
      setSuccess(msg);
      if (isForLogin) setLoginOtpSent(true);
      else setOtpSent(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to send verification code");
    } finally {
      if (isForLogin) setSendingLoginOtp(false);
      else setSendingOtp(false);
    }
  }

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setSuccess("");

    if (!regFirstName || !regLastName) {
      setError("First and Last name are required");
      return;
    }
    if (!regOrg || !regPos) {
      setError(regIsStudent ? "School and Degree are required" : "Company and Position are required");
      return;
    }
    if (!regUsername || !regEmail || !regPassword) {
      setError("Username, Email, and Password are required");
      return;
    }
    if (!regOtp) {
      setError("Please enter the verification code sent to your email");
      return;
    }

    // Auto-attach role based on input type
    const finalMemberType = regIsStudent ? "STUDENT" : regRole;

    try {
      const data = await api<{ token: string; user: User }>("/auth/register", {
        method: "POST",
        body: JSON.stringify({
          username: regUsername,
          email: regEmail,
          password: regPassword,
          firstName: regFirstName,
          lastName: regLastName,
          organization: regOrg,
          position: regPos,
          phone: regPhone || null,
          linkedinUrl: regLinkedin || null,
          memberType: finalMemberType,
          otp: regOtp,
          topics: formatTopics(regTopics),
        }),
      });
      login(data.token, data.user);
      // Topics were chosen here, so skip the topic-only Welcome step; otherwise
      // send them there to pick some.
      navigate(regTopics.length > 0 ? from : "/welcome", { replace: true, state: { from } });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
    }
  }

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setSuccess("");

    if (!loginEmail || !loginPassword) {
      setError("Email and password are required");
      return;
    }

    if (!loginOtpSent) {
      setError("Please send and enter the verification code to authenticate");
      return;
    }

    if (!loginOtp) {
      setError("Verification code (OTP) is required");
      return;
    }

    try {
      const data = await api<{ token: string; user: User }>("/auth/login", {
        method: "POST",
        body: JSON.stringify({
          usernameOrEmail: loginEmail,
          password: loginPassword,
          otp: loginOtp,
        }),
      });
      login(data.token, data.user);
      if (!data.user.topics || data.user.topics.trim().length === 0) {
        navigate("/welcome", { replace: true, state: { from } });
      } else {
        navigate(from, { replace: true });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Invalid credentials or verification code");
    }
  }

  return (
    <div className="relative mx-auto max-w-lg pt-8 pb-16 px-4">
      {/* Background aurora blobs */}
      <div className="absolute top-[-10%] left-[-20%] w-[350px] h-[350px] rounded-full pointer-events-none opacity-20 bg-[radial-gradient(circle_at_center,rgba(94,106,210,0.3)_0%,transparent_70%)] animate-float" />
      <div className="absolute bottom-[-10%] right-[-20%] w-[350px] h-[350px] rounded-full pointer-events-none opacity-15 bg-[radial-gradient(circle_at_center,rgba(94,106,210,0.25)_0%,transparent_70%)] animate-float-slow" />

      <div className="text-center mb-8">
        <h1 className="text-3xl sm:text-4xl font-bold tracking-tight text-white">
          Access <span className="text-transparent bg-clip-text bg-gradient-to-r from-accent via-indigo-300 to-accent">CSCE Nexus</span>
        </h1>
        <p className="text-xs sm:text-sm text-[#8A8F98] mt-2 max-w-md mx-auto">
          Secure, verified operations and carrier benchmarking database for supply chain leaders.
        </p>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-white/[0.06] mb-6">
        <button
          onClick={() => {
            setActiveTab("signin");
            setError("");
            setSuccess("");
          }}
          className={`flex-1 pb-3 text-sm font-semibold border-b-2 transition-all ${
            activeTab === "signin"
              ? "border-[#5E6AD2] text-white"
              : "border-transparent text-[#8A8F98] hover:text-white"
          }`}
        >
          Login
        </button>
        <button
          onClick={() => {
            setActiveTab("register");
            setError("");
            setSuccess("");
          }}
          className={`flex-1 pb-3 text-sm font-semibold border-b-2 transition-all ${
            activeTab === "register"
              ? "border-[#5E6AD2] text-white"
              : "border-transparent text-[#8A8F98] hover:text-white"
          }`}
        >
          Create Account
        </button>
      </div>

      {/* Form Container */}
      <div className="card bg-gradient-to-b from-white/[0.06] to-white/[0.01] border-white/[0.06] p-6 sm:p-8 rounded-2xl shadow-[0_4px_30px_rgba(0,0,0,0.5)]">
        
        {error && (
          <div className="bg-red-500/10 border border-red-500/20 text-red-400 text-xs p-3 rounded-lg mb-4 text-left">
            ⚠️ {error}
          </div>
        )}
        {success && (
          <div className="bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs p-3 rounded-lg mb-4 text-left">
            ✓ {success}
          </div>
        )}

        {activeTab === "signin" ? (
          <form onSubmit={handleLogin} className="space-y-4 text-left">
            <div>
              <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-1.5">
                Email
              </label>
              <div className="flex gap-2">
                <input
                  type="email"
                  value={loginEmail}
                  onChange={(e) => setLoginEmail(e.target.value)}
                  placeholder="you@company.com"
                  className="flex-1 bg-[#0F0F12] border border-white/10 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-[#5E6AD2] focus:ring-1 focus:ring-[#5E6AD2]/50 transition-all"
                  required
                />
                <button
                  type="button"
                  disabled={sendingLoginOtp || !loginEmail}
                  onClick={() => handleSendOtp(loginEmail, true)}
                  className="bg-white/5 hover:bg-white/10 text-white border border-white/10 py-2 px-3 rounded-lg text-xs font-semibold disabled:opacity-40 transition-colors cursor-pointer whitespace-nowrap"
                >
                  {sendingLoginOtp ? "Sending..." : loginOtpSent ? "Resend" : "Send OTP"}
                </button>
              </div>
              <p className="text-[10px] text-[#8A8F98] mt-1.5">
                We'll email a one-time code. No account with this email? You'll be taken to Create Account.
              </p>
            </div>

            <div>
              <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-1.5">
                Password
              </label>
              <input
                type="password"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                placeholder="Enter password"
                className="w-full bg-[#0F0F12] border border-white/10 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-[#5E6AD2] focus:ring-1 focus:ring-[#5E6AD2]/50 transition-all"
                required
              />
            </div>

            {loginOtpSent && (
              <div>
                <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-1.5">
                  Verification Code (OTP)
                </label>
                <input
                  type="text"
                  maxLength={6}
                  value={loginOtp}
                  onChange={(e) => setLoginOtp(e.target.value)}
                  placeholder="Enter 6-digit OTP code"
                  className="w-full bg-[#0F0F12] border border-white/10 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-[#5E6AD2] focus:ring-1 focus:ring-[#5E6AD2]/50 transition-all text-center tracking-widest font-mono"
                  required
                />
              </div>
            )}

            <button
              type="submit"
              className="btn-primary w-full py-2.5 text-xs font-semibold shadow-[0_4px_12px_rgba(94,106,210,0.3)] mt-6"
            >
              Login
            </button>
          </form>
        ) : (
          <form onSubmit={handleRegister} className="space-y-4 text-left">
            
            {/* Row: Name */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-1.5">
                  First Name
                </label>
                <input
                  type="text"
                  value={regFirstName}
                  onChange={(e) => setRegFirstName(e.target.value)}
                  placeholder="John"
                  className="w-full bg-[#0F0F12] border border-white/10 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-[#5E6AD2] transition-all"
                  required
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-1.5">
                  Last Name
                </label>
                <input
                  type="text"
                  value={regLastName}
                  onChange={(e) => setRegLastName(e.target.value)}
                  placeholder="Doe"
                  className="w-full bg-[#0F0F12] border border-white/10 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-[#5E6AD2] transition-all"
                  required
                />
              </div>
            </div>

            {/* Toggle Switch: Working in Company / Student */}
            <div>
              <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-2">
                I am currently a
              </label>
              <div className="grid grid-cols-2 gap-2 bg-[#0a0a0c] p-1 rounded-lg border border-white/[0.06]">
                <button
                  type="button"
                  onClick={() => setRegIsStudent(false)}
                  className={`py-1.5 rounded-md text-xs font-semibold transition-all ${
                    !regIsStudent
                      ? "bg-[#5E6AD2] text-white shadow-sm"
                      : "text-[#8A8F98] hover:text-white"
                  }`}
                >
                  Working in Company
                </button>
                <button
                  type="button"
                  onClick={() => setRegIsStudent(true)}
                  className={`py-1.5 rounded-md text-xs font-semibold transition-all ${
                    regIsStudent
                      ? "bg-[#5E6AD2] text-white shadow-sm"
                      : "text-[#8A8F98] hover:text-white"
                  }`}
                >
                  Student
                </button>
              </div>
            </div>

            {/* Company Details or Student details */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-1.5">
                  {regIsStudent ? "School / University" : "Company Name"}
                </label>
                <input
                  type="text"
                  value={regOrg}
                  onChange={(e) => setRegOrg(e.target.value)}
                  placeholder={regIsStudent ? "MIT" : "Logistics Inc."}
                  className="w-full bg-[#0F0F12] border border-white/10 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-[#5E6AD2] transition-all"
                  required
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-1.5">
                  {regIsStudent ? "Degree studying" : "Position"}
                </label>
                <input
                  type="text"
                  value={regPos}
                  onChange={(e) => setRegPos(e.target.value)}
                  placeholder={regIsStudent ? "B.S. Supply Chain" : "Operations Manager"}
                  className="w-full bg-[#0F0F12] border border-white/10 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-[#5E6AD2] transition-all"
                  required
                />
              </div>
            </div>

            {/* SCM Role Selector - Only shown if working in company */}
            {!regIsStudent && (
              <div>
                <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-1.5">
                  SCM Role Type
                </label>
                <select
                  value={regRole}
                  onChange={(e) => setRegRole(e.target.value)}
                  className="w-full bg-[#0F0F12] border border-white/10 rounded-lg py-2 px-3 text-xs text-white focus:outline-none focus:border-[#5E6AD2] transition-all"
                >
                  {MEMBER_TYPES.filter(m => m.value !== "STUDENT").map(m => (
                    <option key={m.value} value={m.value} className="bg-[#0a0a0c] text-white">
                      {m.label}
                    </option>
                  ))}
                </select>
              </div>
            )}

            {/* Contact Details */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-1.5">
                  Phone (Optional)
                </label>
                <input
                  type="text"
                  value={regPhone}
                  onChange={(e) => setRegPhone(e.target.value)}
                  placeholder="+1 (555) 0199"
                  className="w-full bg-[#0F0F12] border border-white/10 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-[#5E6AD2] transition-all"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-1.5">
                  LinkedIn URL
                </label>
                <input
                  type="text"
                  value={regLinkedin}
                  onChange={(e) => setRegLinkedin(e.target.value)}
                  placeholder="linkedin.com/in/username"
                  className="w-full bg-[#0F0F12] border border-white/10 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-[#5E6AD2] transition-all"
                />
              </div>
            </div>

            {/* Username & Password */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-1.5">
                  Username
                </label>
                <input
                  type="text"
                  value={regUsername}
                  onChange={(e) => setRegUsername(e.target.value)}
                  placeholder="johndoe_scm"
                  className="w-full bg-[#0F0F12] border border-white/10 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-[#5E6AD2] transition-all"
                  required
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-1.5">
                  Password
                </label>
                <input
                  type="password"
                  value={regPassword}
                  onChange={(e) => setRegPassword(e.target.value)}
                  placeholder="••••••••"
                  className="w-full bg-[#0F0F12] border border-white/10 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-[#5E6AD2] transition-all"
                  required
                />
              </div>
            </div>

            {/* Topics of interest */}
            <div>
              <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-2">
                🔔 Topics you follow{" "}
                <span className="font-normal normal-case text-slate-500">
                  (we'll email you new questions in these — optional, editable anytime)
                </span>
              </label>
              <TopicPicker selected={regTopics} onToggle={toggleRegTopic} />
            </div>

            {/* Email OTP Verification */}
            <div>
              <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-1.5">
                Email Address
              </label>
              <div className="flex gap-2">
                <input
                  type="email"
                  value={regEmail}
                  onChange={(e) => setRegEmail(e.target.value)}
                  placeholder="name@company.com"
                  className="flex-1 bg-[#0F0F12] border border-white/10 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-[#5E6AD2] focus:ring-1 focus:ring-[#5E6AD2]/50 transition-all"
                  required
                />
                <button
                  type="button"
                  disabled={sendingOtp || !regEmail}
                  onClick={() => handleSendOtp(regEmail, false)}
                  className="bg-white/5 hover:bg-white/10 text-white border border-white/10 py-2 px-3 rounded-lg text-xs font-semibold disabled:opacity-40 transition-colors cursor-pointer"
                >
                  {sendingOtp ? "Sending..." : otpSent ? "Resend" : "Send OTP"}
                </button>
              </div>
            </div>

            {otpSent && (
              <div>
                <label className="block text-xs font-semibold text-white uppercase tracking-wider mb-1.5">
                  Verification Code (OTP)
                </label>
                <input
                  type="text"
                  maxLength={6}
                  value={regOtp}
                  onChange={(e) => setRegOtp(e.target.value)}
                  placeholder="Enter 6-digit OTP code"
                  className="w-full bg-[#0F0F12] border border-white/10 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-[#5E6AD2] text-center tracking-widest font-mono"
                  required
                />
              </div>
            )}

            <button
              type="submit"
              className="btn-primary w-full py-2.5 text-xs font-semibold shadow-[0_4px_12px_rgba(94,106,210,0.3)] mt-6"
            >
              Verify & Register
            </button>
          </form>
        )}
      </div>

      <p className="mt-8 text-center text-xs font-medium text-slate-500">
        One Mission. One Ecosystem. <span className="text-accent font-semibold">Limitless Impact.</span>
      </p>
    </div>
  );
}
