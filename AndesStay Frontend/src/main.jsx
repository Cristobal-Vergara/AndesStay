import React from "react";
import ReactDOM from "react-dom/client";
import { PublicClientApplication } from "@azure/msal-browser";
import { MsalProvider } from "@azure/msal-react";
import App from "./App";
import { msalConfig, authConfigurationError } from "./authConfig";
import "./index.css";

const root = ReactDOM.createRoot(document.getElementById("root"));
async function startApp() {
    if (authConfigurationError) {
        root.render(<main className="configuration-page"><h1>AndesStay</h1><p role="alert">{authConfigurationError}</p></main>);
        return;
    }
    try {
        const instance = new PublicClientApplication(msalConfig);
        await instance.initialize();
        const result = await instance.handleRedirectPromise();
        instance.setActiveAccount(result?.account || instance.getActiveAccount() || instance.getAllAccounts()[0] || null);
        root.render(<React.StrictMode><MsalProvider instance={instance}><App /></MsalProvider></React.StrictMode>);
    } catch {
        root.render(<main className="configuration-page"><h1>AndesStay</h1><p role="alert">No se pudo iniciar la sesión de Microsoft. Revisa la configuración y vuelve a cargar.</p></main>);
    }
}
startApp();
