import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/auth/Login';
import Register from './pages/auth/Register';
import ProductList from './pages/product-list/ProductList';
import ProductDetail from './pages/product-detail/ProductDetail';
import Cart from './pages/cart/Cart';
import Checkout from './pages/checkout/Checkout';
import MockPayment from './pages/checkout/MockPayment';
import PaymentResult from './pages/payment-result/PaymentResult';
import OrderList from './pages/order-list/OrderList';
import { AuthProvider } from './context/AuthContext';
import { CartProvider } from './context/CartContext';
import { ToastProvider } from './context/ToastContext';
import ProtectedRoute from './components/ProtectedRoute';

// Admin
import AdminLayout from './components/admin/AdminLayout';
import AdminLogin from './pages/admin/auth/AdminLogin';
import CategoryManagement from './pages/admin/category/CategoryManagement';
import ProductManagement from './pages/admin/product/ProductManagement';
import ProductForm from './pages/admin/product/ProductForm';
import OrderManagement from './pages/admin/order/OrderManagement';
import UserManagement from './pages/admin/user/UserManagement';
import Dashboard from './pages/admin/dashboard/Dashboard';

function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <CartProvider>
          <BrowserRouter>
            <Routes>
              {/* ---- Public / Customer Routes ---- */}
              <Route path="/" element={<ProductList />} />
              <Route path="/product/:id" element={<ProductDetail />} />
              <Route path="/cart" element={<Cart />} />
              <Route path="/checkout" element={<Checkout />} />
              <Route path="/mock-payment" element={<MockPayment />} />
              <Route path="/payment-result" element={<PaymentResult />} />
              <Route path="/orders" element={<OrderList />} />
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />

              {/* /admin và /admin/login → Trang đăng nhập admin riêng */}
              <Route path="/admin" element={<AdminLogin />} />
              <Route path="/admin/login" element={<AdminLogin />} />
              <Route
                path="/admin/dashboard"
                element={
                  <ProtectedRoute requireAdmin>
                    <AdminLayout>
                      <Dashboard />
                    </AdminLayout>
                  </ProtectedRoute>
                }
              />

              {/* Các trang quản trị → cần ADMIN role */}
              <Route
                path="/admin/products"
                element={
                  <ProtectedRoute requireAdmin>
                    <AdminLayout>
                      <ProductManagement />
                    </AdminLayout>
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/products/new"
                element={
                  <ProtectedRoute requireAdmin>
                    <AdminLayout>
                      <ProductForm />
                    </AdminLayout>
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/products/:id"
                element={
                  <ProtectedRoute requireAdmin>
                    <AdminLayout>
                      <ProductForm />
                    </AdminLayout>
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/categories"
                element={
                  <ProtectedRoute requireAdmin>
                    <AdminLayout>
                      <CategoryManagement />
                    </AdminLayout>
                  </ProtectedRoute>
                }
              />

              <Route
                path="/admin/orders"
                element={
                  <ProtectedRoute requireAdmin>
                    <AdminLayout>
                      <OrderManagement />
                    </AdminLayout>
                  </ProtectedRoute>
                }
              />

              <Route
                path="/admin/users"
                element={
                  <ProtectedRoute requireAdmin>
                    <AdminLayout>
                      <UserManagement />
                    </AdminLayout>
                  </ProtectedRoute>
                }
              />

              {/* Catch-all */}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </CartProvider>
      </AuthProvider>
    </ToastProvider>
  );
}

export default App;
