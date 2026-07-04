import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { isDark, setTheme } from "../theme";

function ThemeToggle({ className = "" }: { className?: string }) {
  const [dark, setDark] = useState(isDark());

  function toggle() {
    setTheme(!dark);
    setDark(!dark);
  }

  return (
    <button
      onClick={toggle}
      title={dark ? "Switch to light mode" : "Switch to dark mode"}
      className={`rounded-full p-2 text-lg transition hover:bg-slate-100 dark:hover:bg-slate-800 ${className}`}
    >
      {dark ? "☀️" : "🌙"}
    </button>
  );
}

const NAV_LINKS = [
  { to: "/", emoji: "🧭", label: "Feed" },
  { to: "/leaderboard", emoji: "🏆", label: "Leaderboard" },
  { to: "/events", emoji: "📅", label: "Events" },
  { to: "/mentorship", emoji: "🤝", label: "Mentorship" },
  { to: "/marketplace", emoji: "🏬", label: "Marketplace" },
];

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [unread, setUnread] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);

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

  // Close the mobile menu whenever the route changes
  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  function handleLogout() {
    logout();
    setMenuOpen(false);
    navigate("/");
  }

  const iconLink = "rounded-full p-2 text-lg transition hover:bg-slate-100 dark:hover:bg-slate-800";

  return (
    <header className="sticky top-0 z-20 border-b border-slate-200/70 bg-white/80 backdrop-blur-md transition-colors dark:border-slate-800/70 dark:bg-slate-950/80">
      <div className="mx-auto flex max-w-4xl items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <Link
          to="/"
          className="group flex items-center gap-2 font-bold text-slate-900 dark:text-slate-100"
        >
          <span className="rounded-lg bg-gradient-to-br from-indigo-500 via-violet-500 to-fuchsia-500 bg-[length:200%_200%] px-2 py-1 text-sm text-white shadow-sm transition-shadow animate-gradient-x group-hover:shadow-lg group-hover:shadow-indigo-500/40">
            CSCEN
          </span>
          <span className="hidden sm:inline">CSCE Nexus</span>
        </Link>

        {/* Desktop nav */}
        <nav className="hidden items-center gap-1 lg:flex">
          <ThemeToggle />
          {NAV_LINKS.slice(1).map((l) => (
            <Link key={l.to} to={l.to} title={l.label} className={iconLink}>
              {l.emoji}
            </Link>
          ))}
          {user ? (
            <>
              <Link to="/messages" title="Messages" className={`relative ${iconLink}`}>
                💬
                {unread > 0 && (
                  <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-xs font-bold text-white">
                    {unread > 9 ? "9+" : unread}
                  </span>
                )}
              </Link>
              {user.role === "ADMIN" && (
                <Link to="/admin" title="Admin dashboard" className={iconLink}>
                  🛡️
                </Link>
              )}
              <Link to="/settings" title="Settings" className={iconLink}>
                ⚙️
              </Link>
              <Link to="/ask" className="btn-primary ml-1">
                Ask
              </Link>
              <Link to={`/users/${user.username}`} title="Your profile" className="ml-1">
                {user.avatarUrl ? (
                  <img
                    src={user.avatarUrl}
                    alt=""
                    referrerPolicy="no-referrer"
                    className="h-8 w-8 rounded-full border border-slate-200 transition hover:ring-2 hover:ring-indigo-400 dark:border-slate-700"
                  />
                ) : (
                  <span className="grid h-8 w-8 place-items-center rounded-full bg-indigo-100 text-sm font-bold text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300">
                    {user.username.charAt(0).toUpperCase()}
                  </span>
                )}
              </Link>
              <button
                onClick={handleLogout}
                title="Log out"
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

        {/* Mobile controls */}
        <div className="flex items-center gap-1 lg:hidden">
          <ThemeToggle />
          {user && (
            <Link to="/messages" title="Messages" className={`relative ${iconLink}`}>
              💬
              {unread > 0 && (
                <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">
                  {unread > 9 ? "9+" : unread}
                </span>
              )}
            </Link>
          )}
          <button
            onClick={() => setMenuOpen((v) => !v)}
            aria-label="Menu"
            aria-expanded={menuOpen}
            className={iconLink}
          >
            {menuOpen ? "✕" : "☰"}
          </button>
        </div>
      </div>

      {/* Mobile dropdown panel */}
      {menuOpen && (
        <div className="animate-fade-in border-t border-slate-200/70 bg-white/95 px-4 py-3 backdrop-blur lg:hidden dark:border-slate-800/70 dark:bg-slate-950/95">
          {user && (
            <Link
              to={`/users/${user.username}`}
              className="mb-2 flex items-center gap-3 rounded-xl border border-slate-200 p-3 dark:border-slate-800"
            >
              {user.avatarUrl ? (
                <img
                  src={user.avatarUrl}
                  alt=""
                  referrerPolicy="no-referrer"
                  className="h-10 w-10 rounded-full border border-slate-200 dark:border-slate-700"
                />
              ) : (
                <span className="grid h-10 w-10 place-items-center rounded-full bg-indigo-100 font-bold text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300">
                  {user.username.charAt(0).toUpperCase()}
                </span>
              )}
              <div className="min-w-0">
                <p className="truncate font-semibold text-slate-900 dark:text-slate-100">
                  {user.name ?? user.username}
                </p>
                <p className="meta truncate">View your profile →</p>
              </div>
            </Link>
          )}
          <div className="grid grid-cols-2 gap-1">
            {NAV_LINKS.map((l) => (
              <Link
                key={l.to}
                to={l.to}
                className="flex items-center gap-2 rounded-xl px-3 py-2.5 text-sm font-medium text-slate-700 transition hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-800"
              >
                <span className="text-lg">{l.emoji}</span> {l.label}
              </Link>
            ))}
            {user && (
              <Link
                to="/settings"
                className="flex items-center gap-2 rounded-xl px-3 py-2.5 text-sm font-medium text-slate-700 transition hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-800"
              >
                <span className="text-lg">⚙️</span> Settings
              </Link>
            )}
            {user?.role === "ADMIN" && (
              <Link
                to="/admin"
                className="flex items-center gap-2 rounded-xl px-3 py-2.5 text-sm font-medium text-slate-700 transition hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-800"
              >
                <span className="text-lg">🛡️</span> Admin
              </Link>
            )}
          </div>
          <div className="mt-3 flex items-center gap-2">
            {user ? (
              <>
                <Link to="/ask" className="btn-primary flex-1">
                  ✍️ Ask a question
                </Link>
                <button onClick={handleLogout} className="btn-secondary flex-none">
                  Log out
                </button>
              </>
            ) : (
              <Link to="/login" className="btn-primary w-full">
                Sign in
              </Link>
            )}
          </div>
        </div>
      )}
    </header>
  );
}
