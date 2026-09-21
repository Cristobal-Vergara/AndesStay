export const msalConfig = {
    auth: {
        clientId: "3e5c6423-0b9e-4125-b4ad-1b916c5c2f64",
        authority: "https://login.microsoftonline.com/2fec6e6e-3e74-4c31-82ae-8cc3d1974636",
        redirectUri: "http://localhost:5173"
    },
    cache: {
        cacheLocation: "localStorage",
        storeAuthStateInCookie: false
    }
};

export const loginRequest = {
    scopes: [
        "api://3e5c6423-0b9e-4125-b4ad-1b916c5c2f64/access_as_user"
    ]
};
