# E-Commerce Platform

A full-stack e-commerce application built with **Java 21 / Spring Boot 3.5** on the backend and **React 19 (Vite)** on the frontend. Both the backend REST API and the frontend are deployed on **Render**.

The project is built as a backend-engineering showcase rather than a plain CRUD store: the checkout path is transactional, stock updates use optimistic locking, checkout requests are idempotent, refresh tokens are revocable server-side, and every endpoint group has its own rate limit.

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [System Architecture](#system-architecture)
- [Database Design](#database-design)
- [Authentication Flow](#authentication-flow)
- [Checkout Flow](#checkout-flow)
- [Product Search](#product-search)
- [API Documentation](#api-documentation)
- [Testing](#testing)
- [Tech Stack](#tech-stack)
- [Deployment](#deployment)
- [Engineering Highlights](#engineering-highlights)
- [Project Structure](#project-structure)
- [Local Development](#local-development)
- [Future Improvements](#future-improvements)

---

## Overview

The system models a single-vendor online store with two roles:

- **USER** — registers/logs in, browses products, manages a cart, checks out (COD or MoMo), views their own orders and payments, and can retry payment on an unpaid MoMo order.
- **ADMIN** — manages products/categories and updates order status.

Core flow: browse products → add to cart → checkout → an `Order` + `Payment` are created. For **COD**, stock is deducted and the cart cleared immediately. For **MoMo**, the order is left `AWAITING_PAYMENT` and stock/cart are untouched until MoMo's asynchronous IPN callback confirms payment; unpaid MoMo orders are auto-cancelled by a scheduled job after 15 minutes.

## Key Features

### Authentication & Security
- Register/login with BCrypt-hashed passwords, stateless JWT access tokens (default 15 min).
- Refresh tokens (7 days) are **hashed with SHA-256 and stored server-side**, not just signed JWTs — this enables real revocation, not just expiry.
- Refresh-token **rotation on every use**: the old token is revoked and a new one issued.
- **Reuse detection**: if a revoked refresh token is presented again, all of that user's tokens are revoked immediately (`RefreshTokenService.verifyNotRevoked`).
- Refresh token delivered as an `HttpOnly`, `Secure` (prod), `SameSite` cookie scoped to `/api/v1/auth/refresh` — never exposed to JS.
- A daily scheduled job (`RefreshTokenCleanupScheduler`, 2 AM) purges expired/revoked tokens from the database.
- Role-based authorization (`USER`, `ADMIN`) enforced declaratively in `SecurityConfig`.
- Per-endpoint-group **rate limiting** via Bucket4j token buckets (Caffeine-backed, in-memory): stricter limits on `/auth/login` (5/min) and `/auth/register` (3/min) than on public reads (100/min); authenticated groups (cart, checkout, order reads) are keyed by user email instead of IP.

### Product & Inventory
- Products belong to a `Category`; each product has one or more **`ProductVariant`** rows (size + stock), not a single flat stock count.
- Paginated, sortable product listing, filterable by `categoryId`.
- `GET` product-by-id and category-list responses are cached with Caffeine (30 min TTL), evicted on any write.
- Stock is enforced with **optimistic locking** (`@Version` on `Product` and `ProductVariant`) — verified by a concurrency test that races two checkouts against the last unit of stock.

### Cart
- One cart per user, created lazily. Cart lines reference a specific `ProductVariant` (product + size), unique per cart.
- Add-to-cart merges quantity into an existing line and re-validates against live stock; quantity update and item removal are also supported.

### Checkout
- Idempotent by design: the client sends an `Idempotency-Key` header (enforced by a custom `@Idempotent` annotation + `IdempotencyInterceptor`), and the key is inserted into a unique-constrained `idempotency_keys` table inside the same transaction — a duplicate key is rejected as a conflicting/duplicate request before any order is built.
- Stock is re-validated per cart line at checkout time, in the same DB transaction that will (for COD) deduct it.
- Payment-method-specific behavior (order status, stock deduction, cart clearing, external gateway call) is delegated to a **Strategy** implementation (`CodPaymentStrategy` / `MomoPaymentStrategy`) selected by a factory — `CheckoutService` itself has no payment-specific branching.
- A daily scheduled job (`IdempotencyKeyCleanupScheduler`) deletes idempotency keys older than 24 hours.

### Orders
- Orders snapshot product name/price/size at the time of purchase (`OrderItem`), so later price changes don't affect historical orders.
- Paginated order history per user; admin-only endpoint to transition order status.
- `retry-payment` endpoint lets a user regenerate a MoMo payment URL for an order still `AWAITING_PAYMENT`.

### Payment
- Two methods behind a common `PaymentStrategy` interface: **COD** and **MoMo**.
- **MoMo runs in mock mode** (`payment.momo.mock-enabled: true` by default): checkout redirects to a local `/mock-payment` endpoint (`MockPaymentController` / `MockPaymentService`) instead of calling MoMo's real API. No live MoMo sandbox account was used — the project has not been exercised against MoMo's actual servers.
- The MoMo client code itself (`MomoServiceImpl`) implements the real request/response contract from MoMo's docs — building the payment request payload and computing/verifying the HMAC-SHA256 signature exactly as MoMo's API requires — so the integration is "spec-correct" and would call the real `MOMO_API_URL` if `mock-enabled` were switched off and real credentials supplied. But as shipped and actually run, all payment flows in this project go through the mock endpoint, not MoMo's servers.
- **Late/duplicate IPN handling** (`MomoIpnService`): if a success callback arrives *after* the order was already auto-cancelled by the timeout scheduler, the service detects the conflict and triggers an automatic refund flow (`PaymentRefundService`) instead of silently double-processing or losing the payment. This logic is exercised by unit tests against the mocked IPN payload — it has not been validated against a real MoMo IPN callback.
- The outbound MoMo refund HTTP call is a stub (logs only) — the refund *state machine* (`PENDING → REFUNDING → REFUNDED`) and triggering logic are fully implemented, but no real refund request is ever sent to MoMo.

### Reliability & Concurrency
- Optimistic locking (`@Version`) on `Product`, `ProductVariant`, `Order`, and `Payment`.
- Side-effect transactions (order-timeout cancellation, refund processing) run with `Propagation.REQUIRES_NEW` so they don't get rolled back by, or entangled with, the caller's transaction.
- Two scheduled jobs: `PaymentTimeoutScheduler` (every 5 min, cancels MoMo orders unpaid for 15+ min) and cleanup schedulers for stale refresh tokens and idempotency keys.

## System Architecture

```
Controller
    │  (REST, DTO in/out, delegates all logic)
    ▼
Service
    │  (@Transactional business rules, orchestration)
    ▼
Repository (Spring Data JPA)
    ▼
PostgreSQL
```

Supporting components sitting around this core:

| Component | Role |
|---|---|
| `RateLimitFilter` | Runs first in the filter chain; Bucket4j token-bucket check per endpoint group before any JWT parsing |
| `JwtAuthenticationFilter` | Extracts/validates the JWT and populates `SecurityContext` |
| `IdempotencyInterceptor` | `HandlerInterceptor` that rejects requests missing `Idempotency-Key` on `@Idempotent` endpoints |
| `GlobalExceptionHandler` | Centralized `@RestControllerAdvice` mapping domain exceptions to HTTP status + JSON error body |
| `PaymentStrategy` (Strategy + Factory) | Isolates COD vs MoMo checkout logic behind one interface |
| `CacheConfig` (Caffeine) | Read-through caching for product/category lookups |
| Schedulers | Background jobs for payment timeout, token cleanup, idempotency-key cleanup |

Design patterns actually present in the code: **Strategy** (payment methods), **Factory** (`PaymentStrategyFactory`), **DTO + Mapper** (entities never leave the service layer), **Interceptor** (idempotency), and constructor-based **Dependency Injection** throughout.

## Database Design

Key entities and relationships:

| Entity | Relationship | Notes |
|---|---|---|
| `User` | 1—1 `Cart`, 1—N `Order` | role via `Role` enum (`USER`/`ADMIN`) |
| `Category` | 1—N `Product` | unique name |
| `Product` | 1—N `ProductVariant` | `@Version` for optimistic locking |
| `ProductVariant` | N—1 `Product` | size + stock, unique on `(product_id, size_value)`, own `@Version` |
| `Cart` | 1—N `CartItem` | one cart per user |
| `CartItem` | N—1 `ProductVariant`, N—1 `Cart` | unique on `(cart_id, product_variant_id)` |
| `Order` | N—1 `User`, 1—N `OrderItem`, 1—1 `Payment` | `@Version`, snapshots shipping info |
| `OrderItem` | N—1 `Order`, N—1 `Product` | snapshots `size` and `price` at purchase time |
| `Payment` | 1—1 `Order` | `@Version`, stores MoMo `transactionId`/`momoOrderId`/`momoRequestId` |
| `RefreshToken` | N—1 `User` | stores SHA-256 **hash** of the token, plus `revoked` flag and expiry |
| `IdempotencyKey` | standalone | primary key is the client-supplied key string |

Schema is version-controlled with **Flyway** migrations (`V1__init_schema.sql`, `V2__remove_product_id_from_cart_items.sql`, `V3__product_search.sql`); `ddl-auto` is `update` in dev and `validate` in production, so the running schema is always checked against the migration history rather than silently auto-generated in prod.

## Authentication Flow

```
Register/Login
    → Access Token (JWT, 15 min, returned in response body)
    → Refresh Token (JWT, 7 days, HttpOnly cookie, hash stored in DB)
    → /auth/refresh → verify not revoked → rotate (revoke old, issue new)
    → /auth/logout → revoke current refresh token, clear cookie
```

If a refresh token that's already been revoked is presented again (token-reuse signal), **all** of that user's refresh tokens are revoked server-side and the request is rejected — this is a real defense against stolen-refresh-token replay, not just token expiry.

## Checkout Flow

```
POST /api/v1/checkout   (header: Idempotency-Key)
    │
    ▼
1. Insert Idempotency-Key into DB (unique constraint) — duplicate ⇒ reject
2. Load user's cart, reject if empty
3. Per cart line: re-check ProductVariant.stock ≥ requested quantity
4. Build Order + OrderItem (snapshot price & size), sum total
5. Delegate to PaymentStrategy:
     COD  → status=PENDING, create Payment, deduct stock per variant, clear matching cart items
     MoMo → status=AWAITING_PAYMENT, create Payment(PENDING), call/mock MoMo, DO NOT touch stock/cart
6. Return CheckoutResponse (orderId, status, total, paymentUrl for MoMo)
```

Everything runs inside a single `@Transactional` method in `CheckoutService`; stock checks, order creation, and (for COD) stock deduction happen atomically. Concurrent checkouts against the same limited-stock variant are resolved by optimistic locking — verified with a dedicated `OrderConcurrencyTest` that fires two simultaneous HTTP checkouts at the last unit of stock and asserts exactly one succeeds.

## Product Search

The product search feature provides accent-insensitive, case-insensitive substring search with relevance ranking directly inside PostgreSQL:

- **How it works:**
  - **Database schema (Flyway V3):** Adds a generated column `search_name` (`GENERATED ALWAYS AS (lower(immutable_unaccent(name))) STORED`) with a GIN trigram index (`gin (search_name gin_trgm_ops)`) and an index on `category_id`.
  - **Keyword normalization:** `SearchTextUtils.normalize` strips Vietnamese diacritics (`NFD` normalization + `\p{M}+` removal, maps `đ` -> `d`), converts to lowercase, trims and collapses multiple spaces.
  - **Multi-word AND matching:** Splits keywords into up to 5 tokens, safely escapes LIKE wildcards (`\`, `%`, `_`), and matches all tokens with `AND`.
  - **3-tier relevance ranking:** Handled in JPA Criteria:
    1. Exact match (`rank = 3`)
    2. Prefix match (`rank = 2`)
    3. Substring match (`rank = 1`)
    - Same-tier results are sorted by `id ASC`.
  - **Database pagination:** Uses `@EntityGraph(attributePaths = {"category"})` with `@BatchSize(50)` on variants so pagination executes directly in SQL (`OFFSET ? ROWS FETCH FIRST ? ROWS ONLY`) without memory warnings.
- **Why PostgreSQL trigrams instead of Elasticsearch or Full-Text Search (`tsvector`):**
  - Keeps operational complexity low: avoids managing an external cluster, ingestion pipelines, or sync state for a catalog of this scale.
  - Standard PostgreSQL Full-Text Search (`tsvector`) does not support substring matching or unaccented Vietnamese search as naturally or flexibly as `pg_trgm` + `unaccent`.
- **Known Limitations:**
  - No typo tolerance (fuzzy matching on misspellings is omitted to avoid poor precision on short Vietnamese words).
  - Searches by product name only (descriptions are not indexed).
  - Maximum 5 tokens per query; keyword length capped at 100 characters (minimum 2 characters after normalization).
  - Trigram indexes are most effective on tokens with 3+ characters; shorter tokens still match correctly via database scans.
  - Guaranteed for Vietnamese and basic Latin character sets.
- **Prerequisites:**
  - Database user must have privileges to install `pg_trgm` and `unaccent` extensions into schema `public`.

## API Documentation

Interactive docs are available via springdoc-openapi at `/swagger-ui.html` and `/v3/api-docs` (both public).

| Group | Endpoints |
|---|---|
| **Auth** | `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` |
| **Products** | `GET /api/v1/products` (supports `q`, `categoryId`, `page`, `size`, `sortBy ∈ {id,name,price,relevance}`, `sortDir`), `GET /api/v1/products/{id}`, `POST/PUT/DELETE /api/v1/products/{id}` (ADMIN) |
| **Categories** | `GET /api/v1/categories`, `GET /api/v1/categories/{id}`, `POST/PUT/DELETE /api/v1/categories/{id}` (ADMIN) |
| **Cart** | `GET /api/v1/carts`, `POST /api/v1/carts/add`, `PUT /api/v1/carts/update`, `DELETE /api/v1/carts/{itemId}` |
| **Checkout** | `POST /api/v1/checkout` (requires `Idempotency-Key` header) |
| **Orders** | `GET /api/v1/orders` (paginated), `GET /api/v1/orders/{id}`, `PATCH /api/v1/orders/{id}/status` (ADMIN), `POST /api/v1/orders/{id}/retry-payment` |
| **Payments** | `POST /api/v1/payments/momo/ipn` (public, HMAC-verified), `GET /api/v1/payments/momo/return` (public), `GET /api/v1/payments/order/{orderId}` (owner-only) |
| **Mock Payment** | `POST /api/v1/mock-payments/{orderId}/simulate` — only active when `payment.momo.mock-enabled=true` |

## Testing

**213 `@Test` methods across 29 test classes** (verified directly from source), split into:

- **Unit tests** (Mockito) — services, JWT logic, both payment strategies, MoMo signature/IPN logic, refund logic, schedulers, and `SearchTextUtilsTest` (normalization, diacritics removal, LIKE wildcard escaping).
- **Integration tests** (`@SpringBootTest` + `MockMvc`) — `AuthControllerTest`, `ProductControllerTest` (search param mapping & validation), `ProductSearchIntegrationTest` (full-stack search against real PostgreSQL with Flyway V3 migration covering unaccent, case-insensitivity, ranking, wildcard escaping, category filtering, pagination, and schema verification), `CheckoutControllerTest`, `PaymentControllerTest`, `AdminEndpointSecurityTest`, `OrderTimeoutIntegrationTest`, `RateLimitFilterTest`.
- **Concurrency tests** — `ProductConcurrencyTest` and `OrderConcurrencyTest`, using `ExecutorService`/`CountDownLatch` to force two threads to race the same stock row and assert single-winner semantics.
- Tests run against Testcontainers (PostgreSQL 17.9) for isolated integration testing with Flyway migrations and schema validation.

No coverage percentage is claimed here since no coverage tool (e.g. Jacoco) is configured in `pom.xml`.

## Tech Stack

| Category | Technologies |
|---|---|
| **Backend** | Java 21, Spring Boot 3.5, Spring Web, Spring Security, Spring Data JPA / Hibernate |
| **Database** | PostgreSQL, Flyway migrations |
| **Auth** | JWT (`jjwt` 0.12.7) |
| **Caching / Rate Limiting** | Spring Cache + Caffeine, Bucket4j |
| **API Docs** | springdoc-openapi (Swagger UI) |
| **Utilities** | Lombok |
| **Frontend** | React 19, React Router 7, Vite 8, Axios, lucide-react (icons) |
| **Testing** | JUnit 5, Mockito, MockMvc, Spring Security Test, H2 (PG-compatibility mode), Testcontainers (PostgreSQL) |
| **Infrastructure** | Docker (multi-stage build, non-root runtime user, G1GC + container-aware memory flags), GitHub Actions CI (`mvn test`/`mvn package` for backend, `npm run build` for frontend), Render (backend API + frontend hosting) |

## Deployment

```
Frontend (React/Vite, Render)
        │  REST/JSON over HTTPS
        ▼
Backend API (Spring Boot, Render)
        │  JDBC
        ▼
PostgreSQL
```

- The backend `Dockerfile` is a two-stage build: Maven builds the JAR in a temurin-21 build image, then the JAR is copied into a minimal `eclipse-temurin:21-jre-alpine` runtime image running as a non-root user, with `JAVA_OPTS` tuned for constrained-memory containers (`-XX:MaxRAMPercentage=75.0`, G1GC).
- `application-prod.yaml` activates on `SPRING_PROFILES_ACTIVE=prod`: HikariCP pool tuning, `ddl-auto: validate` (schema changes only via Flyway), secure/`SameSite=None` refresh cookies, and reverse-proxy-aware IP resolution (`rate-limit.trust-proxy: true`) for correct rate-limit keys behind Render's proxy.
- `docker-compose.yml` in the repo provisions PostgreSQL only, for local development; it does not define a backend service. `.env.prod.example` documents the production environment variables (Supabase/managed Postgres URL, JWT secret, MoMo config keys) that are configured directly in Render's dashboard rather than committed to the repo; in this deployment `payment.momo.mock-enabled` is left `true`, so no real MoMo credentials are actually required or used.
- CI (`backendci.yml`, `frontendci.yml`) runs build/test verification on push and PR; it does not itself publish images or trigger deployment — deployment to Render is configured on Render's side against the repository.

## Engineering Highlights

- **Idempotent checkout** implemented at the database level (unique-constrained key table inside the transaction), not just an in-memory or cache-based check — safe across app restarts and multiple instances.
- **Refresh-token rotation with reuse detection**: tokens are hashed at rest, rotated on every refresh, and a replayed/revoked token triggers a full account-wide token revocation — a materially stronger model than "JWT refresh token, no DB check."
- **Payment reconciliation under race conditions**: the MoMo IPN handler explicitly reasons about the case where a successful payment notification arrives *after* the order was already auto-cancelled by the timeout job, and routes it into a refund flow instead of either double-fulfilling the order or losing the customer's money silently. This is implemented and unit-tested against simulated IPN payloads (the project runs against mocked MoMo, not a live sandbox).
- **Concurrency correctness is tested, not assumed**: optimistic-locking behavior under simultaneous checkouts is verified with real multi-threaded integration tests against the live HTTP endpoint, not just unit-level mocking.
- **Per-endpoint-group rate limiting** with different keys (IP for pre-auth endpoints, user identity for authenticated ones) and different budgets per group, implemented directly with Bucket4j + Caffeine rather than a single blanket filter.

## Project Structure

```
ecommerce-platform/
├── Dockerfile
├── docker-compose.yml
├── .env.example / .env.prod.example
├── .github/workflows/          # backendci.yml, frontendci.yml
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/ecommerce/
│       │   ├── annotation/     # @Idempotent
│       │   ├── config/         # Security, JWT filter, Cache, MoMo, Idempotency interceptor, WebMvc
│       │   ├── controller/     # REST controllers
│       │   ├── dto/            # request/ and response/ DTOs
│       │   ├── entity/         # JPA entities
│       │   ├── enums/          # OrderStatus, PaymentStatus, PaymentMethod, Role
│       │   ├── exception/      # Custom exceptions + GlobalExceptionHandler
│       │   ├── mapper/         # Entity <-> DTO mappers
│       │   ├── ratelimit/      # Bucket4j filter, bucket registry, IP resolver, config properties
│       │   ├── repository/     # Spring Data JPA repositories
│       │   ├── scheduler/      # Payment timeout, refresh-token cleanup, idempotency-key cleanup
│       │   └── service/        # Business logic (+ service/payment/ strategy classes)
│       ├── main/resources/     # application.yaml, application-prod.yaml, db/migration (Flyway)
│       └── test/java/com/ecommerce/
│           ├── unit/
│           └── integration/
└── frontend/                   # React 19 + Vite client
```

## Local Development

**Prerequisites**: Java 21, Maven (or the bundled `mvnw`), Docker (for PostgreSQL), Node 20 (for the frontend).

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Configure environment
cp .env.example .env   # fill in DB_PASSWORD, JWT_SECRET_KEY; MoMo keys are NOT required — mock-enabled=true by default

# 3. Run the backend (from backend/)
cd backend
./mvnw spring-boot:run
# API on http://localhost:8080

# 4. Run tests (H2 in-memory, no external DB needed)
./mvnw test

# 5. Run the frontend (from frontend/)
cd ../frontend
npm install
npm run dev
```

## Future Improvements

- Implement the real MoMo refund HTTP call (the state machine and trigger logic already exist).
- Add a code path to transition a COD `Payment` out of `PENDING` (e.g., on delivery confirmation).
- Add Jacoco (or similar) for a real test-coverage metric.
- Add a backend service definition to `docker-compose.yml` for a fully containerized local stack.
- Move rate-limit buckets to a shared store (e.g. `bucket4j-redis`) if the backend is ever scaled to multiple instances, since the current implementation is in-memory per instance.
