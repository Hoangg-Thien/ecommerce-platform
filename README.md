# E-Commerce Platform

A full-stack e-commerce application built with **Java 21 / Spring Boot 3.5** on the backend and **React 19 (Vite)** on the frontend. Both the backend REST API and the frontend are deployed on **Render**.

The project is built as a backend-engineering showcase rather than a plain CRUD store: the checkout path is transactional, stock updates use optimistic locking, checkout requests are idempotent, refresh tokens are revocable server-side, and every endpoint group has its own rate limit.

---

## Table of Contents

- [E-Commerce Platform](#e-commerce-platform)
  - [Table of Contents](#table-of-contents)
  - [Overview](#overview)
  - [Key Features](#key-features)
    - [Authentication \& Security](#authentication--security)
    - [Product \& Inventory](#product--inventory)
    - [Cart](#cart)
    - [Promotions \& Vouchers](#promotions--vouchers)
    - [Checkout](#checkout)
    - [Orders](#orders)
    - [Payment](#payment)
    - [Reliability \& Concurrency](#reliability--concurrency)
  - [System Architecture](#system-architecture)
  - [Database Design](#database-design)
  - [Authentication Flow](#authentication-flow)
  - [Checkout Flow](#checkout-flow)
  - [Promotion](#promotion)
    - [Pricing rules](#pricing-rules)
    - [Confirmation flow](#confirmation-flow)
    - [Voucher lifecycle \& snapshots](#voucher-lifecycle--snapshots)
    - [Limitations](#limitations)
  - [Product Search](#product-search)
  - [API Documentation](#api-documentation)
  - [Testing](#testing)
  - [Tech Stack](#tech-stack)
  - [Deployment](#deployment)
  - [Engineering Highlights](#engineering-highlights)
  - [Project Structure](#project-structure)
    - [Module Design Conventions \& Coupling](#module-design-conventions--coupling)
  - [Local Development](#local-development)
  - [Future Improvements](#future-improvements)

---

## Overview

The system models a single-vendor online store with two roles:

- **USER** — registers/logs in, browses products, manages a cart, checks out (COD or MoMo), views their own orders and payments, and can retry payment on an unpaid MoMo order.
- **ADMIN** — manages products, categories, promotions/vouchers and users, and updates order status.

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
- `GET` product-by-id and category-list responses are cached with Caffeine (30 min TTL), evicted on any write. Only base catalog data is cached; live sale prices are composed per request (see [Promotion](#promotion)).
- Stock is enforced with **optimistic locking** (`@Version` on `Product` and `ProductVariant`) — verified by a concurrency test that races two checkouts against the last unit of stock.

### Cart
- One cart per user, created lazily. Cart lines reference a specific `ProductVariant` (product + size), unique per cart.
- Add-to-cart merges quantity into an existing line and re-validates against live stock; quantity update and item removal are also supported.

### Promotions & Vouchers
- Admins manage promotion programs at `/admin/promotions`: product discounts, automatic order-discount tiers and vouchers with usage quotas.
- The backend is the only source of price: it recalculates every checkout, ignores any client-supplied shipping fee and applies at most one order discount (the larger saving wins).
- Price confirmation: the client sends `expectedTotal`; a mismatch returns `409 PRICE_CHANGED` with the new breakdown and nothing is committed.
- Vouchers are reserved inside the checkout transaction and released exactly once when the order is cancelled; orders keep an immutable pricing snapshot. Details in [Promotion](#promotion).

### Checkout
- Idempotent by design: the client sends an `Idempotency-Key` header (enforced by a custom `@Idempotent` annotation + `IdempotencyInterceptor`), and the key is inserted into a unique-constrained `idempotency_keys` table inside the same transaction — a duplicate key is rejected as a conflicting/duplicate request before any order is built. The key is linked to the committed order (Flyway V5), so a client that lost the response can recover the result with `GET /api/v1/checkout/result` using the same key (owner-only).
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
- Cancelling a `PAID` mock order leaves the payment `PAID` as history and releases its voucher; there is no real refund.
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
| `Order` | N—1 `User`, 1—N `OrderItem`, 1—1 `Payment` | `@Version`, snapshots shipping info and promotion pricing (subtotal, discount, voucher code, source, percent) |
| `OrderItem` | N—1 `Order`, N—1 `Product` | snapshots `size`, `price`, `original_price` and product-discount percent at purchase time |
| `Payment` | 1—1 `Order` | `@Version`, stores MoMo `transactionId`/`momoOrderId`/`momoRequestId` |
| `PromotionProgram` | 1—N `Voucher`, `OrderDiscount`, `ProductDiscount` | active window stored as `TIMESTAMPTZ`; ACTIVE/INACTIVE set by admin, SCHEDULED/RUNNING/ENDED derived |
| `Voucher` | N—1 `PromotionProgram` | unique code, percent + cap + minimum amount, quota with `used_quantity` |
| `OrderDiscount` | N—1 `PromotionProgram` | automatic tier: minimum order amount → percent |
| `ProductDiscount` | N—1 `PromotionProgram`, N—1 `Product` | percentage rule per product |
| `VoucherUsage` | N—1 `Voucher`, N—1 `User`, 1—1 `Order` | `APPLIED`/`RELEASED`; unique per order, one `APPLIED` per voucher/user (partial unique index) |
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
2. Load user's cart and the selected lines (`cartItemIds`; null/empty = whole cart), reject if none
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

## Promotion

Demo-store promotions: product discounts, automatic order-discount tiers and vouchers, managed by admins at `/admin/promotions`. Out of scope: real payments/refunds, flash-sale stock reservation, stackable vouchers and distance-based shipping. Request/response examples, validation rules and the demo script are in [docs/PROMOTION.md](docs/PROMOTION.md).

### Pricing rules

1. **Product discount**: the highest percentage wins (ties broken by rule ID). The discount is rounded **per unit** (`BigDecimal`, integer VND, `HALF_UP`) and then multiplied by quantity.
2. **Subtotal** covers only the selected cart lines (`cartItemIds` null or `[]` = whole cart; unknown or foreign IDs are rejected).
3. **One order discount at most**: the larger saving between the best automatic tier and a valid voucher is applied; a voucher wins ties, and a voucher that loses consumes no quota.
4. Minimum amount and cap are respected and the discount never exceeds the subtotal. **Shipping** is a server-side flat fee (`app.shipping.flat-fee`, default 30,000 VND), added last and never discounted; the client value is ignored.
5. A program is effective when `start <= now < end` (injected `Clock`, stored as `TIMESTAMPTZ`).

Example: 200,000 × 2 with a 20% product discount, a 5% automatic tier and `SALE10` (10%, capped at 30,000), shipping 30,000 → subtotal 320,000, voucher saving 30,000 (beats the 16,000 tier), **total 320,000 VND**.

### Confirmation flow

- `POST /api/v1/checkout/preview` returns the full breakdown with no side effects; an invalid voucher yields `applied=false` plus a reason instead of an error.
- `POST /api/v1/checkout` recalculates everything. `expectedTotal` is a required comparison value, never the amount charged: a mismatch returns **409 `PRICE_CHANGED`** with the new breakdown and nothing is committed, and the UI asks the customer to confirm the new price. Losing a voucher race returns **409 `VOUCHER_SOLD_OUT`** and rolls everything back.
- After a lost response, the UI calls `GET /api/v1/checkout/result` with the **same `Idempotency-Key`**. Only the owner can read a committed result; anything else is `404`, which does not prove an in-flight transaction will never commit, so retries keep the same key and fetch a fresh preview first. Keys are purged after 24 hours; no response-replay layer or order guessing is used.

### Voucher lifecycle & snapshots

- The winning voucher is reserved **after the order has an ID**, in the same transaction: conditional quota increment, usage insert and key-to-order link commit together, and any failure rolls back order, payment, stock/cart and key.
- `APPLIED → RELEASED` happens once when an order actually becomes `CANCELLED` (failed payment or out-of-stock fulfilment, timeout, admin cancel); repeats are no-ops and `DONE` never releases.
- Orders and items snapshot original/final prices, discount source and percent, voucher code, shipping and total. Admin edits affect only new previews and checkouts.
- `ProductBaseCache` caches base catalog data only; sale prices are composed live per request, so disabling, editing or expiring a program takes effect without waiting for the TTL. `price` stays the original price; `salePrice` / `unitPrice` carry the discounted price and `saleEndsAt` the end time.

### Limitations

- Preview reserves neither price, stock nor quota. If only the composition of discounts changes while the total stays equal, no `PRICE_CHANGED` is emitted and checkout stores the actual composition. Pending MoMo orders do not reserve inventory.
- Passing tests, including the Chrome end-to-end run, do not establish production-gateway readiness; no coverage percentage is claimed. Evidence and executed counts: [docs/PROMOTION-ACCEPTANCE.md](docs/PROMOTION-ACCEPTANCE.md).
- Token-refresh single-flight is per browser JavaScript context, not coordinated across tabs; server-side rotation and reuse detection are unchanged.

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
| **Checkout** | `POST /api/v1/checkout/preview`, `POST /api/v1/checkout` (requires `Idempotency-Key`), `GET /api/v1/checkout/result` (same `Idempotency-Key`; owner-only) |
| **Orders** | `GET /api/v1/orders` (paginated), `GET /api/v1/orders/{id}`, `POST /api/v1/orders/{id}/retry-payment` |
| **Payments** | `POST /api/v1/payments/momo/ipn` (public, HMAC-verified), `GET /api/v1/payments/momo/return` (public), `GET /api/v1/payments/order/{orderId}` (owner-only) |
| **Mock Payment** | `POST /api/v1/mock-payments/{orderId}/simulate` — only active when `payment.momo.mock-enabled=true` |
| **Admin** | `GET /api/v1/admin/orders` (filter by `status`, `email`), `GET /api/v1/admin/orders/{id}`, `PATCH /api/v1/admin/orders/{id}/status`; `GET /api/v1/admin/users`, `GET /api/v1/admin/users/{id}`, `PATCH /api/v1/admin/users/{id}/role`; `GET /api/v1/admin/dashboard`; `/api/v1/admin/promotions/**` (programs, vouchers, tiers, product discounts — see [docs/PROMOTION.md](docs/PROMOTION.md)) |

## Testing

Tests are split into the groups below. Promotion adds pricing, migration, voucher-lifecycle, concurrency, browser and authentication regressions; executed counts and acceptance results are recorded in [Promotion acceptance](docs/PROMOTION-ACCEPTANCE.md).

- **Unit tests** (Mockito) — services, JWT logic, both payment strategies, MoMo signature/IPN logic, refund logic, schedulers, and `SearchTextUtilsTest` (normalization, diacritics removal, LIKE wildcard escaping).
- **Integration tests** (`@SpringBootTest` + `MockMvc`) — `AuthControllerTest`, `ProductControllerTest` (search param mapping & validation), `ProductSearchIntegrationTest` (full-stack search against real PostgreSQL with Flyway V3 migration covering unaccent, case-insensitivity, ranking, wildcard escaping, category filtering, pagination, and schema verification), `CheckoutControllerTest`, `PaymentControllerTest`, `AdminEndpointSecurityTest`, `OrderTimeoutIntegrationTest`, `RateLimitFilterTest`.
- **Concurrency tests** — `ProductConcurrencyTest` and `OrderConcurrencyTest`, using `ExecutorService`/`CountDownLatch` to force two threads to race the same stock row and assert single-winner semantics.
- **Browser tests** (Chrome, opt-in) — `PromotionBrowserE2E` (real HTTP server + disposable Testcontainers PostgreSQL) and `npm run test:promotion` / `tests/auth-refresh.browser.test.mjs` in `frontend/`. They are not part of the default `mvn test` or CI; see [docs/PROMOTION-ACCEPTANCE.md](docs/PROMOTION-ACCEPTANCE.md).
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
| **Testing** | JUnit 5, Mockito, MockMvc, Spring Security Test, Testcontainers (PostgreSQL) |
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
- Backend CI (`backendci.yml`) runs `mvn test` and packages the JAR on push and PR; frontend CI (`frontendci.yml`) runs `npm ci` and `npm run build` on pushes that touch `frontend/`. Neither publishes images or triggers deployment — deployment to Render is configured on Render's side against the repository.

## Engineering Highlights

- **Idempotent checkout** implemented at the database level (unique-constrained key table inside the transaction), not just an in-memory or cache-based check — safe across app restarts and multiple instances. A committed key is linked to its order, so a lost response can be recovered via `GET /api/v1/checkout/result` without guessing orders.
- **Refresh-token rotation with reuse detection**: tokens are hashed at rest, rotated on every refresh, and a replayed/revoked token triggers a full account-wide token revocation — a materially stronger model than "JWT refresh token, no DB check."
- **Payment consistency under race conditions**: late success cannot revive cancelled orders; voucher release is idempotent and simulated refund state shares its caller's transaction. Optimistic conflicts roll back consistently. Tests use MoMo mock, not a live sandbox.
- **Server-authoritative promotion pricing**: every checkout is recalculated from live rules; `expectedTotal` only detects stale prices (`409 PRICE_CHANGED`), vouchers are reserved with a conditional increment in the order transaction, and orders keep immutable pricing snapshots.
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
├── docs/                       # Promotion acceptance evidence, promotion API details
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
│       │   ├── promotion/      # Programs, vouchers, tiers & product discounts, pricing calculator, voucher usage
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
└── frontend/                   # React 19 + Vite client (tests/: opt-in Chrome browser tests)
```

### Module Design Conventions & Coupling

- **Encapsulation & High Cohesion**: Each business capability is self-contained. Adding or modifying a feature touches only files within that module.
- **Shared Infrastructure (`common`)**: Contains exception handling, idempotency, rate limiting and cache configuration. Checkout-result queries and promotion error translation introduce explicit domain coupling here.
- **Data Seeding (`bootstrap`)**: Populates seed data at startup. No module ever imports classes from `bootstrap`.
- **Known Coupling (Monolith Domain Relationships)**:
  - `order ↔ payment`: `order` checks payment status and allows payment retries; `payment` (MoMo IPN, timeout scheduler, refund service) updates order statuses and variant stock upon payment events.
  - `user ↔ order`: `user` exposes user order history; `order` associates placed orders with customer accounts.
  - `checkout → promotion / payment / order`: checkout orchestrates cart, user, order, payment and promotion; `voucher_usages` reference orders and users.
  These bidirectional references reflect natural business relationships within a single monolithic database schema.

## Local Development

**Prerequisites**: Java 21, Maven (or the bundled `mvnw`), Docker (for PostgreSQL/Testcontainers), Node 20.19+ or 22.12+ (Vite 8; verified with Node 24). Browser tests additionally require Chrome.

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
- Fix the existing frontend ESLint baseline (9 errors) and review the `npm audit` findings.
- Add a backend service definition to `docker-compose.yml` for a fully containerized local stack.
- Move rate-limit buckets to a shared store (e.g. `bucket4j-redis`) if the backend is ever scaled to multiple instances, since the current implementation is in-memory per instance.