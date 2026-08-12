import { useState } from 'react';
import { Link } from 'react-router-dom';
import AuthLayout from '../../components/auth/AuthLayout';
import Input from '../../components/ui/Input';
import PasswordInput from '../../components/auth/PasswordInput';
import Button from '../../components/ui/Button';
import SocialButton from '../../components/auth/SocialButton';
import '../../components/auth/Auth.css';

export default function Login() {
  const [formData, setFormData] = useState({
    email: '',
    password: ''
  });
  const [errors, setErrors] = useState({});
  const [isLoading, setIsLoading] = useState(false);

  const validate = () => {
    const newErrors = {};
    if (!formData.email) {
      newErrors.email = 'Vui lòng nhập Email';
    } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
      newErrors.email = 'Email không hợp lệ';
    }
    if (!formData.password) {
      newErrors.password = 'Vui lòng nhập mật khẩu';
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    // Clear error when user starts typing
    if (errors[name]) {
      setErrors((prev) => ({ ...prev, [name]: '' }));
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (validate()) {
      setIsLoading(true);
      // Simulate API call
      setTimeout(() => {
        setIsLoading(false);
        alert('Đăng nhập thành công (Giả lập)');
      }, 1500);
    }
  };

  const handleGoogleLogin = () => {
    alert('Giả lập đăng nhập bằng Google');
  };

  return (
    <AuthLayout>
      <div className="auth-header">
        <h1 className="auth-title">Chào mừng trở lại</h1>
        <p className="auth-subtitle">Đăng nhập vào tài khoản của bạn để tiếp tục.</p>
      </div>

      <form className="auth-form" onSubmit={handleSubmit}>
        <div className="auth-form-fields">
          <Input
            label="Email"
            name="email"
            type="email"
            placeholder="ban@example.com"
            value={formData.email}
            onChange={handleChange}
            error={errors.email}
            disabled={isLoading}
          />
          
          <PasswordInput
            label="Mật khẩu"
            name="password"
            placeholder="Nhập mật khẩu của bạn"
            value={formData.password}
            onChange={handleChange}
            error={errors.password}
            disabled={isLoading}
          />
        </div>

        <div className="auth-form-footer">
          <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--text-body-md)' }}>
            <input type="checkbox" disabled={isLoading} /> Ghi nhớ đăng nhập
          </label>
          <Link to="#" className="auth-link">Quên mật khẩu?</Link>
        </div>

        <Button type="submit" variant="primary" isLoading={isLoading}>
          Đăng nhập
        </Button>
      </form>

      <div className="auth-divider">Hoặc</div>

      <SocialButton provider="google" onClick={handleGoogleLogin} disabled={isLoading} />

      <div className="auth-redirect">
        Chưa có tài khoản? <Link to="/register" className="auth-link">Đăng ký ngay</Link>
      </div>
    </AuthLayout>
  );
}
