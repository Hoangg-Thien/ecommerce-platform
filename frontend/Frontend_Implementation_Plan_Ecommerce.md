# KẾ HOẠCH TRIỂN KHAI FRONTEND (REACT + VITE)
### Dựa trên phân tích source code backend thực tế (Spring Boot)

> Toàn bộ nội dung dưới đây được đối chiếu trực tiếp với code trong `backend/src/main/java/com/ecommerce`. Không có endpoint, field, hay flow nào bị bịa thêm. Nếu backend chưa hỗ trợ, mục đó được đánh dấu rõ **"Backend chưa hỗ trợ"**.

---

## 1. PHÂN TÍCH BACKEND

### 1.1. Danh sách REST Endpoint

| Method | Endpoint | Auth | Request | Response | Ghi chú |
|---|---|---|---|---|---|
| POST | `/api/v1/auth/register` | Public | `{email, password}` | `AuthResponse` (201) | Tự động login sau khi đăng ký (trả token luôn) |
| POST | `/api/v1/auth/login` | Public | `{email, password}` | `AuthResponse` (200) | |
| POST | `/api/v1/auth/refresh` | Public | `{refeshToken}` | `AuthResponse` (200) | **Lưu ý chính tả:** field là `refeshToken` (thiếu chữ "r"), không phải `refreshToken` — phải khớp chính xác |
| GET | `/api/v1/categories` | Public | — | `List<CategoryResponse>` | |
| GET | `/api/v1/categories/{id}` | Public | — | `CategoryResponse` | |
| POST | `/api/v1/categories` | ADMIN | `{name, description}` | `CategoryResponse` (201) | |
| PUT | `/api/v1/categories/{id}` | ADMIN | `{name, description}` | `CategoryResponse` | |
| DELETE | `/api/v1/categories/{id}` | ADMIN | — | 204 | |
| GET | `/api/v1/products?categoryId&page&size&sortBy&sortDir` | Public | Query params | `PageResponse<ProductResponse>` | `size` bị cap tối đa 50 ở backend |
| GET | `/api/v1/products/{id}` | Public | — | `ProductResponse` | |
| POST | `/api/v1/products` | ADMIN | `ProductRequest` | `ProductResponse` (201) | |
| PUT | `/api/v1/products/{id}` | ADMIN | `ProductRequest` | `ProductResponse` | |
| DELETE | `/api/v1/products/{id}` | ADMIN | — | 204 | |
| POST | `/api/v1/carts/add` | Authenticated | `{productId, quantity}` | `CartResponse` | |
| GET | `/api/v1/carts` | Authenticated | — | `CartResponse` | |
| DELETE | `/api/v1/carts/{itemId}` | Authenticated | — | `CartResponse` | `itemId` là ID của **CartItem**, không phải productId |
| POST | `/api/v1/checkout` | Authenticated | Header `Idempotency-Key` (bắt buộc) + `{paymentMethod: COD\|MOMO}` | `CheckoutResponse` (201) | Xem chi tiết mục 6 |
| GET | `/api/v1/orders?page&size&sortBy&sortDir` | Authenticated | Query params | `PageResponse<OrderResponse>` | Chỉ trả đơn của chính user |
| PATCH | `/api/v1/orders/{id}/status` | ADMIN | `{orderStatus}` | `OrderResponse` | |
| POST | `/api/v1/payments/momo/ipn` | Public (server-to-server) | `MomoIpnRequest` | 200 | MoMo gọi, **không phải frontend gọi** |
| GET | `/api/v1/payments/momo/return` | Public | Query params (browser redirect) | `{orderId, resultCode, message, success}` | Đây là **momoOrderId (string)**, không phải order.id nội bộ — xem mục 6 |
| GET | `/api/v1/payments/order/{orderId}` | Authenticated | — | `PaymentResponse` | `{orderId}` ở đây là **order.id nội bộ (Long)** |

### 1.2. Authentication & Authorization

