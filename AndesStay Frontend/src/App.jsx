import { lazy, Suspense } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { useIsAuthenticated, useMsal } from "@azure/msal-react";
import { useAccount } from "./auth/AccountContext";
import AccountProvider from "./components/AccountProvider";
const Login = lazy(() => import("./pages/Login"));
const Register = lazy(() => import("./pages/Register"));
const Dashboard = lazy(() => import("./pages/Dashboard"));
const Reservations = lazy(() => import("./pages/Reservations"));
const Catalog = lazy(() => import("./pages/Catalog"));
const Reports = lazy(() => import("./pages/Reports"));
const Audit = lazy(() => import("./pages/Audit"));

function ProtectedRoute({ children, roles }) {
    const authenticated = useIsAuthenticated();
    const { instance } = useMsal();
    const account = useAccount();
    if (!authenticated) return <Navigate to="/login" replace />;
    if (account.loading) return <div role="status" className="app-loading">Comprobando tu cuenta…</div>;
    if (account.error) return <main className="configuration-page"><h1>AndesStay</h1><p role="alert">{account.error}</p>
        <button type="button" onClick={account.refreshProfile}>Volver a intentar</button>{" "}
        <button type="button" className="button-secondary" onClick={() => instance.logoutRedirect()}>Cerrar sesión</button></main>;
    if (!account.profile?.registered && account.roles.length === 0) return <Navigate to="/register" replace />;
    if (roles && !roles.some(role => account.roles.includes(role))) return <Navigate to="/dashboard" replace />;
    return children;
}
export function AppRoutes() {
    const staffGuest = ["Admin", "Recepcionista", "Huesped"];
    return <Suspense fallback={<div role="status" className="app-loading">Cargando AndesStay…</div>}><Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/dashboard" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
        <Route path="/reservations" element={<ProtectedRoute roles={staffGuest}><Reservations /></ProtectedRoute>} />
        <Route path="/catalog" element={<ProtectedRoute roles={staffGuest}><Catalog /></ProtectedRoute>} />
        <Route path="/reports" element={<ProtectedRoute roles={["Admin"]}><Reports /></ProtectedRoute>} />
        <Route path="/audit" element={<ProtectedRoute roles={["Admin", "Auditor"]}><Audit /></ProtectedRoute>} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes></Suspense>;
}
export default function App() {
    return <AccountProvider><BrowserRouter><AppRoutes /></BrowserRouter></AccountProvider>;
}
