import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { isDark, setTheme } from "../theme";

function ThemeToggle() {
  const [dark, setDark] = useState(isDark());

  function toggle() {
    setTheme(!dark);
    setDark(!dark);
  }

  return (
    <button
      onClick={toggle}
      title={dark ? "Switch to light mode" : "Switch to dark mode"}
      className="rounded-full p-2 text-lg transition hover:bg-slate-100 dark:hover:bg-slate-800"
    >
      {dark ? "☀️" : "🌙"}
    </button>
  );
}

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    if (!user) {
      setUnread(0);
      return;
    }
    let active = true;
    const load = () =>
      api<{ count: number }>("/messages/unread")
        .then((data) => active && setUnread(data.count))
        .catch(() => {});
    load();
    const timer = setInterval(load, 20000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [user]);

  function handleLogout() {
    logout();
    navigate("/");
  }

  return (
    <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/90 backdrop-blur transition-colors dark:border-slate-800 dark:bg-slate-950/90">
      <div className="mx-auto flex max-w-3xl items-center justify-between gap-3 px-4 py-3">
        <Link to="/" className="group flex items-center gap-2 font-bold text-slate-900 dark:text-slate-100">
          <span className="rounded-md bg-gradient-to-br from-indigo-500 via-violet-500 to-fuchsia-500 bg-[length:200%_200%] px-2 py-1 text-sm text-white shadow-sm transition-shadow animate-gradient-x group-hover:shadow-lg group-hover:shadow-indigo-500/40">
            CSCEN
          </span>
          <span className="hidden sm:inline">CSCE Nexus</span>
        </Link>
        <nav className="flex items-center gap-1.5">
          <ThemeToggle />
          <Link
            to="/leaderboard"
            title="Leaderboard"
            className="rounded-full p-2 text-lg transition hover:bg-slate-100 dark:hover:bg-slate-800"
          >
            🏆
          </Link>
          <Link
            to="/events"
            title="Events & webinars"
            className="rounded-full p-2 text-lg transition hover:bg-slate-100 dark:hover:bg-slate-800"
          >
            📅
          </Link>
          <Link
            to="/mentorship"
            title="Mentorship"
            className="rounded-full p-2 text-lg transition hover:bg-slate-100 dark:hover:bg-slate-800"
          >
            🤝
          </Link>
          {user ? (
            <>
              <Link
                to="/messages"
                title="Messages"
                className="relative rounded-full p-2 text-lg transition hover:bg-slate-100 dark:hover:bg-slate-800"
              >
                💬
                {unread > 0 && (
                  <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-xs font-bold text-white">
                    {unread > 9 ? "9+" : unread}
                  </span>
                )}
              </Link>
              {user.role === "ADMIN" && (
                <Link
                  to="/admin"
                  title="Admin dashboard"
                  className="rounded-full p-2 text-lg transition hover:bg-slate-100 dark:hover:bg-slate-800"
                >
                  🛡️
                </Link>
              )}
              <Link to="/ask" className="btn-primary ml-1">
                Ask
              </Link>
              <Link
                to={`/users/${user.username}`}
                title="Your profile"
                className="ml-1 flex items-center gap-2"
              >
                {user.avatarUrl ? (
                  <img
                    src={user.avatarUrl}
                    alt=""
                    referrerPolicy="no-referrer"
                    className="h-8 w-8 rounded-full border border-slate-200 transition hover:ring-2 hover:ring-indigo-400 dark:border-slate-700"
                  />
                ) : (
                  <span className="username-link hidden text-sm sm:inline">@{user.username}</span>
                )}
              </Link>
              <button
                onClick={handleLogout}
                className="ml-1 text-sm text-slate-500 transition hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200"
              >
                Log out
              </button>
            </>
          ) : (
            <Link to="/login" className="btn-primary ml-1">
              Sign in
            </Link>
          )}
        </nav>
      </div>
    </header>
  );
}