- **Cơ chế:** JWT, stateless (`SessionCreationPolicy.STATELESS`), verify qua `JwtAuthenticationFilter`.
- **Access token:** hết hạn sau 15 phút (900000ms).
- **Refresh token:** hết hạn sau 7 ngày (604800000ms). **Không có rotation** — endpoint `/refresh` trả về lại cùng refresh token cũ, chỉ cấp access token mới.
- **Role:** `ADMIN` / `USER`, check bằng `hasAuthority("ADMIN")` (không phải `hasRole()`, nên không có prefix `ROLE_`).
- **Endpoint public:** toàn bộ `/api/v1/auth/**`, GET categories/products, MoMo IPN, MoMo return, Swagger.
- **Endpoint cần ADMIN:** POST/PUT/DELETE categories & products, PATCH order status.
- **⚠️ CORS: Backend hiện KHÔNG có cấu hình CORS nào** (không tìm thấy `CorsConfig` hay `@CrossOrigin`) — nếu chạy React dev server (Vite, port khác) gọi thẳng API, sẽ bị chặn bởi CORS. **Đây là việc backend cần sửa trước khi frontend có thể gọi API từ trình duyệt** — xem mục 14.

### 1.3. Trace các luồng nghiệp vụ

| # | Flow | API cần gọi (theo thứ tự) |
|---|---|---|
| 1 | Register/Login | `POST /auth/register` hoặc `POST /auth/login` → lưu `accessToken` + `refeshToken` |
| 2 | Product browsing | `GET /categories` (để hiện filter) → `GET /products?categoryId&page&size` |
| 3 | Product detail | `GET /products/{id}` |
| 4 | Cart | `POST /carts/add` → `GET /carts` → `DELETE /carts/{itemId}` |
| 5 | Checkout | `POST /checkout` (kèm header `Idempotency-Key` tự sinh, ví dụ UUID) |
| 6 | COD payment | Xử lý ngay trong response của `/checkout` — không cần gọi thêm API nào |
| 7 | MoMo payment | Nhận `paymentUrl` từ `/checkout` → redirect browser sang MoMo → MoMo tự redirect về `/payments/momo/return` |
| 8 | MoMo IPN | **Không phải việc của frontend** — MoMo server gọi thẳng vào backend |
| 9 | Order | `GET /orders` (danh sách), (không có GET order theo id — xem mục 14) |
| 10 | Payment status | `GET /payments/order/{orderId}` để lấy trạng thái thanh toán thật (không tin vào query param của return URL) |
| 11 | Order cancellation | **Backend chưa hỗ trợ** — không có endpoint hủy đơn |
| 12 | Stock handling | COD: trừ stock ngay khi checkout. MoMo: trừ stock khi IPN xác nhận thành công (không trừ lúc checkout) |
| 13 | Idempotency | Bắt buộc gửi header `Idempotency-Key` (frontend tự sinh UUID mỗi lần bấm "Đặt hàng") |
| 14 | Refund/Late payment | Hoàn toàn tự động ở backend (khi IPN trễ hoặc hết hàng) — frontend chỉ cần hiển thị đúng `PaymentStatus` (`REFUNDING`/`REFUNDED`) khi poll |
| 15 | User profile | **Backend chưa có** endpoint xem/sửa thông tin cá nhân — không dựng trang Profile lúc này |
| 16 | Admin flow | CRUD Category, CRUD Product, cập nhật Order status |

---

## 2. CUSTOMER FRONTEND PAGES

| Priority | Page | Route | Backend API | Auth | Status |
|---|---|---|---|---|---|
| P0 | Register | `/register` | `POST /auth/register` | Public | TODO |
| P0 | Login | `/login` | `POST /auth/login` | Public | TODO |
| P0 | Product List | `/products` | `GET /products` | Public | TODO |
| P0 | Product Detail | `/products/:id` | `GET /products/:id` | Public | TODO |
| P0 | Cart | `/cart` | `GET /carts`, `DELETE /carts/:itemId` | Authenticated | TODO |
| P0 | Checkout | `/checkout` | `POST /checkout` | Authenticated | TODO |
| P0 | MoMo Return | `/payments/momo/return` | `GET /payments/momo/return`, `GET /payments/order/:orderId` | Public (nhưng cần đã login để poll payment) | TODO |
| P1 | Order List (My Orders) | `/orders` | `GET /orders` | Authenticated | TODO |
| P2 | Category Filter Sidebar | (component trong `/products`) | `GET /categories` | Public | TODO |
| — | Order Detail | `/orders/:id` | — | — | **Backend chưa có GET order theo id → không dựng trang này lúc này** |
| — | User Profile | `/profile` | — | — | **Backend chưa hỗ trợ → không dựng** |
| — | Order Cancellation | — | — | — | **Backend chưa hỗ trợ → không dựng** |

## Admin Frontend Pages

