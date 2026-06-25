import type { FormEvent } from "react";
import { useMemo } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "../store/authStore";

interface NavItem {
  label: string;
  to: string;
}

const navItems: NavItem[] = [
  { label: "Photos", to: "/gallery" },
  { label: "Favorites", to: "/favorites" },
  { label: "Albums", to: "/albums" },
  { label: "Archive", to: "/archive" },
  { label: "Trash", to: "/trash" },
  { label: "Settings", to: "/settings" },
];

function getPageTitle(pathname: string): string {
  if (pathname.startsWith("/assets/")) {
    return "Asset Details";
  }

  if (pathname.startsWith("/search")) {
    return "Search";
  }

  const found = navItems.find((item) => pathname.startsWith(item.to));
  return found ? found.label : "Photos";
}

function isNavItemActive(pathname: string, item: NavItem): boolean {
  if (item.to === "/gallery") {
    return pathname === "/gallery";
  }
  return pathname.startsWith(item.to);
}

export function AppShell() {
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);
  const navigate = useNavigate();
  const location = useLocation();
  const shellSearchDefaultValue = useMemo(() => {
    if (!location.pathname.startsWith("/search")) {
      return "";
    }

    return new URLSearchParams(location.search).get("q") ?? "";
  }, [location.pathname, location.search]);

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  const handleShellSearchSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const raw = formData.get("q");
    const query = typeof raw === "string" ? raw.trim() : "";

    if (!query) {
      navigate("/search");
      return;
    }

    navigate(`/search?q=${encodeURIComponent(query)}`);
  };

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Main navigation">
        <h2 className="brand">Private Cloud Gallery</h2>
        <ul className="nav-list">
          {navItems.map((item) => (
            <li key={item.to}>
              <NavLink
                to={item.to}
                className={({ isActive }) => {
                  if (isActive || isNavItemActive(location.pathname, item)) {
                    return "nav-link active";
                  }
                  return "nav-link";
                }}
              >
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>
        <div className="sidebar-footer">
          <button type="button" className="logout-btn" onClick={handleLogout}>
            Logout
          </button>
        </div>
      </aside>

      <div className="content-wrap">
        <header className="topbar">
          <div className="topbar-main">
            <h1 className="topbar-title">{getPageTitle(location.pathname)}</h1>
            <form className="shell-search" onSubmit={handleShellSearchSubmit} role="search">
              <input
                key={`shell-search-${location.pathname}-${location.search}`}
                name="q"
                aria-label="Search assets"
                defaultValue={shellSearchDefaultValue}
                placeholder="Search photos, videos, places..."
              />
            </form>
          </div>
          <div className="topbar-user">
            <div>{user?.displayName ?? "Unknown User"}</div>
            <div>{user?.email ?? ""}</div>
          </div>
        </header>
        <main className="page">
          <Outlet />
        </main>

        <nav className="mobile-nav" aria-label="Mobile navigation">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => {
                if (isActive || isNavItemActive(location.pathname, item)) {
                  return "mobile-nav-link active";
                }
                return "mobile-nav-link";
              }}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </div>
    </div>
  );
}
