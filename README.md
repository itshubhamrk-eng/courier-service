# courier-service

Courier Management backend: Java 21, Spring Boot 3, MySQL 8, Redis. Split from `courier-management-backend` (the `backend/` folder), history kept.

- Build and test: `mvn test` (unit), `mvn verify` (needs Docker for the integration tests).
- Run in a container: `Dockerfile` (the `Release` workflow publishes `ghcr.io/<owner>/courier-service`).
- Pipeline: `CI` (unit tests and an image build) on every pull request; `Release` on every merge to `main`: tests, publishes the
  image (tags: commit SHA and `dev`), deploys it to the dev server, smoke-tests it, and the server rolls back by itself if the
  container does not become healthy. Roll back by hand with *Run workflow* on `Release` and a full commit SHA. One-time setup
  (deploy key, secrets, variables): OCCPL repository, `infra/vps/RUNBOOK.md`, section 9, step 14.
- The web app lives in [`courier-web`](https://github.com/itshubhamrk-eng/courier-web); its nginx forwards `/api/` to a host named `backend`.
- Project notes (architecture, modules, changelog): `MEMORY/`.

## OCCPL platform integration (off by default)
Set `OCCPL_PLATFORM_ENABLED=true` to let the OCCPL SaaS platform provision companies (`/provisioning/v1/**`) and sign people in
(`/api/v1/auth/platform/**`). Settings and flow: `MEMORY/modules/platform-integration.md`.
