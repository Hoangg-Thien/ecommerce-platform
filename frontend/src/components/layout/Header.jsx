import { ShoppingCart, Search, Menu, User } from 'lucide-react';
import { Link } from 'react-router-dom';
import './Header.css';

export default function Header() {
  return (
    <header className="site-header">
      <div className="header-container">
        <div className="header-left">
          <button className="icon-btn mobile-menu-btn" aria-label="Menu">
            <Menu size={24} />
          </button>
          <Link to="/" className="brand-logo">
            KHO.
          </Link>
        </div>

        <nav className="desktop-nav">
          <Link to="/" className="nav-link active">Sản phẩm</Link>
          <Link to="#" className="nav-link">Khuyến mãi</Link>
          <Link to="#" className="nav-link">Về chúng tôi</Link>
        </nav>

        <div className="header-right">
          <div className="search-box">
            <Search size={18} className="search-icon" />
            <input type="text" placeholder="Tìm kiếm..." className="search-input" />
          </div>

          <Link to="/cart" className="icon-btn cart-btn" aria-label="Giỏ hàng">
            <ShoppingCart size={22} />
            <span className="cart-badge">3</span>
          </Link>

          <div className="account-menu-wrapper">
            <div className="icon-btn account-btn" aria-label="Tài khoản" style={{ cursor: 'default' }}>
              <User size={22} />
            </div>
            <div className="account-dropdown">
              <Link to="/login" className="account-dropdown-item">Đăng nhập</Link>
              <Link to="/register" className="account-dropdown-item">Đăng ký</Link>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}
