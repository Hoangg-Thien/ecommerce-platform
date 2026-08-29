# E-Commerce Frontend

React (Vite) client for the [E-Commerce Platform](../README.md) backend. Handles product browsing, cart, checkout, order history, and JWT-based authentication against the Spring Boot API.

This README covers the frontend only. For backend architecture, API details, and deployment, see the [root README](../README.md).

## Tech Stack

| Category | Technology |
|---|---|
| Framework | React 19 |
| Build tool | Vite 8 |
| Routing | React Router 7 |
| HTTP client | Axios |
| Icons | lucide-react |
| Linting | ESLint 10 |

## Pages & Routing

Routes are declared in `src/App.jsx`:

| Path | Page | Notes |
|---|---|---|
| `/` | `ProductList` | Home / product catalog |
| `/product/:id` | `ProductDetail` | Single product view |
| `/cart` | `Cart` | Shopping cart |
| `/checkout` | `Checkout` | Shipping + payment method selection |
| `/mock-payment` | `MockPayment` | Simulated MoMo payment screen (backend runs with `payment.momo.mock-enabled=true`) |
| `/payment-result` | `PaymentResult` | Post-payment result screen |
| `/orders` | `OrderList` | Logged-in user's order history |
| `/login`, `/register` | `Login`, `Register` | Auth pages |

`pages/admin/` also contains `ProductManagement.jsx`, `CategoryManagement.jsx`, and `OrderManagement.jsx`, and the header/nav (`components/layout/Header.jsx`, `components/Navbar.jsx`) link to `/admin` for `ADMIN` users. **No `/admin` route is currently registered in `App.jsx`**, so these admin pages are not reachable yet — they exist as components but aren't wired into the router. This is a known gap, not a hidden feature.

`ProtectedRoute` (`src/components/ProtectedRoute.jsx`) is available for gating routes behind login and, optionally, the `ADMIN` role, but is not yet applied to any route in `App.jsx`.

## State Management

No external state library — global state is handled with React Context:

- **`AuthContext`** — holds the logged-in user (`id`, `email`, `role`), restores the session on page reload by calling `/auth/refresh` (the refresh token lives in an HttpOnly cookie set by the backend) and re-populating the in-memory access token, and exposes `login`/`register`/`logout`.
- **`CartContext`** — fetches the cart via `cartApi` whenever `user` becomes non-null, clears it locally on logout.
- **`ToastContext`** — app-wide toast notifications.

## API Layer

`src/api/` wraps each backend resource group in its own module (`authApi`, `productApi`, `categoryApi`, `cartApi`, `checkoutApi`, `orderApi`, `paymentApi`, `mockPaymentApi`), all built on a shared `axiosClient`:

- Base URL: `VITE_API_BASE_URL` env var, falling back to `http://localhost:8080/api/v1`.
- `withCredentials: true` so the HttpOnly refresh-token cookie is sent automatically.
- The JWT **access token is kept in memory only** (not `localStorage`), attached to every request via a request interceptor — this avoids exposing it to XSS-based token theft.
- A response interceptor catches `401`/`403`, calls `/auth/refresh` once, retries the original request with the new access token, and redirects to `/login` if the refresh itself fails. This mirrors the backend's refresh-token rotation model.

`user` (id/email/role only — never the token) is cached in `localStorage` purely so the UI can restore a logged-in view before the silent refresh call resolves on page load.

## Project Structure

```
frontend/
├── index.html
├── vite.config.js
├── package.json
└── src/
    ├── api/            # axiosClient + one module per backend resource
    ├── components/
    │   ├── layout/     # Header, MainLayout
    │   ├── auth/ cart/ checkout/ order/ product/ ui/
    │   ├── Navbar.jsx
    │   └── ProtectedRoute.jsx
    ├── context/        # AuthContext, CartContext, ToastContext
    ├── pages/          # one folder per route (see table above)
    ├── styles/         # design-tokens.css
    ├── App.jsx         # route declarations
    └── main.jsx        # app entry point
```

## Local Development

```bash
npm install
npm run dev       # starts Vite dev server (default http://localhost:5173)
npm run build     # production build
npm run preview   # preview the production build locally
npm run lint      # ESLint
```

### Environment Variables

| Variable | Purpose | Default |
|---|---|---|
| `VITE_API_BASE_URL` | Base URL of the backend API | `http://localhost:8080/api/v1` |

Create a `.env` file in `frontend/` to override it (e.g. pointing at the deployed Render backend URL for a production build).

## Known Gaps

- Admin pages (`pages/admin/*`) are implemented but not yet registered in the router (`App.jsx` has no `/admin` route), so the "Quản trị" links currently lead nowhere.
- `ProtectedRoute` exists but isn't applied to any route yet — pages like `/cart`, `/checkout`, and `/orders` are not currently guarded client-side against an unauthenticated user (the backend still enforces auth on the API itself).
