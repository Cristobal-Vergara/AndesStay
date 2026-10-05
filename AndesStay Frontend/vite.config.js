import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import process from "node:process";
export default defineConfig({
    plugins: [react()],
    build: { rolldownOptions: { output: {
        strictExecutionOrder: true,
        codeSplitting: { groups: [
            { name: "react-vendor", test: /node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler)[\\/]/, priority: 20 },
            { name: "microsoft-auth", test: /node_modules[\\/]@azure[\\/]/, priority: 10 },
        ] },
    } } },
    server: {
        port: 5173, strictPort: true,
        proxy: { "/api": { target: process.env.VITE_PROXY_TARGET || "http://localhost:8090", changeOrigin: true } },
    },
});
