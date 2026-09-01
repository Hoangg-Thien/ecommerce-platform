import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Eye, EyeOff, Store, AlertCircle } from 'lucide-react';
import { useAuth } from '../../../context/AuthContext';
import { useToast } from '../../../context/ToastContext';
import './AdminLogin.css';

export default function AdminLogin() {
  const navigate = useNavigate();
  const { user, login } = useAuth();
  const { showToast } = useToast();

  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [apiError, setApiError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  // Nếu đã đăng nhập với role ADMIN → thẳng vào admin panel
  useEffect(() => {
    if (user?.role === 'ADMIN') {
      navigate('/admin/dashboard', { replace: true });
    }
  }, [user, navigate]);

  // ---- Validation ----
  const validate = () => {
    const errs = {};
    if (!form.email.trim()) errs.email = 'Vui lòng nhập email';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) errs.email = 'Email không hợp lệ';
    if (!form.password) errs.password = 'Vui lòng nhập mật khẩu';
    return errs;
  };

  // ---- Submit ----
  const handleSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    const errs = validate();
    if (Object.keys(errs).length > 0) {
      setErrors(errs);
      return;
    }

    setIsLoading(true);
    try {
      await login({ email: form.email.trim(), password: form.password });

      // login() cập nhật user trong AuthContext → useEffect phía trên sẽ redirect
      // nhưng ta cũng kiểm tra role trực tiếp từ localStorage cho chắc
      const stored = localStorage.getItem('user');
      const userData = stored ? JSON.parse(stored) : null;

      if (userData?.role !== 'ADMIN') {
        // Đăng nhập thành công nhưng không phải ADMIN
        setApiError('Tài khoản của bạn không có quyền truy cập trang quản trị.');
        // Logout để tránh state lộn xộn
        setIsLoading(false);
        return;
      }

      showToast('Đăng nhập thành công!', 'success');
      navigate('/admin/products', { replace: true });
    } catch (err) {
      const status = err?.response?.status;
      if (status === 401 || status === 403) {
        setApiError('Email hoặc mật khẩu không đúng. Vui lòng thử lại.');
      } else {
        setApiError('Không thể kết nối đến máy chủ. Vui lòng thử lại sau.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleFieldChange = (field, value) => {
    setForm((f) => ({ ...f, [field]: value }));
    if (errors[field]) setErrors((e) => ({ ...e, [field]: '' }));
    if (apiError) setApiError('');
  };

  return (
    <div className="admin-login-page">
      <div className="admin-login__container">

        {/* Main Card */}
        <div className="admin-login__card">
          <div className="admin-login__header">
            <h2 className="admin-login__heading">Đăng nhập Admin</h2>
            <p className="admin-login__subheading">
              Đăng nhập để truy cập hệ thống quản trị
            </p>
          </div>

          {/* API Error */}
          {apiError && (
            <div className="admin-login__error" role="alert">
              <AlertCircle size={16} style={{ flexShrink: 0, marginTop: 1 }} />
              <span>{apiError}</span>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit} noValidate>
            {/* Email */}
            <div className="admin-login__field">
              <label className="admin-login__label" htmlFor="admin-email">
                Email quản trị
              </label>
              <div className="admin-login__input-wrap">
                <input
                  id="admin-email"
                  className={`admin-login__input ${errors.email ? 'error' : ''}`}
                  type="email"
                  placeholder="admin@example.com"
                  value={form.email}
                  onChange={(e) => handleFieldChange('email', e.target.value)}
                  autoComplete="email"
                  autoFocus
                />
              </div>
              {errors.email && (
                <div className="admin-login__field-error">{errors.email}</div>
              )}
            </div>

            {/* Password */}
            <div className="admin-login__field">
              <label className="admin-login__label" htmlFor="admin-password">
                Mật khẩu
              </label>
              <div className="admin-login__input-wrap">
                <input
                  id="admin-password"
                  className={`admin-login__input admin-login__input--password ${errors.password ? 'error' : ''}`}
                  type={showPassword ? 'text' : 'password'}
                  placeholder="••••••••"
                  value={form.password}
                  onChange={(e) => handleFieldChange('password', e.target.value)}
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  className="admin-login__eye-btn"
                  onClick={() => setShowPassword((v) => !v)}
                  aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {errors.password && (
                <div className="admin-login__field-error">{errors.password}</div>
              )}
            </div>

            {/* Submit */}
            <button
              id="admin-login-submit"
              type="submit"
              className="admin-login__submit-btn"
              disabled={isLoading}
            >
              {isLoading ? 'Đang đăng nhập...' : 'Đăng nhập'}
            </button>
          </form>

          {/* Footer */}
          <p className="admin-login__footer-text">
            Bạn là khách hàng?{' '}
            <Link to="/" className="admin-login__footer-link">
              Về trang mua sắm
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
