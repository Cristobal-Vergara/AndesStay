import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { useIsAuthenticated } from "@azure/msal-react";

import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Reservations from "./pages/Reservations";
import Catalog from "./pages/Catalog";
import Reports from "./pages/Reports";
import Audit from "./pages/Audit";

function ProtectedRoute({ children }) {
    const isAuthenticated = useIsAuthenticated();

    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }

    return children;
}

function App() {
    return (
        <BrowserRouter>
            <Routes>

                <Route path="/login" element={<Login />} />

                <Route
                    path="/dashboard"
                    element={
                        <ProtectedRoute>
                            <Dashboard />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/reservations"
                    element={
                        <ProtectedRoute>
                            <Reservations />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/catalog"
                    element={
                        <ProtectedRoute>
                            <Catalog />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/reports"
                    element={
                        <ProtectedRoute>
                            <Reports />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/audit"
                    element={
                        <ProtectedRoute>
                            <Audit />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/"
                    element={<Navigate to="/dashboard" replace />}
                />

                <Route
                    path="*"
                    element={<Navigate to="/dashboard" replace />}
                />

            </Routes>
        </BrowserRouter>
    );
}

export default App;