| Priority | Page | Route | Backend API | Auth | Status |
|---|---|---|---|---|---|
| P0 | Product Management | `/admin/products` | `GET/POST/PUT/DELETE /products` | ADMIN | TODO |
| P0 | Category Management | `/admin/categories` | `GET/POST/PUT/DELETE /categories` | ADMIN | TODO |
| P1 | Order Management | `/admin/orders` | `GET /orders`, `PATCH /orders/:id/status` | ADMIN | TODO — *lưu ý: `GET /orders` hiện chỉ trả đơn của chính user gọi API, cần xác nhận lại với backend xem có filter riêng cho admin xem tất cả đơn hay không trước khi code trang này* |

---

## 3. CHECKLIST COMPONENT

### Layout
- [ ] Navbar (hiện trạng thái login/logout, link Cart, link Orders)
- [ ] Footer
- [ ] ProtectedRoute (chặn route cần login)
- [ ] AdminRoute (chặn route cần role ADMIN — dựa vào `role` trong `AuthResponse`)
- [ ] Loading state
- [ ] Error state / Toast thông báo lỗi

### Product
- [ ] ProductCard
- [ ] ProductGrid
- [ ] ProductDetail
- [ ] CategoryFilter (dropdown/sidebar, dùng `GET /categories`)
- [ ] Pagination (dựa trên `PageResponse`: `pageNo`, `totalPages`, `last`)

### Cart
- [ ] CartItem
- [ ] CartSummary (hiện `totalPrice` trả sẵn từ backend, **không tự tính lại ở frontend**)
- [ ] QuantityControl — **lưu ý:** backend không có API "update quantity", chỉ có add và remove. Muốn đổi số lượng, frontend phải gọi lại `POST /carts/add` (cộng dồn) hoặc `DELETE` rồi add lại — cần xác nhận logic cộng dồn ở `CartService` trước khi code UI tăng/giảm số lượng.

### Checkout
- [ ] PaymentMethodSelector (chỉ 2 lựa chọn: COD / MOMO — enum `PaymentMethod`)
- [ ] OrderSummary
- [ ] CheckoutButton (tự sinh `Idempotency-Key` UUID trước mỗi lần gọi API, tránh double-submit khi user bấm 2 lần)

### Order
- [ ] OrderList
- [ ] OrderStatusBadge (map đúng 6 giá trị enum: `AWAITING_PAYMENT`, `PENDING`, `CONFIRMED`, `SHIPPING`, `DONE`, `CANCELLED`)

### Payment
- [ ] PaymentResultPage (trang `/payments/momo/return`)
- [ ] PaymentStatusBadge (map đúng 5 giá trị enum: `PENDING`, `PAID`, `FAILED`, `REFUNDED`, `REFUNDING`)

*(Không tạo AddressForm — backend không có field địa chỉ giao hàng ở bất kỳ DTO nào, hệ thống hiện chưa hỗ trợ nhập địa chỉ giao hàng.)*

---

## 4. API INTEGRATION LAYER

```
src/
├── api/
│   ├── axiosClient.js
│   ├── authApi.js
│   ├── categoryApi.js
│   ├── productApi.js
│   ├── cartApi.js
│   ├── checkoutApi.js
│   ├── orderApi.js
│   └── paymentApi.js
```
*(Khớp đúng với các file rỗng bạn đã tạo sẵn — không cần thêm/bớt file nào.)*

**authApi.js**
- `register({email, password})` → POST `/auth/register` → Public
- `login({email, password})` → POST `/auth/login` → Public
- `refreshToken({refeshToken})` → POST `/auth/refresh` → Public

**categoryApi.js**
- `getAll()` → GET `/categories` → Public
- `getById(id)` → GET `/categories/{id}` → Public
- `create(data)`, `update(id, data)`, `remove(id)` → ADMIN only

**productApi.js**
- `getAll({categoryId, page, size, sortBy, sortDir})` → GET `/products` → Public
- `getById(id)` → GET `/products/{id}` → Public
- `create(data)`, `update(id, data)`, `remove(id)` → ADMIN only

**cartApi.js**
- `addToCart({productId, quantity})` → POST `/carts/add` → Auth required
- `getCart()` → GET `/carts` → Auth required
- `removeItem(itemId)` → DELETE `/carts/{itemId}` → Auth required

**checkoutApi.js**
- `checkout({paymentMethod}, idempotencyKey)` → POST `/checkout` (header `Idempotency-Key`) → Auth required

