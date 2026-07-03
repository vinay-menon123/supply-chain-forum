import { ReactNode } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./auth";
import Navbar from "./components/Navbar";
import Admin from "./pages/Admin";
import Ask from "./pages/Ask";
import Feed from "./pages/Feed";
import Login from "./pages/Login";
import Messages from "./pages/Messages";
import MessageThread from "./pages/MessageThread";
import Profile from "./pages/Profile";
import QuestionDetail from "./pages/QuestionDetail";

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
  const { user } = useAuth();

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 transition-colors dark:bg-slate-950 dark:text-slate-100">
      <Navbar />
      {user?.isBanned && (
        <div className="border-b border-red-200 bg-red-50 px-4 py-2 text-center text-sm text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-300">
          Your account is suspended due to repeated guideline violations. You can browse, but
          posting is disabled.
        </div>
      )}
      <main className="mx-auto max-w-3xl px-4 py-8">
        <Routes>
          <Route path="/" element={<Feed />} />
          <Route path="/questions/:id" element={<QuestionDetail />} />
          <Route path="/users/:username" element={<Profile />} />
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
