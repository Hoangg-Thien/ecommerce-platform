import { ShoppingCart, Search, Menu, User } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import './Header.css';
import { useAuth } from '../../context/AuthContext';
import { useCart } from '../../context/CartContext';
import ConfirmModal from '../ui/ConfirmModal';

export default function Header() {
  const { user, logout } = useAuth();
  const { cart } = useCart();
  const navigate = useNavigate();
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  const confirmLogout = () => {
    logout();
    setShowLogoutConfirm(false);
    navigate('/');
  };

  const handleLogoutClick = () => {
    setShowLogoutConfirm(true);
  };

  const toggleMobileMenu = () => {
    setIsMobileMenuOpen(!isMobileMenuOpen);
  };

  const cartItemCount = cart?.items?.length || 0;

  return (
    <>
      <header className="site-header">
        <div className="header-container">
          <div className="header-left">
            <button className="icon-btn mobile-menu-btn" aria-label="Menu" onClick={toggleMobileMenu}>
              <Menu size={24} />
            </button>
            <Link to="/" className="brand-logo">
              XOÀI
            </Link>
          </div>

          <nav className={`desktop-nav ${isMobileMenuOpen ? 'mobile-open' : ''}`}>
            <Link to="/" className="nav-link active" onClick={() => setIsMobileMenuOpen(false)}>Sản phẩm</Link>
            <Link to="#" className="nav-link" onClick={() => setIsMobileMenuOpen(false)}>Về chúng tôi</Link>
          </nav>

          <div className="header-right">
            <div className="search-box">
              <Search size={18} className="search-icon" />
              <input type="text" placeholder="Tìm kiếm..." className="search-input" />
            </div>

            <Link to="/cart" className="icon-btn cart-btn" aria-label="Giỏ hàng">
              <ShoppingCart size={22} />
              {cartItemCount > 0 && (
                <span className="cart-badge">{cartItemCount}</span>
              )}
            </Link>

            <div className="account-menu-wrapper">
              <div className="icon-btn account-btn" aria-label="Tài khoản" style={{ cursor: 'default' }}>
                <User size={22} />
              </div>
              <div className="account-dropdown">
                {user ? (
                  <>
                    <div className="account-dropdown-header" style={{ padding: '6px 16px', borderBottom: '1px solid #eee', fontSize: '13px', color: '#666', marginBottom: '4px' }}>
                      Xin chào,<br/><strong style={{color: '#000', fontSize: '14px', display: 'block', marginTop: '2px', wordBreak: 'break-all'}}>{user.email}</strong>
                    </div>
                    <Link to="/orders" className="account-dropdown-item">Đơn hàng của tôi</Link>
                    {user.role === 'ADMIN' && (
                      <Link to="/admin" className="account-dropdown-item">Quản trị viên</Link>
                    )}
                    <div onClick={handleLogoutClick} className="account-dropdown-item" style={{ cursor: 'pointer', color: '#e74c3c' }}>
                      Đăng xuất
                    </div>
                  </>
                ) : (
                  <>
                    <Link to="/login" className="account-dropdown-item">Đăng nhập</Link>
                    <Link to="/register" className="account-dropdown-item">Đăng ký</Link>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      </header>
      
      <ConfirmModal 
        isOpen={showLogoutConfirm}
        title="Xác nhận đăng xuất"
        message="Bạn có chắc chắn muốn đăng xuất không?"
        confirmText="Đăng xuất"
        onConfirm={confirmLogout}
        onCancel={() => setShowLogoutConfirm(false)}
        isDanger={true}
      />
    </>
  );
}