**orderApi.js**
- `getMyOrders({page, size, sortBy, sortDir})` → GET `/orders` → Auth required
- `updateStatus(id, {orderStatus})` → PATCH `/orders/{id}/status` → ADMIN only

**paymentApi.js**
- `getMomoReturnResult(queryParams)` → GET `/payments/momo/return` → Public
- `getPaymentByOrderId(orderId)` → GET `/payments/order/{orderId}` → Auth required

---

## 5. AUTHENTICATION FRONTEND

- **Token lưu ở đâu:** khuyến nghị lưu `accessToken` trong bộ nhớ (React state/Context, KHÔNG localStorage) để giảm rủi ro XSS; `refeshToken` có thể lưu ở localStorage tạm thời vì backend hiện chưa hỗ trợ httpOnly cookie — đây là đánh đổi cần chấp nhận vì backend chưa có cơ chế cookie-based refresh.
- **Login flow:** gọi `/auth/login` → lưu `accessToken`, `refeshToken`, `role` vào `AuthContext` → redirect trang chủ.
- **Logout flow:** xóa token khỏi state + localStorage. **Backend không có endpoint `/logout`** (vì JWT stateless, không có token blacklist) — logout chỉ là hành động phía client.
- **Axios interceptor:** gắn `Authorization: Bearer {accessToken}` cho mọi request cần auth.
- **Xử lý token hết hạn:** interceptor response bắt lỗi 401 → gọi `/auth/refresh` bằng `refeshToken` đang có → nếu thành công, retry lại request cũ; nếu thất bại, logout và redirect `/login`.
- **Protected routes:** `ProtectedRoute` kiểm tra có `accessToken` không.
- **Role-based routes:** `AdminRoute` kiểm tra thêm `role === "ADMIN"` lấy từ `AuthResponse`.
- **HTTP 401:** trigger refresh flow ở trên.
- **HTTP 403:** hiện thông báo "Không có quyền truy cập", không tự động logout (403 nghĩa là đã auth nhưng sai role, khác với 401).

---

## 6. CART → CHECKOUT → PAYMENT FLOW

```
User → Cart → Checkout → Create Order → Payment (COD/MoMo) → Payment Result → Order Status
```

### COD
```
Frontend: user chọn COD, bấm "Đặt hàng"
→ POST /checkout { paymentMethod: "COD" }, header Idempotency-Key: <uuid>
→ Response: { orderId, orderStatus: "PENDING", paymentResponse: {paymentStatus: "PENDING", ...}, paymentUrl: null }
→ Frontend: xóa cart khỏi UI state (backend đã xóa ở server), hiện thông báo thành công
→ Redirect: /orders (hoặc trang cảm ơn)
```
**Lưu ý quan trọng:** với COD, backend trừ stock và xóa cart **ngay lập tức** khi gọi `/checkout` — nhưng `PaymentStatus` của đơn COD **vẫn là PENDING mãi mãi**, vì không có endpoint nào cập nhật payment status cho COD (chỉ có `PATCH /orders/{id}/status` cập nhật `OrderStatus`, không đụng vào `PaymentStatus`). Đây là điểm cần lưu ý khi thiết kế UI hiển thị — không nên hiển thị "Đã thanh toán" cho đơn COD dù `OrderStatus` đã là `DONE`.

### MoMo
```
Frontend: user chọn MOMO, bấm "Đặt hàng"
→ POST /checkout { paymentMethod: "MOMO" }, header Idempotency-Key: <uuid>
→ Response: { orderId (Long, nội bộ), orderStatus: "AWAITING_PAYMENT", paymentUrl: "https://..." }
→ Frontend: LƯU LẠI orderId (Long) vào localStorage/state TRƯỚC khi redirect
→ window.location.href = paymentUrl (redirect sang MoMo)
→ [User thanh toán trên MoMo]
→ MoMo redirect browser về: GET /payments/momo/return?orderId=<momoOrderId string>&resultCode=...&signature=...
```

