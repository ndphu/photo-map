import { useNavigate } from "react-router-dom";
import { PagePanel } from "../components/PagePanel";
import { useAuthStore } from "../store/authStore";

export function SettingsPage() {
  const logout = useAuthStore((state) => state.logout);
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <PagePanel title="Settings">
      <p>
        Session controls are available now. Additional user preferences can be
        added in a later phase.
      </p>
      <p style={{ marginTop: 12 }}>
        <button type="button" className="logout-btn" onClick={handleLogout}>
          Logout
        </button>
      </p>
    </PagePanel>
  );
}
