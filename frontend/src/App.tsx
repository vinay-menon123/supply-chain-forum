import { ReactNode, useEffect } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./auth";
import Navbar from "./components/Navbar";
import { Link } from "react-router-dom";
import Admin from "./pages/Admin";
import Agents from "./pages/Agents";
import Ask from "./pages/Ask";
import Sourcing from "./pages/Sourcing";
import Events from "./pages/Events";
import Feed from "./pages/Feed";
import Jobs from "./pages/Jobs";
import Landing from "./pages/Landing";
import Leaderboard from "./pages/Leaderboard";
import Login from "./pages/Login";
import Mentorship from "./pages/Mentorship";
import Messages from "./pages/Messages";
import MessageThread from "./pages/MessageThread";
import Notifications from "./pages/Notifications";
import Profile from "./pages/Profile";
import QuestionDetail from "./pages/QuestionDetail";
import Settings from "./pages/Settings";
import Templates from "./pages/Templates";
import Welcome from "./pages/Welcome";

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <div className="py-16 text-center text-slate-500 dark:text-slate-400">Loading…</div>;
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return <>{children}</>;
}

export default function App() {
  const { user, loading } = useAuth();
  const location = useLocation();

  useEffect(() => {
    // Force dark mode globally for the Linear theme
    document.documentElement.classList.add("dark");
  }, []);

  const isLanding = location.pathname === "/landing" || (!user && location.pathname === "/");

  return (
    <div className="relative flex min-h-screen flex-col text-foreground font-sans selection:bg-accent/30 selection:text-white">
      {/* Calm layered aurora — painted ONCE, no continuous animation. A moving
          background (animated blur, a full-screen particle canvas, a repainting grid)
          was pinning the GPU every frame and making the whole app lag. The look stays;
          the per-frame cost is gone. A static blurred fixed layer composites for free,
          so scrolling opaque content over it is smooth. */}
      <div className="app-aurora">
        <div className="blob left-[-15%] top-[-12%] h-[40rem] w-[40rem] bg-accent/[0.35]" />
        <div className="blob right-[-12%] top-[2%] h-[34rem] w-[34rem] bg-violet-600/[0.30]" />
        <div className="blob hidden bottom-[-14%] left-[14%] h-[34rem] w-[34rem] bg-indigo-500/[0.26] md:block" />
        {/* Static grid texture (no drift animation) */}
        <div className="absolute inset-0 grid-pattern pointer-events-none" />
      </div>

      <Navbar />
      {user?.isBanned && (
        <div className="border-b border-red-950 bg-red-950/50 px-4 py-2 text-center text-sm text-red-300">
          Your account is suspended due to repeated guideline violations. You can browse, but
          posting is disabled.
        </div>
      )}
      {user && !user.memberType && (
        <div className="border-b border-accent/25 bg-accent/10 px-4 py-2 text-center text-sm text-indigo-300">
          👋 Finish setting up your profile —{" "}
          <Link to="/welcome" className="font-semibold underline text-white">
            choose how you participate
          </Link>{" "}
          in the ecosystem.
        </div>
      )}
      <main className={isLanding ? "w-full max-w-full overflow-x-hidden flex-1 animate-fade-in" : "mx-auto w-full max-w-5xl flex-1 px-4 py-6 sm:px-6 sm:py-10 overflow-x-hidden animate-fade-in"}>
        <Routes>
          <Route
            path="/"
            element={user ? <Feed /> : loading ? <div className="py-16" /> : <Landing />}
          />
          {/* Always-accessible marketing/landing page, even when signed in. */}
          <Route path="/landing" element={<Landing />} />
          <Route
            path="/questions"
            element={
              <RequireAuth>
                <Feed />
              </RequireAuth>
            }
          />
          <Route
            path="/questions/:id"
            element={
              <RequireAuth>
                <QuestionDetail />
              </RequireAuth>
            }
          />
          <Route path="/users/:username" element={<Profile />} />
          <Route path="/leaderboard" element={<Leaderboard />} />
          <Route path="/events" element={<Events />} />
          <Route path="/mentorship" element={<Mentorship />} />
          <Route path="/jobs" element={<Jobs />} />
          <Route path="/templates" element={<Templates />} />
          <Route
            path="/agents"
            element={
              <RequireAuth>
                <Agents />
              </RequireAuth>
            }
          />
          <Route
            path="/sourcing"
            element={
              <RequireAuth>
                <Sourcing />
              </RequireAuth>
            }
          />
          <Route
            path="/notifications"
            element={
              <RequireAuth>
                <Notifications />
              </RequireAuth>
            }
          />
          <Route
            path="/settings"
            element={
              <RequireAuth>
                <Settings />
              </RequireAuth>
            }
          />
          <Route
            path="/welcome"
            element={
              <RequireAuth>
                <Welcome />
              </RequireAuth>
            }
          />
          <Route
            path="/ask"
            element={
              <RequireAuth>
                <Ask />
              </RequireAuth>
            }
          />
          <Route
            path="/messages"
            element={
              <RequireAuth>
                <Messages />
              </RequireAuth>
            }
          />
          <Route
            path="/messages/:username"
            element={
              <RequireAuth>
                <MessageThread />
              </RequireAuth>
            }
          />
          <Route
            path="/admin"
            element={
              <RequireAuth>
                <Admin />
              </RequireAuth>
            }
          />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Navigate to="/login" replace />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      
      {/* Footer */}
      {!["/login", "/welcome"].includes(location.pathname) && (
        <footer className="border-t border-white/[0.06] bg-[#020203] py-12 mt-20 relative z-10">
          <div className="mx-auto max-w-5xl px-6 flex flex-col md:flex-row items-center justify-between gap-6">
            <div className="text-center md:text-left">
              <span className="text-xs font-semibold text-white tracking-wider">
                CSCE NEXUS
              </span>
              <p className="text-[11px] text-[#8A8F98] mt-1.5 font-sans">
                © 2026 Centre for Supply Chain Excellence. All rights reserved.
              </p>
            </div>
            <div className="flex items-center gap-6">
              <a 
                href="https://www.linkedin.com/company/centre-for-supply-chain-excellence/" 
                target="_blank" 
                rel="noreferrer"
                className="text-xs text-[#8A8F98] hover:text-white transition-colors flex items-center gap-1.5 font-medium"
              >
                <svg className="h-4 w-4 fill-current" viewBox="0 0 24 24">
                  <path d="M19 0h-14c-2.761 0-5 2.239-5 5v14c0 2.761 2.239 5 5 5h14c2.762 0 5-2.239 5-5v-14c0-2.761-2.238-5-5-5zm-11 19h-3v-11h3v11zm-1.5-12.268c-.966 0-1.75-.779-1.75-1.75s.784-1.75 1.75-1.75 1.75.779 1.75 1.75-.784 1.75-1.75 1.75zm13.5 12.268h-3v-5.604c0-3.368-4-3.113-4 0v5.604h-3v-11h3v1.765c1.396-2.586 7-2.777 7 2.476v6.759z"/>
                </svg>
                LinkedIn
              </a>
            </div>
          </div>
        </footer>
      )}
    </div>
  );
}