**⚠️ Điểm dễ nhầm lẫn nhất trong toàn bộ flow:** `orderId` trả về ở `/payments/momo/return` là **momoOrderId dạng string** (mã MoMo tự sinh), **KHÔNG PHẢI** `orderId` (Long) nội bộ mà bạn nhận được từ `/checkout` ban đầu. Vì vậy:
1. Frontend **không nên tin trực tiếp** vào query param `orderId` trên URL return để hiển thị kết quả — vì endpoint `/payments/momo/return` **không verify chữ ký** (chỉ verify ở IPN), nên response của nó về lý thuyết có thể bị giả mạo qua URL.
2. Cách làm đúng: dùng lại `orderId` (Long) đã lưu ở bước checkout trước đó (từ localStorage) để gọi `GET /payments/order/{orderId}` — đây là API **có auth + ownership check**, lấy được trạng thái thanh toán đáng tin cậy nhất.
3. `/payments/momo/return` chỉ nên dùng để hiện tạm "đang xử lý" trong lúc chờ, không dùng để quyết định hiển thị "thành công"/"thất bại" cuối cùng.

**Có cần polling không?** Có — vì IPN (nguồn sự thật thật sự) có thể đến **trễ hơn** thời điểm user được redirect về return URL. Khuyến nghị: polling `GET /payments/order/{orderId}` mỗi 2-3 giây, tối đa ~10 lần, cho đến khi `paymentStatus` khác `PENDING`.

**Late payment/refund ảnh hưởng UI thế nào:** nếu polling thấy `paymentStatus` chuyển thành `REFUNDING` hoặc `REFUNDED`, hiển thị thông báo "Đơn hàng đã được hoàn tiền do hết hàng/lỗi xử lý" — đây là trường hợp có thật trong code (`MomoIpnService.handleSuccessfulPayment` khi hết hàng lúc IPN về).

---

## 7. STATE MANAGEMENT

**Đề xuất: React Context, không cần Zustand/Redux Toolkit.**

*Lý do:* Quy mô backend hiện tại nhỏ (7 controller, không có nghiệp vụ real-time phức tạp như chat/notification). Global state cần thiết chỉ gồm 2 nhóm, dùng 2 Context riêng là đủ:

```
AuthContext: { accessToken, refeshToken, user: {id, email, role} }
CartContext: { cart, refetchCart() }
```

`product`, `order`, `payment` **không cần global state** — mỗi trang tự fetch dữ liệu của nó qua React state cục bộ (`useState` + `useEffect`, hoặc custom hook), vì các trang này không cần chia sẻ dữ liệu qua lại giữa nhiều component không liên quan.

---

## 8. ERROR HANDLING

Dựa trên `GlobalExceptionHandler` thực tế:

| Status | Khi nào xảy ra | Frontend xử lý |
|---|---|---|
| 400 | Validation lỗi (`MethodArgumentNotValidException`), out of stock, cart rỗng, idempotency key trùng đang xử lý | Hiện message lỗi từ field `message` (hoặc `messages` object nếu là lỗi validate nhiều field) |
| 401 | Sai email/password, token invalid/expired | Trigger refresh flow; nếu refresh cũng fail → logout |
| 402 | Lỗi từ MoMo payment gateway (`PaymentException`) | Hiện thông báo lỗi thanh toán, cho phép thử lại |
| 403 | Không đủ quyền (vd: gọi admin API mà không phải ADMIN); hoặc xem payment không phải của mình | Hiện "Không có quyền truy cập" |
| 404 | Resource not found (`ResourceNotFoundException`) | Hiện trang/thông báo "Không tìm thấy" |
| 409 | Email đã tồn tại, duplicate resource, **race condition tồn kho (Optimistic Locking)** | Với 409 do optimistic locking: hiện đúng message backend trả "The product is being purchased by another customer at the same time. Please try again!" — mời user thử lại |
| 500 | Lỗi hệ thống chung | Hiện thông báo lỗi chung, không show chi tiết kỹ thuật cho user |

**Backend không có mã lỗi 422.**

Các lỗi nghiệp vụ đặc biệt cần UI xử lý riêng:
- **Out of stock** → 400, message cụ thể tên sản phẩm hết hàng.
- **Concurrent checkout (optimistic locking)** → 409, cần cho phép "Thử lại".
- **Duplicate idempotency key** → 400 "Request is already processing or completed" — nếu user bấm "Đặt hàng" nhiều lần liên tiếp nhanh, hiện thông báo "Đơn hàng đang được xử lý" thay vì lỗi chung chung.
- **Invalid/Expired payment, Cancelled order, Late MoMo payment, Refund** → các trạng thái này **không trả lỗi HTTP**, mà thể hiện qua giá trị `OrderStatus`/`PaymentStatus` khi poll — xử lý bằng cách hiển thị đúng badge trạng thái, không phải bằng try/catch lỗi.

