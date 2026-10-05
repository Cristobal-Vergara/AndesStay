FROM node:24-bookworm-slim AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY . .
ARG VITE_ENTRA_TENANT_ID
ARG VITE_ENTRA_CLIENT_ID
ARG VITE_ENTRA_SCOPE
ARG VITE_APP_TIME_ZONE=America/Santiago
ENV VITE_ENTRA_TENANT_ID=$VITE_ENTRA_TENANT_ID VITE_ENTRA_CLIENT_ID=$VITE_ENTRA_CLIENT_ID VITE_ENTRA_SCOPE=$VITE_ENTRA_SCOPE VITE_APP_TIME_ZONE=$VITE_APP_TIME_ZONE
RUN npm run build
FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
