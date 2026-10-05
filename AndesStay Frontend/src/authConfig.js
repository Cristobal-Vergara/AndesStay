const tenantId = import.meta.env.VITE_ENTRA_TENANT_ID;
const clientId = import.meta.env.VITE_ENTRA_CLIENT_ID;
const scope = import.meta.env.VITE_ENTRA_SCOPE;
export const authConfigurationError = !tenantId || !clientId || !scope
    ? "Falta configurar Microsoft Entra. Ejecuta scripts/setup-local.cmd y reinicia el frontend." : "";
export const msalConfig = {
    auth: {
        clientId,
        authority: `https://login.microsoftonline.com/${tenantId}`,
        redirectUri: import.meta.env.VITE_ENTRA_REDIRECT_URI || window.location.origin,
        postLogoutRedirectUri: import.meta.env.VITE_ENTRA_REDIRECT_URI || window.location.origin,
    },
    cache: { cacheLocation: "sessionStorage", storeAuthStateInCookie: false },
};
export const loginRequest = { scopes: scope ? [scope] : [] };
