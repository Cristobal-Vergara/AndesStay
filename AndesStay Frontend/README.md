# Frontend AndesStay

Consulta el [README principal](../README.md) para levantar el entorno completo y la configuración de Microsoft Entra.

Para desarrollo con Node 24 LTS:

```powershell
..\scripts\setup-local.cmd
npm ci
npm run dev
```

Vite utiliza localhost:5173 y reenvía `/api` a un BFF iniciado fuera de Docker en localhost:8090. Usa este modo cuando desarrolles los servicios de forma nativa y el puerto 5173 esté libre. Para utilizar un BFF accesible en otra dirección, configura `VITE_PROXY_TARGET`. Si el frontend se conecta a un origen distinto sin proxy, configura `VITE_API_BASE_URL` y autoriza ese origen en CORS del BFF.

Con el Compose principal, Nginx realiza el proxy y no hace falta iniciar Vite.

`npm test` verifica llamadas HTTP y flujos de páginas. `npm run lint` ejecuta ESLint y `npm run build` genera la versión de producción. `.env.local` contiene únicamente configuración pública de Entra y la zona horaria; está excluido de Git.
