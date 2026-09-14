import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const ProtectedRoute = ({ children, requireAdmin = false }) => {
  const { user } = useAuth();
  const location = useLocation();

  // Chưa đăng nhập
  if (!user) {
    // Nếu đang cố vào trang admin → đá về admin login
    if (requireAdmin) {
      return <Navigate to="/admin" state={{ from: location }} replace />;
    }
    // Trang thường → đá về user login
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // Cần quyền admin nhưng không phải admin → đá về trang chủ user
  if (requireAdmin && user.role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }

  return children;
};

export default ProtectedRoute;
