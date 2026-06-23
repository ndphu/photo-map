import { useEffect } from "react";
import { Navigate, Route, Routes, useNavigate } from "react-router-dom";
import { AppShell } from "./components/AppShell";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { setUnauthorizedHandler } from "./lib/apiClient";
import { AlbumsPage } from "./pages/AlbumsPage";
import { AlbumDetailsPage } from "./pages/AlbumDetailsPage";
import { AssetDetailsPage } from "./pages/AssetDetailsPage";
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
  return (
    <>
      <UnauthorizedRedirectSync />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route path="/" element={<Navigate to="/gallery" replace />} />
            <Route path="/gallery" element={<GalleryPage />} />
            <Route path="/assets/:id" element={<AssetDetailsPage />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/albums" element={<AlbumsPage />} />
            <Route path="/albums/:id" element={<AlbumDetailsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/gallery" replace />} />
      </Routes>
    </>
  );
}

export default App;
