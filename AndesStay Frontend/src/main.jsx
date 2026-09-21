import React from "react";
import ReactDOM from "react-dom/client";
import { PublicClientApplication } from "@azure/msal-browser";
import { MsalProvider } from "@azure/msal-react";

import App from "./App";
import { msalConfig } from "./authConfig";

const msalInstance = new PublicClientApplication(msalConfig);

async function startApp() {

    await msalInstance.initialize();

    await msalInstance.handleRedirectPromise();

    ReactDOM.createRoot(document.getElementById("root")).render(
        <React.StrictMode>
            <MsalProvider instance={msalInstance}>
                <App />
            </MsalProvider>
        </React.StrictMode>
    );
}

startApp();