---

## 9. FRONTEND SECURITY CHECKLIST

- [ ] Access token: giữ trong memory (Context), tránh localStorage nếu có thể giảm thiểu rủi ro XSS.
- [ ] XSS: không dùng `dangerouslySetInnerHTML` với dữ liệu từ API (mô tả sản phẩm, v.v.) nếu chưa sanitize.
- [ ] CORS: **cần backend cấu hình trước** (xem mục 14) — frontend không tự sửa được.
- [ ] Protected routes: đã liệt kê ở mục 5.
- [ ] Role-based UI: ẩn hoàn toàn menu/nút Admin nếu `role !== "ADMIN"` — nhưng nhớ đây chỉ là UX, bảo mật thật nằm ở backend (`hasAuthority("ADMIN")`), không dựa vào ẩn UI để bảo mật.
- [ ] Không expose secret: không đặt `MOMO_SECRET_KEY` hay bất kỳ secret nào ở phía frontend — hiện tại đúng là frontend không cần biết secret này (chữ ký được verify hoàn toàn ở backend).
- [ ] API error handling: đã liệt kê ở mục 8.
- [ ] Idempotency-Key: **bắt buộc** phải tự sinh (UUID) ở frontend cho mọi lần gọi `/checkout`, không tái sử dụng key cũ trừ khi cố ý retry đúng request đã gửi.
- [ ] Không trust price/stock từ frontend: đúng, vì `CheckoutRequest` **không hề nhận price/stock từ client** — toàn bộ được backend tự tính từ DB, đây là điểm tốt sẵn có của backend, frontend không cần validate lại giá.

---

## 10. FOLDER STRUCTURE

Cấu trúc hiện tại của bạn đã hợp lý và khớp với độ phức tạp thực tế của backend — **không cần đổi**, chỉ cần bổ sung thêm 1 thư mục còn thiếu:

```
frontend/
├── src/
│   ├── api/            (đã có đủ 7 file — xem mục 4)
│   ├── components/      (đã có Input, Button, Navbar, ProtectedRoute)
│   ├── pages/            ← CẦN TẠO MỚI (hiện chưa có trong scaffold)
│   │   ├── Login.jsx
│   │   ├── Register.jsx
│   │   ├── ProductList.jsx
│   │   ├── ProductDetail.jsx
│   │   ├── Cart.jsx
│   │   ├── Checkout.jsx
│   │   ├── PaymentResult.jsx
│   │   ├── OrderList.jsx
│   │   └── admin/
│   │       ├── ProductManagement.jsx
│   │       ├── CategoryManagement.jsx
│   │       └── OrderManagement.jsx
│   ├── layouts/          (đã có AdminLayout, MainLayout)
│   ├── hooks/            (đã có useCart — cần thêm useAuth nếu tách khỏi Context)
│   ├── context/          (đã có AuthContext — cần thêm CartContext)
│   ├── utils/            (đã có formatCurrency — cần thêm generateIdempotencyKey.js)
│   ├── routes/           (đã có AppRoutes)
│   ├── App.jsx
│   └── main.jsx
├── .env
├── package.json
└── vite.config.js
```

*Không cần thêm `constants/`, `assets/`, hay `stores/` (Zustand/Redux) — quy mô backend không đòi hỏi.*

---

## 11. IMPLEMENTATION ROADMAP

### Phase 1 — Auth + Layout (nền tảng)
- **Mục tiêu:** login/register hoạt động, có thể lưu và refresh token.
- **Files:** `AuthContext.jsx`, `authApi.js`, `axiosClient.js` (interceptor), `Login.jsx`, `Register.jsx`, `ProtectedRoute.jsx`, `Navbar.jsx`.
- **API integrate:** `/auth/register`, `/auth/login`, `/auth/refresh`.
- **Dependencies:** `axios`, `react-router-dom`.
- **Checklist test:** đăng ký thành công → tự động login; đăng nhập sai password → hiện lỗi; access token hết hạn → tự refresh mà không văng user ra.

