# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working in this repository.

## Repository purpose and architecture

OctaPoint is a multi-tenant Credit-as-a-Service project. The intended MVP uses PostgreSQL as the authoritative store for balances and ledger entries; Redis for HMAC nonce/replay protection; Spring Boot Core for tenant and credit APIs; a separate TypeScript Signer for Sui transactions; and a Next.js Merchant/Admin portal. Settlement is intended to be asynchronous through a transactional outbox and worker.

Treat the architecture and requirements as target design, not proof of implementation. Current source still has mock credit endpoints, incomplete ledger logic, an HMAC filter using a test secret, and a Signer/Move issue spike without outbox-worker integration or redeem. Check code and tests before describing a capability as complete.

Big-picture boundaries:
- `backend/` is a Java 25 / Spring Boot 3.5.16 modular monolith. Packages such as `ledger`, `tenant`, `apikey`, `security`, and `chain` separate domain responsibilities. `ChainClient` is the Core-facing blockchain abstraction; the Signer owns key access and transaction submission. `MockChainClient` currently throws for its operations rather than simulating success.
- `backend/src/main/resources/db/migration/` contains Flyway PostgreSQL migrations. Keep applied migration files immutable; add a new versioned migration for schema changes. Default/local Spring config currently uses H2 while Compose's `core` profile uses PostgreSQL, so validate behavior against the intended database.
- `signer/` is a separate Node.js/TypeScript service. It exposes internal HTTP endpoints and calls Sui; it is not yet wired to an outbox worker in Compose.
- `contracts/octapoint_move/` contains the Sui Move package. The current contract is a limited issue/balance prototype, not the full target account/redeem/sequence design.
- `frontend/` is a Next.js 16.4.2 / React 19 TypeScript app using the App Router, Tailwind 4 and Zustand. It is run separately; Compose does not start it.
- `docs/` contains URD/SRS/requirements, architecture, ADRs, API and setup evidence. ADR-0003 is the target HMAC contract; it explicitly documents incompatibility with the current filter. Resolve contract conflicts before changing clients/server together.

## Commands

Run commands from the relevant project directory unless stated otherwise.

### Backend (`backend/`)

```sh
mvn --batch-mode verify
mvn test
mvn -Dtest=MockChainClientTest test
mvn -Dtest=ArchitectureTest test
mvn spring-boot:run
```

`mvn verify` is the CI-equivalent backend build/test command. Maven generates Spring interfaces/models from `src/main/resources/openapi.yaml` using the configured OpenAPI Generator plugin; check generated-source behavior before editing generated files.

### Signer (`signer/`)

```sh
npm ci
npm test
npm run build
npm run dev
npm start
```

`npm test` is TypeScript type-checking (`tsc --noEmit`), not a behavioral test suite.

### Frontend (`frontend/`)

```sh
npm ci
npm run dev
npm run lint
npm run build
npm start
```

Before making frontend code changes, read the applicable guide under `frontend/node_modules/next/dist/docs/`. Next.js 16 has repo-specific generated guidance in `frontend/AGENTS.md`; heed it and preserve its generated block.

### Compose and Move

From repository root, Compose requires `.env` values including `POSTGRES_PASSWORD`, `API_KEY_MASTER_KEY`, and `API_KEY_ADMIN_TOKEN`:

```sh
docker compose up --build
```

Compose starts PostgreSQL, Redis, Sui CLI and Core; it does not start the frontend, Signer or a settlement worker. For Move package validation, use the Sui CLI environment described in `docs/7.Setup/sui-setup.md`, then run from `contracts/octapoint_move/`:

```sh
sui move build
sui move test
```

## Team workflow

The repository README specifies `main` and `develop` as protected integration branches; work on `feature/SCRUM-<number>-<short-description>` branches. Commit messages use `SCRUM-<number>: <change>`. Pull requests target `develop` and use the SCRUM key in the title. Do not push directly to `main` or `develop`.

## Requirements and security contract

- PostgreSQL is the business source of truth; Sui settlement is asynchronous and must not be represented as settled until verified.
- For Merchant API HMAC, use the accepted contract in `docs/adr/0003-hmac-api-authentication.md`: `X-OP-Key`, `X-OP-Timestamp`, `X-OP-Nonce`, `X-OP-Signature`; sign method, path, canonical query, timestamp, nonce and SHA-256 body hash separated by newlines. Validate the contract against current implementation before integrating. Store nonce only after signature validation; Redis failure must fail closed.
- Idempotency keys and HMAC nonces serve different purposes. Keep tenant scoping in server-side credential context, not client-supplied tenant IDs.
- Do not infer implementation completeness from SRS, architecture, ADRs, mock UI, or historical Signer reports. Verify source/tests/runtime evidence.

## Guidance sources

- `README.md` contains branch, commit and PR conventions.
- `frontend/AGENTS.md` contains the generated Next.js instruction to read local Next docs before frontend changes.
- `.github/workflows/ci.yml` defines CI: backend `mvn --batch-mode verify`, Signer `npm ci`/`npm test`/`npm run build`, and Docker Compose build.
- No repository-root `.cursorrules`, `.cursor/rules/`, `.github/copilot-instructions.md`, `GEMINI.md`, or `.codex/` config was found during initialization.
