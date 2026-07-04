import { ReactNode } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./auth";
import Navbar from "./components/Navbar";
import { Link } from "react-router-dom";
import Admin from "./pages/Admin";
import Ask from "./pages/Ask";
import Events from "./pages/Events";
import Feed from "./pages/Feed";
import Landing from "./pages/Landing";
import Leaderboard from "./pages/Leaderboard";
import Login from "./pages/Login";
import Mentorship from "./pages/Mentorship";
import Messages from "./pages/Messages";
import MessageThread from "./pages/MessageThread";
import Profile from "./pages/Profile";
import QuestionDetail from "./pages/QuestionDetail";
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

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 transition-colors dark:bg-slate-950 dark:text-slate-100">
      <Navbar />
      {user?.isBanned && (
        <div className="border-b border-red-200 bg-red-50 px-4 py-2 text-center text-sm text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-300">
          Your account is suspended due to repeated guideline violations. You can browse, but
          posting is disabled.
        </div>
      )}
      {user && !user.memberType && (
        <div className="border-b border-indigo-200 bg-indigo-50 px-4 py-2 text-center text-sm text-indigo-700 dark:border-indigo-900 dark:bg-indigo-950 dark:text-indigo-300">
          👋 Finish setting up your profile —{" "}
          <Link to="/welcome" className="font-semibold underline">
            choose how you participate
          </Link>{" "}
          in the ecosystem.
        </div>
      )}
      <main className="mx-auto max-w-3xl px-4 py-8">
        <Routes>
          <Route
            path="/"
            element={user ? <Feed /> : loading ? <div className="py-16" /> : <Landing />}
          />
          <Route path="/questions" element={<Feed />} />
          <Route path="/questions/:id" element={<QuestionDetail />} />
          <Route path="/users/:username" element={<Profile />} />
          <Route path="/leaderboard" element={<Leaderboard />} />
          <Route path="/events" element={<Events />} />
          <Route path="/mentorship" element={<Mentorship />} />
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
    </div>
  );
}
