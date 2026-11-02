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
- [Promotion V1](#promotion-v1)
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

Core flow: browse products → add to cart → preview prices → checkout → an `Order` + `Payment` are created. For **COD**, stock is deducted and purchased cart lines cleared immediately. For **MoMo mock**, the order is left `AWAITING_PAYMENT` and stock/cart are untouched until mock SUCCESS; a scheduled job scans every five minutes for unpaid MoMo orders older than fifteen minutes. No real money is collected.

## Key Features

### Authentication & Security
- Register/login with BCrypt-hashed passwords, stateless JWT access tokens (default 15 min).
- Refresh tokens (7 days) are **hashed with SHA-256 and stored server-side**, not just signed JWTs — this enables real revocation, not just expiry.
- Refresh-token **rotation on every use**: the old token is revoked and a new one issued.
- Each refresh JWT has a random UUID `jti`, preventing duplicate hashes when tokens are issued within one second. Frontend session restoration and Axios retries share a single refresh Promise; delayed unauthorized responses reuse the refreshed session. Access-token claims and lifetimes are unchanged.
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
- The legacy MoMo client contains request/signature handling and can call an external endpoint if mock mode is disabled. That branch has not been verified against a real gateway and must not be treated as production-safe. Dev and demo prod explicitly keep `mock-enabled=true`.
- **Late/duplicate success handling**: fulfillment checks `CANCELLED` before changing payment or stock. Admin cancellation closes pending payment; late mock SUCCESS cannot resurrect the order. Legacy IPN behavior is regression-tested, but the real-gateway branch is not audited or certified for production.
- The outbound MoMo refund HTTP call is a stub (logs only) — the refund *state machine* (`PENDING → REFUNDING → REFUNDED`) and triggering logic are fully implemented, but no real refund request is ever sent to MoMo.

### Reliability & Concurrency
- Optimistic locking (`@Version`) on `Product`, `ProductVariant`, `Order`, and `Payment`.
- Timeout cancellation uses an independent transaction per order. Simulated refund processing uses `Propagation.REQUIRED`, so order/payment/voucher and refund state roll back together on a conflict. No refund HTTP request is sent.
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
| `IdempotencyKey` | links committed `Order` | primary key is the client-supplied key string; owner-checked result lookup |

Schema is version-controlled with **Flyway**: V1 initial schema, V2 variant-based cart, V3 product search, V4 promotion schema/snapshots, V5 checkout key → order link. Dev, prod and PostgreSQL tests use `ddl-auto=validate`. Never edit applied migrations, use `update` to bypass an error, or reset an existing database without explicit permission. V4 rejects abnormal historical amounts for manual review rather than silently rewriting them.

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
4. Recalculate promotions/shipping, compare expectedTotal; mismatch ⇒ 409 PRICE_CHANGED + breakdown
5. Build Order + OrderItem with immutable pricing snapshots
6. Delegate to PaymentStrategy:
     COD  → status=PENDING, create Payment, deduct stock per variant, clear matching cart items
     MoMo → status=AWAITING_PAYMENT, create Payment(PENDING), call/mock MoMo, DO NOT touch stock/cart
7. Reserve winning voucher after order has an ID; link idempotency key to order in this transaction
8. Return CheckoutResponse (orderId, status, total, local paymentUrl for MoMo mock)
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

Tests are split into the groups below. Promotion V1 adds pricing, migration, lifecycle, concurrency, browser and authentication regressions; executed counts and acceptance results are recorded in [Promotion V1 acceptance](docs/PROMOTION-V1-ACCEPTANCE.md).

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
- **Payment consistency under race conditions**: late success cannot revive cancelled orders; voucher release is idempotent and simulated refund state shares its caller's transaction. Optimistic conflicts roll back consistently. Tests use MoMo mock, not a live sandbox.
- **Concurrency correctness is tested, not assumed**: optimistic-locking behavior under simultaneous checkouts is verified with real multi-threaded integration tests against the live HTTP endpoint, not just unit-level mocking.
- **Per-endpoint-group rate limiting** with different keys (IP for pre-auth endpoints, user identity for authenticated ones) and different budgets per group, implemented directly with Bucket4j + Caffeine rather than a single blanket filter.

## Project Structure

The backend source code is organized **by feature module** (`package-by-feature`), grouping controllers, services, repositories, entities, DTOs, mappers, and enums belonging to the same business capability together. Unit and integration tests mirror this exact module layout.

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
│       │   ├── EcommerceBackendApplication.java
│       │   ├── auth/           # Authentication, JWT, tokens, security config & filters
│       │   ├── user/           # User management, admin user service, roles
│       │   ├── category/       # Category catalog management
│       │   ├── product/        # Products, variants, inventory, criteria search (unaccent/trigrams)
│       │   ├── cart/           # Shopping cart & line items
│       │   ├── order/          # Order placement, items, cancellation, order admin
│       │   ├── payment/        # Payment state machine, COD & MoMo strategies, IPN & refund, schedulers
│       │   ├── checkout/       # Idempotent checkout orchestration
│       │   ├── dashboard/      # Admin metrics & statistics aggregations
│       │   ├── common/         # Cross-cutting infrastructure: exceptions, rate limiting, idempotency, cache config
│       │   └── bootstrap/      # DatabaseSeeder (initial data population)
│       ├── main/resources/     # application.yaml, application-prod.yaml, db/migration (Flyway)
│       └── test/java/com/ecommerce/
│           ├── EcommerceBackendApplicationTests.java
│           └── {auth, user, category, product, cart, order, payment, checkout, dashboard, common}/
│               ├── service/    # Unit tests with Mockito
│               ├── strategy/   # Payment strategy unit tests
│               ├── scheduler/  # Scheduler unit tests
│               ├── search/     # Search text & specification unit tests
│               └── integration/# SpringBootTest MockMvc & Testcontainers tests
└── frontend/                   # React 19 + Vite client
```

### Module Design Conventions & Coupling

- **Encapsulation & High Cohesion**: Each business capability is self-contained. Adding or modifying a feature touches only files within that module.
- **Shared Infrastructure (`common`)**: Contains exception handling, idempotency, rate limiting and cache configuration. Checkout-result queries and promotion error translation introduce explicit domain coupling here.
- **Data Seeding (`bootstrap`)**: Populates seed data at startup. No module ever imports classes from `bootstrap`.
- **Known Coupling (Monolith Domain Relationships)**:
  - `order ↔ payment`: `order` checks payment status and allows payment retries; `payment` (MoMo IPN, timeout scheduler, refund service) updates order statuses and variant stock upon payment events.
  - `user ↔ order`: `user` exposes user order history; `order` associates placed orders with customer accounts.
  These bidirectional references reflect natural business relationships within a single monolithic database schema.

## Local Development

**Prerequisites**: Java 21, Maven (or the bundled `mvnw`), Docker (for PostgreSQL/Testcontainers), Node 22.12+ (verified with Node 24; Vite 8). Browser tests additionally require Chrome.

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Configure environment
cp .env.example .env   # fill in DB_PASSWORD, JWT_SECRET_KEY; MoMo keys are NOT required — mock-enabled=true by default

# 3. Run the backend (from backend/)
cd backend
./mvnw spring-boot:run
# API on http://localhost:8080

# 4. Run tests (Docker running; PostgreSQL Testcontainers, isolated from local DB)
./mvnw test

# 5. Run the frontend (from frontend/)
cd ../frontend
npm ci
npm run dev
```

## Future Improvements

- Implement the real MoMo refund HTTP call (the state machine and trigger logic already exist).
- Add a code path to transition a COD `Payment` out of `PENDING` (e.g., on delivery confirmation).
- Add Jacoco (or similar) for a real test-coverage metric.
- Add a backend service definition to `docker-compose.yml` for a fully containerized local stack.
- Move rate-limit buckets to a shared store (e.g. `bucket4j-redis`) if the backend is ever scaled to multiple instances, since the current implementation is in-memory per instance.

## Promotion V1

Portfolio/demo scope: vouchers, automatic order-discount tiers and product discounts. Admin management is available at **`/admin/promotions`**. No real payments/refunds, flash-sale inventory reservation, stacked vouchers or distance-based shipping are included.

### Pricing and data model

`promotion_programs` owns `vouchers`, `order_discounts` and `product_discounts`. `voucher_usages` records order/user/voucher history as APPLIED or RELEASED. PostgreSQL enforces one usage per order and one APPLIED usage per voucher/user. V4 adds these tables and backfills historical order snapshots; V5 links checkout keys to orders. Tests cover fresh schema, migration from populated V3 and refusal of invalid historical money values.

1. Apply the highest product percentage first, breaking ties deterministically by rule ID. Round the discount **per unit** using `BigDecimal`, integer VND and `HALF_UP`, then multiply by quantity.
2. Calculate subtotal only for selected lines. `cartItemIds: null` or `[]` means the whole cart; foreign/unknown IDs or an empty checkout are rejected.
3. Compare monetary savings from eligible auto tiers and the valid voucher. Apply at most one order discount; voucher wins a tie. A voucher that loses to auto does not consume a use.
4. Respect minimum/cap and never discount more than subtotal. Add backend shipping last; shipping is not discounted.
5. Validity uses the injected `Clock`: `start <= now < end`. ACTIVE/INACTIVE is admin status; SCHEDULED/RUNNING/ENDED is derived. Promotion dates are Instant/TIMESTAMPTZ; UI uses Vietnam time.

Example: 200,000 × 2, product discount 20%, auto 5%, voucher 10% capped at 30,000, ship 30,000 → subtotal 320,000, voucher savings 30,000, **total 320,000 VND**.

Configure shipping with `app.shipping.flat-fee` / `SHIPPING_FLAT_FEE` (default 30,000 VND). Client `shippingFee` is ignored, including negative/arbitrary values. Zero-total checkout is COD-only; tests configure shipping=0 for this case.

### API and confirmation flow

All checkout calls require authentication. Preview has no reservation side effect:

```http
POST /api/v1/checkout/preview
Content-Type: application/json
Authorization: Bearer <access-token>

{"cartItemIds":[1],"voucherCode":"sale10"}
```

Example response (illustrative IDs):

```json
{
  "items":[{"cartItemId":1,"productId":10,"quantity":2,"originalUnitPrice":200000,"unitPrice":160000,"productDiscountPercent":20,"lineTotal":320000}],
  "originalSubtotal":400000,"productDiscountTotal":80000,"subtotal":320000,
  "orderDiscount":{"source":"VOUCHER","id":1,"code":"SALE10","percent":10,"amount":30000},
  "shippingFee":30000,"total":320000,
  "voucher":{"code":"SALE10","applied":true,"reason":null,"message":null}
}
```

Invalid vouchers still produce a preview with `applied=false`, reason and message (not found, inactive, future/expired, sold out, minimum not met or already used). Checkout blocks until the code is removed/corrected. `NOT_BETTER_THAN_ORDER_DISCOUNT` is informational: auto wins, no voucher quota consumed.

```http
POST /api/v1/checkout
Authorization: Bearer <access-token>
Idempotency-Key: <unique-attempt-key>
Content-Type: application/json

{"firstName":"Demo","lastName":"Buyer","email":"demo@example.com","address":"Demo street","city":"Ho Chi Minh","ward":"Demo ward","phone":"0900000000","paymentMethod":"MOMO","cartItemIds":[1],"voucherCode":"SALE10","expectedTotal":320000}
```

Backend recalculates every checkout. `expectedTotal` is a required integer comparison, never the charged amount. A mismatch returns **409 PRICE_CHANGED** with `breakdown`, with no committed order/payment/usage. The UI displays the new price and requires **Xác nhận lại và đặt hàng**. A reservation race returns **409 VOUCHER_SOLD_OUT**, also rolling everything back; UI refreshes preview and requires correction/confirmation instead of silently falling back or resubmitting.

After losing a response, the UI calls `GET /api/v1/checkout/result` with the **same Idempotency-Key**. Only its owner can retrieve the committed order; missing/rolled-back/non-owned results return 404. A 404 does not prove an in-flight transaction will never commit: explicit retry retains the same key and fetches a fresh preview. Keys are cleaned after 24 hours; uncertain older attempts are directed to order history. No response replay framework or order guessing by amount/cart/time is used.

ADMIN-only routes use prefix `/api/v1/admin/promotions`:

| Routes | Purpose |
|---|---|
| POST /, GET /?page=0&size=10, GET/PUT/DELETE /{id}, PATCH /{id}/status | Program CRUD and ACTIVE/INACTIVE |
| POST /{id}/vouchers, PUT/DELETE /{id}/vouchers/{vid}, PATCH /{id}/vouchers/{vid}/status | Voucher management |
| POST /{id}/order-discounts, PUT/DELETE /{id}/order-discounts/{oid} | Automatic tiers |
| POST /{id}/product-discounts, PUT/DELETE /{id}/product-discounts/{pid} | Bulk `productIds`; `pid` is the rule ID |

Example bodies: program `{"name":"Demo","startDate":"2026-01-01T00:00:00Z","endDate":"2099-01-01T00:00:00Z"}`; voucher `{"code":"SALE10","discountPercent":10,"minimumAmount":300000,"maximumDiscount":30000,"quantity":5}`; tier `{"minimumAmount":300000,"discountPercent":5}`; product rules `{"productIds":[1,2],"discountPercent":20}`.

Validation: percentage (0,100], minimum >=0, cap null or >0, end after start, quantity >0 and >=used. Voucher code is trimmed uppercase `[A-Z0-9_-]`, max 50, unique. Admin quota edits lock before reading the counter; clients cannot write `usedQuantity`. Bulk creation is atomic. Invalid inputs return 400, missing/foreign child IDs 404, duplicates/history/FK conflicts 409. Used history, including RELEASED, blocks hard deletion: deactivate instead.

### Transactions, snapshots and cache

- Reserve only the winning voucher **after the order has an ID**, in the same checkout transaction. Conditional increment + usage insert + key/order link commit together; any failure rolls back order, payment, stock/cart and key. Constraint errors are translated after rollback.
- APPLIED includes pending reservations and completed purchases. When an order actually becomes CANCELLED, release APPLIED → RELEASED and decrement once. Failure/out-of-stock fulfillment, timeout and admin cancel all participate; repeated release does nothing and DONE never releases.
- **Admin cancellation of a PAID MoMo mock order leaves payment PAID as history and releases the voucher. There is no real refund.** Simulated refund state in other branches joins its caller's transaction and rolls back on optimistic conflicts; refund HTTP is not implemented.
- Order/item snapshots preserve original/final unit prices, product %, subtotal, order savings/source/%, voucher code, shipping and total. Admin edits affect new preview/checkout only, including while a program is RUNNING.
- `ProductBaseCache` stores base data in a separate bean. Live sales are composed into fresh DTOs after cache retrieval, never mutated into cached responses. List/cart fetch rules in a batch and use the shared calculator; sale end time comes from the same winning program. Disable/expiry/edit takes effect without waiting for TTL.
- Product `price` / cart `productPrice` remain original. `salePrice` / `unitPrice` are discounted; cart `subTotal` is after product discount and original totals remain available. `saleEndsAt` is an ISO Instant, null without an active discount, consistently for product/cart. Historical order item `price` retains its purchased value.

### Demo and verification

1. Register a demo customer. To prepare a local admin, explicitly promote your own account in your local database and log in again; registration never grants ADMIN.
2. Open `/admin/promotions`, create a program surrounding now, add product 20%, auto 5%, and SALE10 10% with sufficient quota. Use the example cart above to demonstrate cap/selection.
3. Browse product/cart; try a bad voucher, remove it and apply SALE10. Checkout shows backend shipping and savings.
4. Change % in another admin session after preview; checkout returns PRICE_CHANGED and waits for explicit confirmation. Inspect the created order after another admin edit: snapshot stays unchanged.
5. Use MoMo mock SUCCESS/FAIL/PENDING; cancel and verify release, reject late SUCCESS, and try hard deletion of used history (409). Turn off the program and check live catalog prices.

Final checks (use `mvnw.cmd` on Windows):

```bash
cd backend
./mvnw -B clean test
./mvnw -B clean package -DskipTests
cd ../frontend
npm ci
npm run lint
npm run build
npm run test:promotion
node --test tests/auth-refresh.browser.test.mjs
cd ../backend
./mvnw -B -Dtest=PromotionBrowserE2E test
```

Chrome tests use Node's built-in test runner/WebSocket and an isolated profile, with no added dependency. Set `CHROME_PATH` if not at the Windows default; port 5179 must be free. Fixture tests cover stale preview, sold-out, price confirmation, lost response/reload/same-key retry, selected/free cart, snapshot/admin forms, responsive content, and mock controls. The auth fixture test covers single-flight, delayed 401, failure cleanup and retry.

`PromotionBrowserE2E` is opt-in (outside default `*Test` naming): JUnit starts real Spring HTTP on a random port and a disposable PostgreSQL Testcontainer, then React/Chrome. It uses real login, refresh, admin/cart/checkout/payment APIs and checks DB invariants. Only the test database is seeded; the existing local database is never reset. Browser output: `backend/target/phase9-browser-e2e.log`.

See [acceptance evidence](docs/PROMOTION-V1-ACCEPTANCE.md) for executed counts and coverage: pricing examples, 20/5 voucher concurrency, same-user uniqueness, old/new schema, payment races/refund rollback, snapshot edits and cache tests.

### V1 limitations

- Preview reserves neither price nor stock/quota. If composition changes but total stays equal, no PRICE_CHANGED is emitted; checkout saves the actual composition. MoMo pending orders do not reserve inventory.
- The fixture suite is distinct from real HTTP E2E; passing tests do not establish production gateway readiness or a coverage percentage.
- Frontend lint has a baseline of 9 errors / 5 warnings; this work must not increase it. Existing shared customer-header overflow at 320px is outside this feature; promotion/cart/checkout content and the admin route are tested separately.
- Refresh single-flight is per browser JavaScript context, not cross-tab distributed coordination. Existing server rotation/reuse detection remains intact; no auth redesign is included.
- Checkout orchestrates cart/user/order/payment/promotion. Order and payment share state transitions; promotion usages reference orders/users. These explicit modular-monolith dependencies are retained, with no new service boundary or infrastructure.