### Phase 2 — Product Browsing (không cần auth)
- **Mục tiêu:** xem danh sách + chi tiết sản phẩm, filter theo category, phân trang.
- **Files:** `productApi.js`, `categoryApi.js`, `ProductList.jsx`, `ProductDetail.jsx`, `ProductCard.jsx`, `Pagination.jsx`, `CategoryFilter.jsx`.
- **API integrate:** `/products`, `/products/{id}`, `/categories`.
- **Checklist test:** load trang không cần login; filter category đúng; phân trang đúng `totalPages`.

### Phase 3 — Cart
- **Mục tiêu:** thêm/xóa sản phẩm khỏi giỏ, chỉ cho user đã login.
- **Files:** `cartApi.js`, `CartContext.jsx`, `Cart.jsx`, `CartItem.jsx`, `CartSummary.jsx`.
- **API integrate:** `/carts/add`, `/carts`, `/carts/{itemId}`.
- **Checklist test:** thêm sản phẩm khi chưa login → redirect login; xóa item cập nhật đúng `totalPrice`.

### Phase 4 — Checkout + Payment (MVP quan trọng nhất)
- **Mục tiêu:** đặt hàng thành công với cả 2 phương thức COD và MoMo.
- **Files:** `checkoutApi.js`, `paymentApi.js`, `Checkout.jsx`, `PaymentMethodSelector.jsx`, `PaymentResult.jsx`, `generateIdempotencyKey.js`.
- **API integrate:** `/checkout`, `/payments/momo/return`, `/payments/order/{orderId}`.
- **Dependencies:** `uuid` (để sinh Idempotency-Key).
- **Checklist test:** COD tạo đơn thành công + trừ stock; MoMo redirect đúng URL; quay lại từ MoMo hiển thị đúng trạng thái qua polling, không tin trực tiếp query param.

### Phase 5 — Orders (My Orders)
- **Mục tiêu:** xem lịch sử đơn hàng.
- **Files:** `orderApi.js`, `OrderList.jsx`, `OrderStatusBadge.jsx`.
- **API integrate:** `/orders`.
- **Checklist test:** phân trang đúng; badge hiển thị đúng 6 trạng thái.

### Phase 6 — Admin Panel
- **Mục tiêu:** CRUD product/category, cập nhật trạng thái đơn.
- **Files:** `AdminLayout.jsx` (đã có), `ProductManagement.jsx`, `CategoryManagement.jsx`, `OrderManagement.jsx`, `AdminRoute.jsx`.
- **API integrate:** `/products` (POST/PUT/DELETE), `/categories` (POST/PUT/DELETE), `/orders/{id}/status`.
- **Checklist test:** user thường không truy cập được `/admin/**`; CRUD hoạt động đúng; cập nhật status hiện ngay trong UI.

*(Không có Phase riêng cho "polish UI" — nên polish dần trong từng phase, không dồn về cuối, để mỗi phase đều có thể demo được ngay.)*

---

## 12. TESTING CHECKLIST

### Unit Test
- [ ] `formatCurrency.js`
- [ ] `generateIdempotencyKey.js`
- [ ] Logic tính `totalPrice` hiển thị (nếu có tính toán phía client, dù nên ưu tiên dùng số backend trả về)

### Component Test
- [ ] `ProtectedRoute` redirect đúng khi chưa login
- [ ] `AdminRoute` chặn đúng khi không phải ADMIN
- [ ] `OrderStatusBadge` / `PaymentStatusBadge` render đúng màu/label theo từng enum value

### Integration Test
- [ ] Login flow (kể cả trường hợp sai password)
- [ ] Cart flow (add → get → remove)
- [ ] Checkout flow — COD
- [ ] Checkout flow — MoMo (mock `paymentUrl`, không cần gọi MoMo thật)
- [ ] Error handling (400, 401, 403, 404, 409, 500) — mock response từ backend

### E2E
- [ ] Register → Login → Browse → Cart → Checkout (COD) → Order xuất hiện trong `/orders`
- [ ] Register → Login → Browse → Cart → Checkout (MoMo) → Payment Result page → poll đúng trạng thái

*(Không đề xuất test cho Refund/Late payment ở frontend E2E vì toàn bộ logic này nằm ở backend, frontend chỉ hiển thị kết quả — nên test ở tầng backend, không phải frontend.)*

---

## 13. FINAL FRONTEND CHECKLIST

