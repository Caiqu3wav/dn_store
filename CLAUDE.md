# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

DN Store (Desafio Natureza) is an e-commerce site for mountain-bike apparel plus event listings. It is a monorepo with two independent apps and no root build:

- `backendJava/` — Spring Boot 3 / Java 21 REST API (MySQL, Flyway, JWT). Base path `/api`, port 8080.
- `frontend/` — Next.js 16 (App Router) / React 19 / Tailwind 4 + styled-components. Port 3000.
- `docs/` — Portuguese-language docs (API reference, DB schema, Melhor Envio production checklist). `docs/DOCUMENTACAO_BACKEND.md` and `dn_store_schema.sql` can lag behind the Flyway migrations; treat migrations and JPA entities as the source of truth.

UI text, domain terms and most docs are in Portuguese (`produtos`, `carrinho`, `pedidos`, `eventos`, `conta`, `favoritos`). Frontend routes follow these names.

## Commands

Backend (run from `backendJava/`; needs a `.env` — copy `.env.example`; `application.properties` imports it via `spring.config.import`, so run from that directory):

```bash
./mvnw spring-boot:run                 # run API (.\mvnw.cmd on Windows)
./mvnw test                            # unit/slice tests
./mvnw test -Dtest=PaymentServiceTest                  # one class
./mvnw test -Dtest=PaymentServiceTest#methodName       # one method
./mvnw test -Dtest=PaymentSchemaMySqlIT                # MySQL integration test (see below)
docker compose up --build              # containerised backend, reads .env
```

Frontend (run from `frontend/`):

```bash
npm run dev      # next dev
npm run build
npm run lint     # eslint (no test runner is configured)
```

`PaymentSchemaMySqlIT` is not picked up by plain `./mvnw test` (no failsafe plugin). It uses `src/test/resources/application-integration-local.properties`, which expects a local MySQL at `127.0.0.1:33316`, db/user `dn_store_it`, and runs the real Flyway migrations against it. Other tests use mocks/slices and need no database.

The frontend reads `NEXT_PUBLIC_API_URL`; in development it falls back to `http://localhost:8080/api`, in production it is required (see `frontend/lib/axios.ts`).

## Backend architecture

Package root `com.dnstore.backend`, layered: `controller` → `service` → `repository` (Spring Data JPA) → `model`. Hibernate runs with `ddl-auto=validate`, so **every schema change needs a new Flyway migration** in `src/main/resources/db/migration/` (numbering has a gap: there is no V19) and the entity must match exactly — `JpaModelConsistencyTest` and the MySQL IT guard this. UUIDs are stored as `VARCHAR`.

Things that span multiple files:

- **Auth**: stateless JWT (`JwtAuthenticationFilter`, `JwtService`) with email-based challenge/MFA and password-reset flows under `/api/auth/**` (Resend for email). Authorization rules live centrally in `config/SecurityConfiguration.java`; `/api/admin/**` requires `ROLE_ADMIN`. New public endpoints must be added there explicitly. Auth failures are shaped by `security/ApiSecurityExceptionHandler`; other errors by `exception/GlobalExceptionHandler`.
- **Pluggable gateways**: payments and shipping are interfaces (`PaymentGateway`, `ShippingGateway`) selected by properties. `shipping.gateway` = `simulated` (default) | `melhorEnvio` | unavailable; the Melhor Envio beans, OAuth controller and token store are `@ConditionalOnProperty`. Melhor Envio uses OAuth with tokens encrypted at rest (`TokenCipher`, `MELHOR_ENVIO_TOKEN_ENCRYPTION_KEY`) and fails closed when misconfigured. Payments use Asaas (`AsaasGatewayService`).
- **Payments are a state machine, not a flag**: `PaymentStatus` transitions are governed by `PaymentTransitionPolicy` (CREATING → PENDING → PAID/FAILED/OVERDUE → refund states, plus `RECONCILIATION_REQUIRED` when gateway creation outcome is uncertain — `PaymentCreationUncertainException`). State changes go through `PaymentAttemptStateService`; don't set status on entities directly. Financial history/blocks/refunds are separate tables (`PaymentFinancialHistory`, `FinancialBlock`, `PaymentRefund`).
- **Webhooks are durable and async**: `POST /api/payment/webhook` authenticates via `ASAAS_WEBHOOK_TOKEN`, `PaymentWebhookIngestionService` persists a `PaymentWebhookEvent` (idempotent by event id), and `PaymentWebhookWorker` (`@Scheduled`) leases and processes events with retries (`payment.webhook.*` settings) in `PaymentWebhookTransactionService`.
- **Inventory/orders**: stock lives on product variants/sizes; `OrderInventoryService` reserves and releases it using locking repository queries (`InventoryLockingRepositoryTest`). `PaymentExpirationScheduler/Service` expires unpaid orders after `payment.order-expiration-minutes` and releases stock. Orders persist a shipping quote snapshot (`ShippingQuoteOptionEntity`) so checkout uses the quoted price; `ShippingDataCleanupScheduler` deletes expired OAuth states and unconsumed quotes.
- **Catalog data**: product/catalog import and cleanup scripts are in `src/main/resources/db/catalog/` (manual SQL, not Flyway). `DatabaseSeeder` seeds initial data at startup. `ProductDefaultRule`/`ProductDefaultsService` supply admin-configurable defaults for new products.

Production DB is Aiven MySQL over TLS (`ssl-mode=REQUIRED`); `DB_URL`, `DB_USER`, `DB_PASS` are separate variables.

## Frontend architecture

App Router under `frontend/app/`, with admin pages under `app/admin/*` (guarded client-side and by the backend). Server-state conventions:

- `lib/axios.ts` is the single API client: attaches the `auth_token` cookie as a Bearer token, and on 401 clears the cookie and redirects to `/auth?next=...` for protected prefixes (`/checkout`, `/conta`, `/admin`). Add new protected route prefixes to `authenticatedRoutes` there.
- Global state is React context in `app/context/` (`AuthContext`, `CartContext`, `FavoritesContext`), mounted via `app/components/ClientLayoutWrapper.tsx`. Data fetching goes through axios (`hooks/useApi.ts` is a small custom hook, not SWR).
- Styling mixes Tailwind and styled-components (SSR registry in `app/StyledComponentsRegistry.tsx`).
- Checkout is a multi-step flow in `app/checkout/` (delivery → payment → confirmation) driven by the backend shipping quote and payment APIs.
- Remote images are limited to hosts allow-listed in `next.config.ts`.

## Repo hygiene notes

- `contas_teste` at the repo root is a tracked file containing test account credentials; don't copy its contents into code or docs, and consider removing it from git and rotating those credentials.
- `.env` files are gitignored; only `backendJava/.env.example` is tracked.

## Token and Context Optimization

- Avoid reading the entire codebase unless explicitly requested.
- Inspect only files relevant to the current task.
- Do not repeatedly read files already inspected during the current session unless they have changed.
- Prefer targeted searches over dumping entire files or directories.
- Keep command outputs concise. Use filters and limits when inspecting logs.
- Do not repeat large code blocks or explanations unnecessarily.
- Use specialized Skills only when relevant to the current task.
- Delegate independent investigations to subagents when beneficial.
- Before implementing large changes, create a concise plan.
- Never sacrifice code quality, security, or test coverage merely to reduce token consumption.
