import { useEffect } from "react";
import type { Location } from "react-router-dom";
import {
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { AppShell } from "./components/AppShell";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { setUnauthorizedHandler } from "./lib/apiClient";
import { AlbumsPage } from "./pages/AlbumsPage";
import { AlbumDetailsPage } from "./pages/AlbumDetailsPage";
import {
  AssetDetailsModal,
  AssetDetailsPage,
} from "./pages/AssetDetailsPage";
import { GalleryPage } from "./pages/GalleryPage";
import { LoginPage } from "./pages/LoginPage";
import { SearchPage } from "./pages/SearchPage";
import { SettingsPage } from "./pages/SettingsPage";

function UnauthorizedRedirectSync() {
  const navigate = useNavigate();

  useEffect(() => {
    setUnauthorizedHandler(() => {
      navigate("/login", { replace: true });
    });

    return () => {
      setUnauthorizedHandler(null);
    };
  }, [navigate]);

  return null;
}

function App() {
  const location = useLocation();
  const routeState = location.state as
    | { backgroundLocation?: Location }
    | null;
  const backgroundLocation = routeState?.backgroundLocation;

  return (
    <>
      <UnauthorizedRedirectSync />
      <Routes location={backgroundLocation ?? location}>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route path="/" element={<Navigate to="/gallery" replace />} />
            <Route path="/gallery" element={<GalleryPage />} />
            <Route path="/favorites" element={<GalleryPage />} />
            <Route path="/archive" element={<GalleryPage />} />
            <Route path="/trash" element={<GalleryPage />} />
            <Route path="/assets/:id" element={<AssetDetailsPage />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/albums" element={<AlbumsPage />} />
            <Route path="/albums/:id" element={<AlbumDetailsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/gallery" replace />} />
      </Routes>

      {backgroundLocation ? (
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/assets/:id" element={<AssetDetailsModal />} />
          </Route>
        </Routes>
      ) : null}
    </>
  );
}

export default App;
