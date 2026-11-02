# Promotion V1 — Phase 9 acceptance

Verified on 2026-11-02, Windows, Node 24.15.0, Chrome and Docker/PostgreSQL Testcontainers. Scope: portfolio demo with COD and MoMo mock. No real gateway/refund verification is claimed.

## Executed checks

| Command/check | Result |
|---|---|
| Backend `mvnw.cmd -B clean test` | PASS: 399 tests, 0 failures/errors/skips (Phase 8 baseline: 397) |
| Backend `mvnw.cmd -B clean package -DskipTests` | PASS: executable JAR packaged; tests were run separately above |
| Auth/security focused suite: JwtServiceTest, RefreshTokenServiceTest, AuthControllerTest, AdminEndpointSecurityTest | PASS: 41 tests |
| Explicit `-Dtest=RefreshRotationIntegrationTest,PromotionBrowserE2E` | PASS: 2 JUnit tests; browser subprocess 1 real HTTP E2E scenario |
| Frontend `npm ci` | PASS; reports 12 dependency vulnerabilities (6 moderate, 6 high); no dependency upgrade performed |
| Frontend `npm run lint` | FAIL: 9 errors, 5 warnings, unchanged from Phase 8 baseline |
| Frontend `npm run build` | PASS |
| Frontend `npm run test:promotion` | PASS: 11 scenario subtests plus parent, Node reports 12 tests |
| Frontend `node --test tests/auth-refresh.browser.test.mjs` | PASS: 1 scenario, including concurrent refresh, delayed 401 and failure recovery |

The focused suites overlap the 399-test default run; counts must not be added together. `PromotionBrowserE2E` is explicitly invoked and excluded from default `*Test` discovery. Lint is not green: acceptance follows the plan's requirement of no increase over baseline.

Local ignored evidence logs: `phase9-clean-test.log`, `phase9-package.log`, `phase9-npm-ci.log`, `phase9-lint.log`, `phase9-build.log`, `phase9-fixture.log`, `phase9-auth-browser.log`, `phase9-rotation-e2e.log`, `phase9-browser-e2e.log`. The E2E harness normally writes its browser log to `backend/target/phase9-browser-e2e.log`; `clean` removes target artifacts.

## Authorized refresh fix and regression evidence

Refresh JWTs previously had identical claims for the same user when issued within one second. JWT timestamp precision meant the signed strings and persisted hashes could be identical, violating `refresh_tokens_token_hash_key`. Separately, session restoration and concurrent unauthorized API responses could each start refresh; rotation then exposed callers to an already revoked cookie.

- Only refresh JWTs now receive a random UUID `jti`. Access JWT claims, signature, expiration policy, cookie/API contract, hashing and database constraints remain unchanged.
- `refreshSession` shares one in-flight Promise across AuthContext restoration and Axios retries. A request's session generation allows a delayed 401 to reuse the newer session even when two access JWT strings happen to match. The Promise clears on success or failure; the raw refresh request cannot recursively invoke the response interceptor.
- AuthContext ignores an abandoned effect's results while retaining React StrictMode. Backend rotation and reuse detection are unchanged.
- Before the fix, the new JWT regression failed for missing `jti`; the browser regression observed 4 refresh calls where 1 was expected. Both pass after the fix.
- Unit regression issues 100 refresh JWTs and checks unique UUIDs, validity, lifetime and unchanged access claims. Real PostgreSQL integration rotates 10 times immediately, verifies 11 distinct stored hashes and exactly one active token, then proves replay still revokes the successor.
- Real E2E logs in through the UI, concurrently requests refresh and authenticated APIs, reloads checkout through cookie restoration, verifies exactly one refresh for restoration under StrictMode, and completes checkout.

Single-flight coordinates one JavaScript context. Cross-tab coordination and a broader authentication redesign are outside this authorized fix.

## Promotion acceptance coverage

| Behavior | Evidence |
|---|---|
| Pricing examples, rounding, tie/cap, time boundaries | PricingCalculatorTest, PromotionPricingServiceTest |
| PostgreSQL constraints, voucher quota concurrency and same-user uniqueness | PromotionRepositoryIntegrationTest |
| Migration of existing orders and invalid historical values | ExistingOrderMigrationTest, PromotionMigrationGuardTest; default suite starts fresh PostgreSQL schemas too |
| Backend shipping, selected/all cart, zero total, expectedTotal, reserve rollback and key-to-order ownership/recovery | ShippingCheckoutIntegrationTest, ZeroTotalCheckoutIntegrationTest, PromotionCheckoutIntegrationTest, CheckoutControllerTest |
| Cancellation/release once, DONE guard, timeout/callback races, optimistic conflict, simulated refund rollback | VoucherLifecycleIntegrationTest and payment/order regression suites |
| Admin validation, locking, history protection, live product/cart pricing and cache | AdminPromotionIntegrationTest and product/cart regression suites |
| Stale preview, sold-out confirmation, lost response, reload/same-key retry, selected cart, admin forms and permissions | Fixture Chrome acceptance suite |
| Real login, refresh, checkout, admin edits and mock lifecycle | PromotionBrowserE2E + promotion.real.test.mjs |

The real E2E uses a disposable PostgreSQL database and real Spring HTTP endpoints. It drives login, checkout, price reconfirmation, mock payment and program creation through React; some fixture setup and state checks use authenticated real APIs. It verifies:

1. No-promotion COD total, backend shipping and cart clearing (stock assertions are covered by backend regression tests).
2. Product sale and cart end time; voucher selection; admin edit after preview produces PRICE_CHANGED and no order until explicit reconfirmation.
3. Order snapshots survive later edits; mock SUCCESS and admin cancellation of a PAID mock order retain payment PAID and release the voucher.
4. FAIL/PENDING, voucher reuse, cancellation and late SUCCESS rejection; used-history deletion returns 409; disabling a program immediately clears live sale pricing.
5. Final database invariants: four orders, matching payment/order totals, four committed key/order links, no remaining APPLIED voucher usages and zero used quota.

Concurrency, timeout and migration cases are deterministic backend tests, not simulated browser timing tests. Fixtures and real E2E are reported separately; no coverage percentage is claimed.

## Boundaries and remaining issues

- Existing local database was not reset or modified by these tests. No Phase 9 migration, dependency, gateway or refund implementation was added.
- MoMo remains mock-only. Admin cancellation after mock PAID does not refund money; payment remains PAID as history.
- Existing frontend lint findings and customer-header overflow at 320px remain documented limitations. Promotion content and responsive scenarios are tested; this is not a claim that every application page is fully responsive.
- README retains its original structure, with a Promotion V1 section and targeted corrections to stale transaction/schema descriptions.
- The specification file remains untracked and is excluded from the Phase 9 commit.

Reproduction commands and API/demo examples are in the README's Promotion V1 section. Chrome suites require a free port 5179; run them sequentially. Start Docker first; set `CHROME_PATH` if Chrome is not at the default Windows location.
