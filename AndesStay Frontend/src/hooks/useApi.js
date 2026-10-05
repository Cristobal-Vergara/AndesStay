import { useCallback } from "react";
import { useMsal } from "@azure/msal-react";
import { loginRequest } from "../authConfig";
import { fetchJson } from "../api";
import { useAccount } from "../auth/AccountContext";

export function useApi() {
    const { instance, accounts } = useMsal();
    const account = accounts[0];
    const session = useAccount();
    const request = useCallback(async (path, options = {}) => {
        if (!account) throw new Error("Inicia sesión para continuar.");
        let response;
        try {
            response = await instance.acquireTokenSilent({ ...loginRequest, account });
        } catch {
            throw new Error("Tu sesión necesita renovarse. Vuelve a iniciar sesión.");
        }
        return fetchJson(path, response.accessToken, options);
    }, [instance, account]);
    return { request, account, roles: session?.roles || account?.idTokenClaims?.roles || [] };
}
