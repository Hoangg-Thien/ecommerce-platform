import { useState } from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import {
  LayoutDashboard,
  Package,
  Tag,
  ShoppingCart,
  Users,
  LogOut,
  Menu,
  X,
  ChevronRight,
  Store,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import ConfirmModal from '../ui/ConfirmModal';
import './AdminLayout.css';

// ---- Menu Definition ----
const NAV_ITEMS = [
  {
    section: 'Tổng quan',
    items: [
      { label: 'Dashboard', icon: LayoutDashboard, to: '/admin/dashboard' },
    ],
  },
  {
    section: 'Danh mục',
    items: [
      { label: 'Sản phẩm', icon: Package, to: '/admin/products' },
      { label: 'Danh mục', icon: Tag, to: '/admin/categories' },
    ],
  },
  {
    section: 'Vận hành',
    items: [
      { label: 'Đơn hàng', icon: ShoppingCart, to: '/admin/orders' },
      { label: 'Khách hàng', icon: Users, to: '/admin/users' },
    ],
  },
];

// ---- Breadcrumb helper ----
function useBreadcrumb() {
  const { pathname } = useLocation();
  const crumbs = [{ label: 'Admin', to: '/admin/dashboard' }];
  if (pathname === '/admin/dashboard') crumbs.push({ label: 'Dashboard' });
  if (pathname.includes('/products/new')) crumbs.push({ label: 'Sản phẩm', to: '/admin/products' }, { label: 'Thêm mới' });
  else if (pathname.match(/\/products\/\d+/)) crumbs.push({ label: 'Sản phẩm', to: '/admin/products' }, { label: 'Chỉnh sửa' });
  else if (pathname.includes('/products')) crumbs.push({ label: 'Sản phẩm' });
  else if (pathname.includes('/categories')) crumbs.push({ label: 'Danh mục' });
  else if (pathname.includes('/orders')) crumbs.push({ label: 'Đơn hàng' });
  else if (pathname.includes('/users')) crumbs.push({ label: 'Khách hàng' });
  return crumbs;
}

export default function AdminLayout({ children }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const { user, logout } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const crumbs = useBreadcrumb();

  const handleLogout = async () => {
    setShowLogoutConfirm(false);
    await logout();
    showToast('Đã đăng xuất thành công', 'success');
    navigate('/admin');
  };

  const closeMobile = () => setMobileOpen(false);

  // Get initials for avatar
  const initials = user?.email?.slice(0, 2).toUpperCase() || 'AD';

  return (
    <div className="admin-shell">
      {/* ====== SIDEBAR ====== */}
      <aside className={`admin-sidebar ${mobileOpen ? 'mobile-open' : ''}`}>
        {/* Logo */}
        <NavLink to="/admin/dashboard" className="admin-sidebar__logo" onClick={closeMobile}>
          <div className="admin-sidebar__logo-icon">
            <Store size={22} color="#fff" strokeWidth={2.4} />
          </div>
        </NavLink>

        {/* Navigation */}
        <nav className="admin-sidebar__nav">
          {NAV_ITEMS.map((group) => (
            <div key={group.section}>
              <div className="admin-sidebar__section-label">{group.section}</div>
              {group.items.map((item) => {
                const Icon = item.icon;
                if (item.disabled) {
                  return (
                    <div key={item.to} className="admin-sidebar__item" style={{ opacity: 0.45, cursor: 'not-allowed' }} title={item.label}>
                      <Icon className="admin-sidebar__item-icon" size={18} />
                      <span>{item.label}</span>
                      {item.badge && <span className="admin-sidebar__item-badge">{item.badge}</span>}
                    </div>
                  );
                }
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.exact}
                    className={({ isActive }) =>
                      `admin-sidebar__item${isActive ? ' active' : ''}`
                    }
                    onClick={closeMobile}
                  >
                    <Icon className="admin-sidebar__item-icon" size={18} />
                    <span>{item.label}</span>
                  </NavLink>
                );
              })}
              <div className="admin-sidebar__divider" />
            </div>
          ))}
        </nav>

        {/* Footer — User info */}
        <div className="admin-sidebar__footer">
          <div className="admin-sidebar__user" title={user?.email}>
            <div className="admin-sidebar__avatar">{initials}</div>
            <div className="admin-sidebar__user-info">
              <div className="admin-sidebar__user-name">{user?.email}</div>
              <div className="admin-sidebar__user-role">ADMIN</div>
            </div>
          </div>
        </div>
      </aside>

      {/* Mobile overlay */}
      {mobileOpen && (
        <div className="admin-overlay" onClick={closeMobile} />
      )}

      {/* ====== HEADER ====== */}
      <header className="admin-header">
        {/* Mobile hamburger */}
        <button
          className="admin-header__icon-btn"
          onClick={() => setMobileOpen((v) => !v)}
          aria-label="Toggle sidebar"
          style={{ display: 'none' }}
          id="admin-hamburger"
        >
          {mobileOpen ? <X size={18} /> : <Menu size={18} />}
        </button>

        {/* Breadcrumb */}
        <nav className="admin-header__breadcrumb" aria-label="Breadcrumb">
          {crumbs.map((c, i) => (
            <span key={i} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              {i > 0 && <ChevronRight size={14} className="admin-header__breadcrumb-sep" />}
              <span className={`admin-header__breadcrumb-item${i === crumbs.length - 1 ? ' current' : ''}`}>
                {c.label}
              </span>
            </span>
          ))}
        </nav>

        {/* Actions */}
        <div className="admin-header__actions">
          <button
            className="admin-header__logout-btn"
            onClick={() => setShowLogoutConfirm(true)}
            aria-label="Đăng xuất"
            id="admin-logout-btn"
          >
            <LogOut size={15} />
            <span>Đăng xuất</span>
          </button>
        </div>
      </header>

      {/* ====== MAIN CONTENT ====== */}
      <main className="admin-main">
        {children}
      </main>

      {/* ====== LOGOUT CONFIRM MODAL ====== */}
      <ConfirmModal
        isOpen={showLogoutConfirm}
        title="Xác nhận đăng xuất"
        message="Bạn có chắc chắn muốn đăng xuất không?"
        confirmText="Đăng xuất"
        cancelText="Hủy"
        isDanger={true}
        onConfirm={handleLogout}
        onCancel={() => setShowLogoutConfirm(false)}
      />
    </div>
  );
}
