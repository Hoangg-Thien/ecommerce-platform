import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const ProtectedRoute = ({ children, requireAdmin = false }) => {
  const { user } = useAuth();
  const location = useLocation();

  // Chưa đăng nhập -> đá về Login, lưu lại trang đang muốn vào để chuyển về sau khi login
  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // Cần quyền admin nhưng không phải admin -> báo lỗi 403 / đá về trang chủ
  if (requireAdmin && user.role !== 'ADMIN') {
    return <Navigate to="/" replace />; // Hoặc chuyển đến một trang báo lỗi 403
  }

  return children;
};

export default ProtectedRoute;
