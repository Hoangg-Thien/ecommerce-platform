import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const Navbar = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav style={{ display: 'flex', gap: '20px', padding: '10px' }}>
      <Link to="/">Trang chủ</Link>
      <Link to="/products">Sản phẩm</Link>
      
      {user ? (
        <>
          <Link to="/cart">Giỏ hàng</Link>
          <Link to="/orders">Đơn hàng của tôi</Link>
          {user.role === 'ADMIN' && <Link to="/admin">Quản trị</Link>}
          
          <span>Xin chào, {user.email}!</span>
          <button onClick={handleLogout}>Đăng xuất</button>
        </>
      ) : (
        <>
          <Link to="/login">Đăng nhập</Link>
          <Link to="/register">Đăng ký</Link>
        </>
      )}
    </nav>
  );
};

export default Navbar;
