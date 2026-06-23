import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "../store/authStore";

interface NavItem {
  label: string;
  to: string;
}

const navItems: NavItem[] = [
  { label: "Gallery", to: "/gallery" },
  { label: "Search", to: "/search" },
  { label: "Albums", to: "/albums" },
  { label: "Settings", to: "/settings" },
];

function getPageTitle(pathname: string): string {
  if (pathname.startsWith("/assets/")) {
    return "Asset Details";
  }

  const found = navItems.find((item) => pathname.startsWith(item.to));
  return found ? found.label : "Gallery";
}

export function AppShell() {
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
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
                className={({ isActive }) =>
                  isActive ? "nav-link active" : "nav-link"
                }
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
          <h1 className="topbar-title">{getPageTitle(location.pathname)}</h1>
          <div className="topbar-user">
            <div>{user?.displayName ?? "Unknown User"}</div>
            <div>{user?.email ?? ""}</div>
          </div>
        </header>
        <main className="page">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