```
## Setup
🔴 React + Vite (đã có sẵn)
🔴 Axios (đã có sẵn axiosClient.js — cần code interceptor)
🔴 React Router (đã có AppRoutes.jsx — cần code routes)
🟠 uuid (cho Idempotency-Key)

## Authentication
🔴 Login
🔴 Register
🔴 Token refresh (interceptor 401)
🔴 Logout (client-side only)
🟠 AdminRoute / role-based routing

## Product
🔴 Product list + pagination
🔴 Product detail
🟠 Category filter

## Cart
🔴 Add to cart
🔴 View cart
🔴 Remove cart item
🟢 Update quantity UI (workaround vì backend không có API riêng)

## Checkout
🔴 Idempotency-Key generation
🔴 COD flow
🔴 MoMo flow (redirect + return page)
🔴 Poll payment status sau MoMo return

## Order
🔴 My Orders list
🟠 Order status badge (6 giá trị)
🟠 Payment status badge (5 giá trị)

## Admin
🟠 Product CRUD
🟠 Category CRUD
🟠 Order status update

## Error handling
🔴 400/401/403/404/409/500 mapping
🔴 Optimistic locking conflict (409) retry UX

## Testing
🟠 Integration test cho checkout flow (COD + MoMo)
🟢 Unit test cho utils

## Production
🔴 CORS phải được backend cấu hình trước deploy (xem mục 14)
🟠 .env cho API base URL
```

---

## 14. ĐÁNH GIÁ MỨC ĐỘ HOÀN THIỆN CỦA BACKEND

### Backend readiness for frontend: **6.5/10**

| Tiêu chí | Đánh giá |
|---|---|
| API completeness | Đủ cho luồng mua hàng cốt lõi (browse → cart → checkout → order), nhưng thiếu: xem chi tiết 1 đơn hàng theo id, hủy đơn, sửa thông tin cá nhân |
| Authentication | Tốt — JWT + refresh hoạt động rõ ràng, có validate đầy đủ |
| E-commerce flow | Vững — đặc biệt xử lý concurrency (optimistic locking) và MoMo late-IPN/refund tự động là điểm rất mạnh, hiếm gặp ở project sinh viên |
| Error handling | Khá đầy đủ, message rõ ràng, nhưng response error hiện dùng `Map<String,Object>` tự build thay vì DTO cố định — frontend cần code theo đúng field name (`message`, `error`, `status`) một cách thủ công vì không có type an toàn từ OpenAPI |
| Payment | Chắc chắn về mặt logic (idempotency, signature verify, refund tự động) nhưng **luồng xác nhận thanh toán COD bị thiếu** (payment status COD kẹt ở PENDING vĩnh viễn) |
| Order management | Thiếu GET order theo id, thiếu hủy đơn |
| Security | Tốt (JWT, ownership check ở payment) nhưng **CORS chưa cấu hình** — chặn cứng việc gọi từ browser |
| API consistency | Khá tốt, đặt tên endpoint theo chuẩn REST, nhưng có 1 lỗi chính tả field (`refeshToken`) lặp lại xuyên suốt — frontend phải theo đúng lỗi chính tả này |
| Documentation | Có Swagger UI (`/swagger-ui/**` public) — tốt, nên dùng để double-check khi code |
| Frontend readiness | Đủ để bắt đầu code Phase 1–5 ngay; Phase 6 (Admin Order Management) cần xác nhận thêm 1 điểm (xem dưới) |

### **"Nếu tôi bắt đầu code React ngay bây giờ, backend đã đủ ổn chưa?"**

**Đủ để bắt đầu, nhưng có 2 việc bắt buộc phải xử lý trước khi test được trên trình duyệt thật:**

1. **CORS chưa được cấu hình ở backend.** Đây là việc phải làm ở phía backend (thêm `CorsConfigurationSource` bean hoặc `@CrossOrigin`), không phải phía frontend — nếu không sửa, mọi request từ Vite dev server sẽ bị trình duyệt chặn ngay từ đầu, kể cả code frontend đúng 100%.
2. **COD payment status không bao giờ được cập nhật thành PAID.** Cần xác nhận với backend: đây là thiết kế cố ý (COD = thu tiền mặt khi giao, nên "PAID" sẽ được set bằng cách khác ngoài hệ thống này), hay là một tính năng còn thiếu (cần thêm 1 field/endpoint để đánh dấu COD đã thu tiền). Nếu không làm rõ trước, phần UI hiển thị trạng thái thanh toán cho đơn COD sẽ dễ hiển thị sai.

Ngoài 2 điểm trên, không có gì chặn việc bắt đầu code Phase 1–5 ngay hôm nay.
