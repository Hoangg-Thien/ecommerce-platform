import { useState } from 'react';
import { Link } from 'react-router-dom';
import AuthLayout from '../../components/auth/AuthLayout';
import Input from '../../components/ui/Input';
import PasswordInput from '../../components/auth/PasswordInput';
import Button from '../../components/ui/Button';
import SocialButton from '../../components/auth/SocialButton';
import '../../components/auth/Auth.css';

export default function Register() {
  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    password: '',
    confirmPassword: ''
  });
  const [errors, setErrors] = useState({});
  const [isLoading, setIsLoading] = useState(false);

  const validate = () => {
    const newErrors = {};
    if (!formData.fullName) {
      newErrors.fullName = 'Vui lòng nhập họ và tên';
    }
    if (!formData.email) {
      newErrors.email = 'Vui lòng nhập Email';
    } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
      newErrors.email = 'Email không hợp lệ';
    }
    if (!formData.password) {
      newErrors.password = 'Vui lòng nhập mật khẩu';
    } else if (formData.password.length < 6) {
      newErrors.password = 'Mật khẩu phải có ít nhất 6 ký tự';
    }
    if (!formData.confirmPassword) {
      newErrors.confirmPassword = 'Vui lòng xác nhận mật khẩu';
    } else if (formData.password !== formData.confirmPassword) {
      newErrors.confirmPassword = 'Mật khẩu không khớp';
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
        alert('Đăng ký thành công (Giả lập)');
      }, 1500);
    }
  };

  const handleGoogleLogin = () => {
    alert('Giả lập đăng ký bằng Google');
  };

  return (
    <AuthLayout>
      <div className="auth-header">
        <h1 className="auth-title">Tạo tài khoản</h1>
        <p className="auth-subtitle">Tham gia cùng chúng tôi và khám phá Năng lượng Kinetic.</p>
      </div>

      <form className="auth-form" onSubmit={handleSubmit}>
        <div className="auth-form-fields">
          <Input
            label="Họ và Tên"
            name="fullName"
            type="text"
            placeholder="Nguyễn Văn A"
            value={formData.fullName}
            onChange={handleChange}
            error={errors.fullName}
            disabled={isLoading}
          />

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
            placeholder="Tạo mật khẩu"
            value={formData.password}
            onChange={handleChange}
            error={errors.password}
            disabled={isLoading}
          />

          <PasswordInput
            label="Xác nhận mật khẩu"
            name="confirmPassword"
            placeholder="Nhập lại mật khẩu"
            value={formData.confirmPassword}
            onChange={handleChange}
            error={errors.confirmPassword}
            disabled={isLoading}
          />
        </div>

        <Button type="submit" variant="primary" isLoading={isLoading}>
          Tạo tài khoản
        </Button>
      </form>

      <div className="auth-divider">Hoặc</div>

      <SocialButton provider="google" onClick={handleGoogleLogin} disabled={isLoading} />

      <div className="auth-redirect">
        Đã có tài khoản? <Link to="/login" className="auth-link">Đăng nhập</Link>
      </div>
    </AuthLayout>
  );
}
