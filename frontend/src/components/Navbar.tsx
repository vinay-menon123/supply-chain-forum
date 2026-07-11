import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { startVisibilityInterval } from "../poll";

// Primary destinations sit inline; the rest fold into a "Community" dropdown.
const PRIMARY = [
  { to: "/", label: "Feed" },
  { to: "/ask", label: "Ask" },
  { to: "/jobs", label: "Jobs" },
  { to: "/agents", label: "🤖 Agents" },
];
const COMMUNITY = [
  { to: "/templates", label: "📚 Templates" },
  { to: "/leaderboard", label: "🏆 Leaderboard" },
  { to: "/events", label: "📅 Events" },
  { to: "/mentorship", label: "🤝 Mentorship" },
];

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [unread, setUnread] = useState(0);
  const [notif, setNotif] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const [dropdown, setDropdown] = useState<null | "community" | "account">(null);

  useEffect(() => {
    if (!user) {
      setUnread(0);
      setNotif(0);
      return;
    }
    let active = true;
    const load = () => {
      api<{ count: number }>("/messages/unread")
        .then((data) => active && setUnread(data.count))
        .catch(() => {});
      api<{ count: number }>("/notifications/unread")
        .then((data) => active && setNotif(data.count))
        .catch(() => {});
    };
    load();
    // Poll while the tab is visible; pause when backgrounded (see poll.ts).
    const stop = startVisibilityInterval(load, 20000);
    // The Notifications page fires this after marking everything read.
    const onRefresh = () => load();
    window.addEventListener("notif:refresh", onRefresh);
    return () => {
      active = false;
      stop();
      window.removeEventListener("notif:refresh", onRefresh);
    };
  }, [user]);

  // Close menus whenever the route changes
  useEffect(() => {
    setMenuOpen(false);
    setDropdown(null);
  }, [location.pathname]);

  function handleLogout() {
    logout();
    setMenuOpen(false);
    setDropdown(null);
    navigate("/");
  }

  const isActive = (to: string) =>
    location.pathname === to || (to !== "/" && location.pathname.startsWith(to));

  const navLinkClass = (to: string) =>
    `text-sm font-medium tracking-wide transition-all duration-200 ${
      isActive(to) ? "text-white" : "text-[#8A8F98] hover:text-white"
    }`;

  const communityActive = COMMUNITY.some((l) => isActive(l.to));

  return (
    <header className="sticky top-0 z-50 border-b border-white/[0.06] bg-bg-base/70 backdrop-blur-xl text-foreground font-sans">
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-4 sm:px-6">
        {/* Brand */}
        <Link
          to="/landing"
          className="group flex flex-none items-center gap-2 font-bold tracking-tight text-white"
        >
          <span className="rounded bg-accent px-2 py-1 text-xs text-white shadow-[0_0_12px_rgba(94,106,210,0.4)]">
            CSCEN
          </span>
          <span className="hidden whitespace-nowrap text-sm font-semibold tracking-wide xl:inline">
            CSCE Nexus
          </span>
        </Link>

        {/* Desktop nav */}
        <nav className="hidden items-center gap-5 lg:flex">
          {PRIMARY.map((l) => (
            <Link key={l.to} to={l.to} className={navLinkClass(l.to)}>
              {l.label}
            </Link>
          ))}

          {/* Community dropdown */}
          <div className="relative">
            <button
              onClick={() => setDropdown((d) => (d === "community" ? null : "community"))}
              className={`flex items-center gap-1 text-sm font-medium tracking-wide transition-all duration-200 ${
                communityActive || dropdown === "community" ? "text-white" : "text-[#8A8F98] hover:text-white"
              }`}
            >
              Community
              <svg className={`h-3 w-3 transition-transform ${dropdown === "community" ? "rotate-180" : ""}`} viewBox="0 0 12 12" fill="none">
                <path d="M3 4.5L6 7.5L9 4.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </button>
            {dropdown === "community" && (
              <div className="absolute left-0 top-full z-50 mt-2 w-44 rounded-xl border border-white/10 bg-bg-base/95 p-1.5 shadow-[0_8px_30px_rgba(0,0,0,0.6)] backdrop-blur-xl">
                {COMMUNITY.map((l) => (
                  <Link
                    key={l.to}
                    to={l.to}
                    className={`block rounded-lg px-3 py-2 text-sm transition ${
                      isActive(l.to) ? "bg-white/[0.06] text-white" : "text-[#8A8F98] hover:bg-white/[0.04] hover:text-white"
                    }`}
                  >
                    {l.label}
                  </Link>
                ))}
              </div>
            )}
          </div>
        </nav>

        {/* Desktop actions */}
        <div className="hidden flex-none items-center gap-4 lg:flex">
          {user ? (
            <>
              {/* Notification bell */}
              <Link
                to="/notifications"
                className={`relative ${isActive("/notifications") ? "text-white" : "text-[#8A8F98] hover:text-white"}`}
                title="Notifications"
              >
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                </svg>
                {notif > 0 && (
                  <span className="absolute -right-2 -top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-500 px-1 text-[9px] font-bold text-white">
                    {notif > 9 ? "9+" : notif}
                  </span>
                )}
              </Link>

              {/* Messages */}
              <Link
                to="/messages"
                className={`relative ${isActive("/messages") ? "text-white" : "text-[#8A8F98] hover:text-white"}`}
                title="Messages"
              >
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M8 10h8M8 14h5m-9 6l3-3h9a2 2 0 002-2V7a2 2 0 00-2-2H4a2 2 0 00-2 2v11z" />
                </svg>
                {unread > 0 && (
                  <span className="absolute -right-2 -top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-accent px-1 text-[9px] font-bold text-white">
                    {unread > 9 ? "9+" : unread}
                  </span>
                )}
              </Link>

              {/* Account dropdown */}
              <div className="relative">
                <button
                  onClick={() => setDropdown((d) => (d === "account" ? null : "account"))}
                  className="flex items-center gap-1.5 group"
                >
                  {user.avatarUrl ? (
                    <img
                      src={user.avatarUrl}
                      alt=""
                      referrerPolicy="no-referrer"
                      className="h-8 w-8 rounded-full border border-white/10 object-cover group-hover:border-accent transition-colors"
                    />
                  ) : (
                    <span className="grid h-8 w-8 place-items-center rounded-full bg-white/5 border border-white/10 text-xs font-semibold text-white group-hover:border-accent transition-colors">
                      {user.username.charAt(0).toUpperCase()}
                    </span>
                  )}
                  <svg className={`h-3 w-3 text-[#8A8F98] transition-transform ${dropdown === "account" ? "rotate-180" : ""}`} viewBox="0 0 12 12" fill="none">
                    <path d="M3 4.5L6 7.5L9 4.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </button>
                {dropdown === "account" && (
                  <div className="absolute right-0 top-full z-50 mt-2 w-52 rounded-xl border border-white/10 bg-bg-base/95 p-1.5 shadow-[0_8px_30px_rgba(0,0,0,0.6)] backdrop-blur-xl">
                    <div className="px-3 py-2 border-b border-white/[0.06] mb-1">
                      <p className="truncate text-sm font-semibold text-white">{user.name ?? user.username}</p>
                      <p className="truncate text-[11px] text-[#8A8F98]">@{user.username}</p>
                    </div>
                    <Link to={`/users/${user.username}`} className="block rounded-lg px-3 py-2 text-sm text-[#8A8F98] transition hover:bg-white/[0.04] hover:text-white">
                      👤 View profile
                    </Link>
                    <Link to="/settings" className="block rounded-lg px-3 py-2 text-sm text-[#8A8F98] transition hover:bg-white/[0.04] hover:text-white">
                      ⚙️ Settings
                    </Link>
                    {user.role === "ADMIN" && (
                      <Link to="/admin" className="block rounded-lg px-3 py-2 text-sm text-[#8A8F98] transition hover:bg-white/[0.04] hover:text-white">
                        🛡️ Admin
                      </Link>
                    )}
                    <button
                      onClick={handleLogout}
                      className="block w-full rounded-lg px-3 py-2 text-left text-sm text-[#8A8F98] transition hover:bg-white/[0.04] hover:text-white"
                    >
                      ↩ Log out
                    </button>
                  </div>
                )}
              </div>
            </>
          ) : (
            <Link to="/login" className="btn-primary py-1.5 px-4 text-xs">
              Login
            </Link>
          )}
        </div>

        {/* Mobile controls */}
        <div className="flex items-center gap-3 lg:hidden">
          {user && (
            <>
              <Link to="/notifications" className="relative text-[#8A8F98] hover:text-white" title="Notifications">
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                </svg>
                {notif > 0 && (
                  <span className="absolute -right-1.5 -top-1.5 flex h-3.5 min-w-3.5 items-center justify-center rounded-full bg-rose-500 px-1 text-[8px] font-bold text-white">
                    {notif > 9 ? "9+" : notif}
                  </span>
                )}
              </Link>
              <Link to="/messages" className="relative text-[#8A8F98] hover:text-white" title="Messages">
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M8 10h8M8 14h5m-9 6l3-3h9a2 2 0 002-2V7a2 2 0 00-2-2H4a2 2 0 00-2 2v11z" />
                </svg>
                {unread > 0 && (
                  <span className="absolute -right-1.5 -top-1.5 flex h-3.5 min-w-3.5 items-center justify-center rounded-full bg-accent px-1 text-[8px] font-bold text-white">
                    {unread > 9 ? "9+" : unread}
                  </span>
                )}
              </Link>
            </>
          )}
          <button
            onClick={() => setMenuOpen((v) => !v)}
            aria-label="Menu"
            aria-expanded={menuOpen}
            className="text-[#8A8F98] hover:text-white p-1.5"
          >
            {menuOpen ? (
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            ) : (
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16m-7 6h7" />
              </svg>
            )}
          </button>
        </div>
      </div>

      {/* Click-away backdrop for desktop dropdowns */}
      {dropdown && (
        <div className="fixed inset-0 z-40 hidden lg:block" onClick={() => setDropdown(null)} />
      )}

      {/* Mobile dropdown panel */}
      {menuOpen && (
        <div className="animate-fade-in border-t border-white/[0.06] bg-bg-base/95 px-6 py-4 backdrop-blur-xl lg:hidden">
          {user && (
            <Link
              to={`/users/${user.username}`}
              className="mb-4 flex items-center gap-3 rounded-lg border border-white/[0.06] bg-white/[0.02] p-3"
            >
              {user.avatarUrl ? (
                <img
                  src={user.avatarUrl}
                  alt=""
                  referrerPolicy="no-referrer"
                  className="h-9 w-9 rounded-full border border-white/10 object-cover"
                />
              ) : (
                <span className="grid h-9 w-9 place-items-center rounded-full bg-white/5 border border-white/10 text-sm font-semibold text-white">
                  {user.username.charAt(0).toUpperCase()}
                </span>
              )}
              <div className="min-w-0">
                <p className="truncate text-xs font-semibold text-white">{user.name ?? user.username}</p>
                <p className="text-[10px] text-[#8A8F98] truncate">View Profile →</p>
              </div>
            </Link>
          )}
          <div className="flex flex-col gap-1">
            {[...PRIMARY, ...COMMUNITY].map((l) => (
              <Link
                key={l.to}
                to={l.to}
                className="rounded-md px-3 py-2 text-sm font-medium text-[#8A8F98] transition hover:bg-white/[0.04] hover:text-white"
              >
                {l.label}
              </Link>
            ))}
            {user && (
              <>
                <Link
                  to="/notifications"
                  className="rounded-md px-3 py-2 text-sm font-medium text-[#8A8F98] transition hover:bg-white/[0.04] hover:text-white"
                >
                  🔔 Notifications{notif > 0 ? ` (${notif > 9 ? "9+" : notif})` : ""}
                </Link>
                <Link
                  to="/settings"
                  className="rounded-md px-3 py-2 text-sm font-medium text-[#8A8F98] transition hover:bg-white/[0.04] hover:text-white"
                >
                  ⚙️ Settings
                </Link>
                {user.role === "ADMIN" && (
                  <Link
                    to="/admin"
                    className="rounded-md px-3 py-2 text-sm font-medium text-[#8A8F98] transition hover:bg-white/[0.04] hover:text-white"
                  >
                    🛡️ Admin Dashboard
                  </Link>
                )}
              </>
            )}
          </div>
          <div className="mt-4 pt-4 border-t border-white/[0.06] flex items-center gap-3">
            {user ? (
              <>
                <Link to="/ask" className="btn-primary flex-1 text-center py-2 text-xs">
                  Ask a question
                </Link>
                <button onClick={handleLogout} className="btn-secondary py-2 px-4 text-xs">
                  Log out
                </button>
              </>
            ) : (
              <Link to="/login" className="btn-primary w-full text-center py-2 text-xs">
                Login
              </Link>
            )}
          </div>
        </div>
      )}
    </header>
  );
}
