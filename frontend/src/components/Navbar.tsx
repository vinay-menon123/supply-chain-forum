import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth";

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/");
  }

  return (
    <header className="sticky top-0 z-10 border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-3xl items-center justify-between gap-4 px-4 py-3">
        <Link to="/" className="flex items-center gap-2 font-bold text-slate-900">
          <span className="rounded-md bg-indigo-600 px-2 py-1 text-sm text-white">SCF</span>
          <span className="hidden sm:inline">Supply Chain Forum</span>
        </Link>
        <nav className="flex items-center gap-3">
          {user ? (
            <>
              <Link to="/ask" className="btn-primary">
                Ask Question
              </Link>
              <span className="hidden items-center gap-2 text-sm font-medium text-slate-600 sm:flex">
                {user.avatarUrl && (
                  <img
                    src={user.avatarUrl}
                    alt=""
                    referrerPolicy="no-referrer"
                    className="h-7 w-7 rounded-full border border-slate-200"
                  />
                )}
                @{user.username}
              </span>
              <button
                onClick={handleLogout}
                className="text-sm text-slate-500 transition hover:text-slate-800"
              >
                Log out
              </button>
            </>
          ) : (
            <Link to="/login" className="btn-primary">
              Sign in
            </Link>
          )}
        </nav>
      </div>
    </header>
  );
}
