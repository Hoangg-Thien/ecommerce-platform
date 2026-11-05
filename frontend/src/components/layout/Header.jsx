import { ShoppingCart, Search, Menu, User, X, Loader2, ArrowRight } from 'lucide-react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useEffect, useState, useRef } from 'react';
import './Header.css';
import { useAuth } from '../../context/AuthContext';
import { useCart } from '../../context/CartContext';
import ConfirmModal from '../ui/ConfirmModal';
import categoryApi from '../../api/categoryApi';
import productApi from '../../api/productApi';

export default function Header() {
  const { user, logout } = useAuth();
  const { cart } = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [categories, setCategories] = useState([]);
  const [keyword, setKeyword] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [totalMatches, setTotalMatches] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const searchWrapperRef = useRef(null);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const q = params.get('q') || '';
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setKeyword(q);
  }, [location.search]);

  // Đóng dropdown khi chuyển trang
  useEffect(() => {
    setShowDropdown(false);
  }, [location.pathname, location.search]);

  // Đóng dropdown khi click ra ngoài
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (searchWrapperRef.current && !searchWrapperRef.current.contains(e.target)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Debounce tìm kiếm gợi ý khi gõ từ khóa (>= 2 ký tự)
  useEffect(() => {
    const trimmed = keyword.trim();
    if (trimmed.length < 2) {
      setSuggestions([]);
      setTotalMatches(0);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    const timer = setTimeout(async () => {
      try {
        const data = await productApi.getProducts({ q: trimmed, size: 5, page: 0 });
        setSuggestions(data.content || []);
        setTotalMatches(data.totalElements || 0);
        setShowDropdown(true);
      } catch (error) {
        console.error('Lỗi khi tải gợi ý tìm kiếm', error);
        setSuggestions([]);
        setTotalMatches(0);
      } finally {
        setIsLoading(false);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [keyword]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setShowDropdown(false);
    const trimmed = keyword.trim();
    if (!trimmed) {
      navigate('/');
    } else if (trimmed.length === 1) {
      // đúng 1 ký tự -> không làm gì
      return;
    } else {
      navigate('/?q=' + encodeURIComponent(trimmed));
    }
  };

  const handleSelectProduct = (productId) => {
    setShowDropdown(false);
    navigate(`/product/${productId}`);
  };

  const handleViewAllResults = () => {
    setShowDropdown(false);
    const trimmed = keyword.trim();
    if (trimmed) {
      navigate('/?q=' + encodeURIComponent(trimmed));
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Escape') {
      setShowDropdown(false);
    }
  };

  useEffect(() => {
    const fetchCategories = async () => {
      try {
        const data = await categoryApi.getAllCategories();
        setCategories([{ id: '', name: 'Tất cả sản phẩm'}, ...data]);
      } catch (error) {
        console.error('Lỗi khi tải danh mục', error);
      }
    };
    fetchCategories();
  }, []);

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
            {categories.map(category => (
              <Link 
                key={category.id || 'all'}
                to={category.id ? `/?category=${category.id}` : '/'} 
                className="nav-link" 
                onClick={() => setIsMobileMenuOpen(false)}
              >
                {category.name}
              </Link>
            ))}
          </nav>

          <div className="header-right">
            <div className="search-wrapper" ref={searchWrapperRef}>
              <form onSubmit={handleSearchSubmit} className="search-box">
                <button
                  type="submit"
                  aria-label="Tìm kiếm"
                  style={{ background: 'none', border: 'none', padding: 0, display: 'flex', alignItems: 'center', cursor: 'pointer', color: 'inherit' }}
                >
                  <Search size={18} className="search-icon" />
                </button>
                <input
                  type="text"
                  placeholder="Tìm kiếm..."
                  className="search-input"
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  onFocus={() => {
                    if (keyword.trim().length >= 2 && (suggestions.length > 0 || totalMatches > 0 || isLoading)) {
                      setShowDropdown(true);
                    }
                  }}
                  onKeyDown={handleKeyDown}
                  maxLength={100}
                  aria-label="Tìm kiếm sản phẩm"
                />
                {isLoading && <Loader2 size={15} className="search-spinner" />}
                {keyword && !isLoading && (
                  <button
                    type="button"
                    className="search-clear-btn"
                    onClick={() => {
                      setKeyword('');
                      setSuggestions([]);
                      setTotalMatches(0);
                      setShowDropdown(false);
                    }}
                    aria-label="Xóa từ khóa"
                  >
                    <X size={14} />
                  </button>
                )}
              </form>

              {showDropdown && keyword.trim().length >= 2 && (
                <div className="search-dropdown">
                  {isLoading && suggestions.length === 0 ? (
                    <div className="search-dropdown-loading">
                      <Loader2 size={16} className="search-spinner" />
                      <span>Đang tìm kiếm...</span>
                    </div>
                  ) : suggestions.length === 0 ? (
                    <div className="search-dropdown-empty">
                      Không tìm thấy sản phẩm phù hợp
                    </div>
                  ) : (
                    <>
                      <div className="search-dropdown-header">
                        Sản phẩm gợi ý
                      </div>
                      <div className="search-dropdown-list">
                        {suggestions.map((item) => {
                          const displayPrice = new Intl.NumberFormat('vi-VN', {
                            style: 'currency',
                            currency: 'VND'
                          }).format(item.price);
                          const thumb = item.imageUrl || item.image || 'https://placehold.co/48x48?text=XOAI';

                          return (
                            <div
                              key={item.id}
                              className="search-dropdown-item"
                              onClick={() => handleSelectProduct(item.id)}
                            >
                              <img
                                src={thumb}
                                alt={item.name}
                                className="search-dropdown-thumb"
                                onError={(e) => {
                                  e.target.onerror = null;
                                  e.target.src = 'https://placehold.co/48x48?text=XOAI';
                                }}
                              />
                              <div className="search-dropdown-info">
                                <div className="search-dropdown-name" title={item.name}>
                                  {item.name}
                                </div>
                                <div className="search-dropdown-price">
                                  {displayPrice}
                                </div>
                              </div>
                            </div>
                          );
                        })}
                      </div>

                      {totalMatches > 5 ? (
                        <div
                          className="search-dropdown-footer"
                          onClick={handleViewAllResults}
                        >
                          <span>Hiển thị thêm <strong>{totalMatches - 5}</strong> sản phẩm</span>
                          <ArrowRight size={14} />
                        </div>
                      ) : (
                        <div
                          className="search-dropdown-footer"
                          onClick={handleViewAllResults}
                        >
                          <span>Xem tất cả kết quả cho "<strong>{keyword.trim()}</strong>"</span>
                          <ArrowRight size={14} />
                        </div>
                      )}
                    </>
                  )}
                </div>
              )}
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
