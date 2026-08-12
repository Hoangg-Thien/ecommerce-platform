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
      newErrors.email = 'Email is required';
    } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
      newErrors.email = 'Please enter a valid email address';
    }
    if (!formData.password) {
      newErrors.password = 'Password is required';
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
        alert('Login simulated successful');
      }, 1500);
    }
  };

  const handleGoogleLogin = () => {
    alert('Google login simulation');
  };

  return (
    <AuthLayout>
      <div className="auth-header">
        <h1 className="auth-title">Welcome Back</h1>
        <p className="auth-subtitle">Sign in to your account to continue.</p>
      </div>

      <form className="auth-form" onSubmit={handleSubmit}>
        <div className="auth-form-fields">
          <Input
            label="Email"
            name="email"
            type="email"
            placeholder="you@example.com"
            value={formData.email}
            onChange={handleChange}
            error={errors.email}
            disabled={isLoading}
          />
          
          <PasswordInput
            label="Password"
            name="password"
            placeholder="Enter your password"
            value={formData.password}
            onChange={handleChange}
            error={errors.password}
            disabled={isLoading}
          />
        </div>

        <div className="auth-form-footer">
          <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--text-body-md)' }}>
            <input type="checkbox" disabled={isLoading} /> Remember me
          </label>
          <Link to="#" className="auth-link">Forgot Password?</Link>
        </div>

        <Button type="submit" variant="primary" isLoading={isLoading}>
          Sign In
        </Button>
      </form>

      <div className="auth-divider">Or</div>

      <SocialButton provider="google" onClick={handleGoogleLogin} disabled={isLoading} />

      <div className="auth-redirect">
        Don't have an account? <Link to="/register" className="auth-link">Sign up</Link>
      </div>
    </AuthLayout>
  );
}
