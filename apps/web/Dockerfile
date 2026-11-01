# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim@sha256:c86ecead9e55a5210ac1727de38a4ca840bf03dcaf34dc9749addbf383b1920c AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json package-lock.json ./
COPY apps/web/package.json ./apps/web/package.json
COPY scripts/patch-redocly-minimatch.cjs ./scripts/patch-redocly-minimatch.cjs
RUN --mount=type=cache,target=/root/.npm npm ci
COPY apps/web ./apps/web
COPY contracts ./contracts
# Next rewrites are compiled at build time; rebuild to change this destination.
ARG API_URL=http://backend:8080
ENV API_URL=$API_URL
RUN npm run build && mkdir -p apps/web/public

FROM node:24-bookworm-slim@sha256:c86ecead9e55a5210ac1727de38a4ca840bf03dcaf34dc9749addbf383b1920c
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 HOSTNAME=0.0.0.0 PORT=3000
COPY --from=build --chown=node:node /app/apps/web/.next/standalone ./
COPY --from=build --chown=node:node /app/apps/web/.next/static ./apps/web/.next/static
COPY --from=build --chown=node:node /app/apps/web/public ./apps/web/public
RUN chown node:node /tmp && chmod 1777 /tmp
VOLUME ["/tmp"]
USER node
EXPOSE 3000
CMD ["node", "apps/web/server.js"]
