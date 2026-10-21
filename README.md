# courier-service

Courier Management backend: Java 21, Spring Boot 3, MySQL 8, Redis. Split from `courier-management-backend` (the `backend/` folder), history kept.

- Build and test: `mvn test` (unit), `mvn verify` (needs Docker for the integration tests).
- Run in a container: `Dockerfile` (the `Image` workflow publishes `ghcr.io/<owner>/courier-service`).
- The web app lives in [`courier-web`](https://github.com/occplsoftech/courier-web); its nginx forwards `/api/` to a host named `backend`.
- Project notes (architecture, modules, changelog): `MEMORY/`.

## OCCPL platform integration (off by default)
Set `OCCPL_PLATFORM_ENABLED=true` to let the OCCPL SaaS platform provision companies (`/provisioning/v1/**`) and sign people in
(`/api/v1/auth/platform/**`). Settings and flow: `MEMORY/modules/platform-integration.md`.
