import { ReactNode, useEffect, useState } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./auth";
import Navbar from "./components/Navbar";
import BackgroundNetwork from "./components/BackgroundNetwork";
import { Link } from "react-router-dom";
import Admin from "./pages/Admin";
import Agents from "./pages/Agents";
import Ask from "./pages/Ask";
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
  const [scrollY, setScrollY] = useState(0);

  useEffect(() => {
    // Force dark mode globally for the Linear theme
    document.documentElement.classList.add("dark");

    const handleScroll = () => setScrollY(window.scrollY);
    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  const isLanding = location.pathname === "/landing" || (!user && location.pathname === "/");

  return (
    <div className="relative flex min-h-screen flex-col text-foreground font-sans selection:bg-accent/30 selection:text-white">
      {/* Cinematic Layered Background System — calm, slowly drifting multi-hue
          aurora across three parallax depths (global, so it shows on every screen).
          All motion is disabled under prefers-reduced-motion (see index.css). */}
      <div className="app-aurora">
        {/* Far layer — slow forward parallax */}
        <div
          style={{ transform: `translateY(${scrollY * 0.12}px)` }}
          className="absolute inset-0 pointer-events-none"
        >
          <div className="blob left-[-15%] top-[-12%] h-[52rem] w-[52rem] bg-accent/[0.45] animate-drift" />
          <div
            className="blob right-[-12%] top-[4%] h-[40rem] w-[40rem] bg-violet-600/[0.38] animate-float-slow"
            style={{ animationDelay: '2s' }}
          />
        </div>
        {/* Mid layer — gentle reverse parallax */}
        <div
          style={{ transform: `translateY(${scrollY * -0.06}px)` }}
          className="absolute inset-0 pointer-events-none hidden md:block"
        >
          <div
            className="blob right-[-8%] top-[38%] h-[38rem] w-[38rem] bg-indigo-500/[0.38] animate-drift-slow"
            style={{ animationDelay: '4s' }}
          />
          <div
            className="blob left-[6%] top-[46%] h-[34rem] w-[34rem] bg-sky-500/[0.30] animate-float"
            style={{ animationDelay: '1s' }}
          />
        </div>
        {/* Near layer — subtle forward parallax */}
        <div
          style={{ transform: `translateY(${scrollY * 0.04}px)` }}
          className="absolute inset-0 pointer-events-none hidden md:block"
        >
          <div
            className="blob bottom-[-18%] left-[18%] h-[46rem] w-[46rem] bg-accent/[0.35] animate-drift"
            style={{ animationDelay: '6s' }}
          />
          <div
            className="blob bottom-[-10%] right-[14%] h-[36rem] w-[36rem] bg-fuchsia-600/[0.30] animate-float-slow"
            style={{ animationDelay: '3s' }}
          />
        </div>
        {/* Grid Overlay with Drift Animation */}
        <div className="absolute inset-0 grid-pattern pointer-events-none" />
      </div>
      <BackgroundNetwork />

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
