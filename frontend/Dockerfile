FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Public API URL so the SPA works even if nginx→backend private DNS fails.
ARG VITE_API_BASE_URL=https://backend-production-df5b.up.railway.app/api/v1
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine
RUN apk add --no-cache gettext
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV PORT=8080
ENV BACKEND_URL=https://backend-production-df5b.up.railway.app
ENV BACKEND_HOST=backend-production-df5b.up.railway.app
CMD ["sh", "-c", "envsubst '${PORT} ${BACKEND_URL} ${BACKEND_HOST}' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -t && nginx -g 'daemon off;'"]
