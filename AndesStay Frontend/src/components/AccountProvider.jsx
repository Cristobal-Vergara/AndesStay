import { useCallback, useState } from "react";
import { useIsAuthenticated } from "@azure/msal-react";
import { AccountContext } from "../auth/AccountContext";
import { useApi } from "../hooks/useApi";
import { useResource } from "../hooks/useResource";

const knownRoles = ["Admin", "Recepcionista", "Huesped", "Auditor"];
export default function AccountProvider({ children }) {
    const authenticated = useIsAuthenticated();
    const { request, account } = useApi();
    const [receipt, setReceipt] = useState(null);
    const load = useCallback(signal => request("/api/v1/bff/account/profile", { signal }), [request]);
    const resource = useResource(load, authenticated && !!account);
    const saved = receipt && receipt.account === account ? receipt.profile : null;
    const profile = saved || resource.data;
    const roles = Array.isArray(profile?.roles) ? profile.roles.filter(role => knownRoles.includes(role)) : [];
    const completeProfile = useCallback(profile => setReceipt({ account, profile }), [account]);
    const refreshProfile = () => { setReceipt(null); resource.refresh(); };
    return <AccountContext.Provider value={{ profile, roles, loading: resource.loading && !saved,
        error: saved ? "" : resource.error, completeProfile, refreshProfile }}>{children}</AccountContext.Provider>;
}
