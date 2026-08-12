import { useState, forwardRef } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import Input from '../ui/Input';
import './PasswordInput.css';

const PasswordInput = forwardRef((props, ref) => {
  const [showPassword, setShowPassword] = useState(false);

  const togglePassword = () => {
    setShowPassword((prev) => !prev);
  };

  return (
    <div className="password-input-wrapper">
      <Input
        ref={ref}
        type={showPassword ? 'text' : 'password'}
        {...props}
        className={`password-input ${props.className || ''}`}
      />
      <button
        type="button"
        className="password-toggle-btn"
        onClick={togglePassword}
        aria-label={showPassword ? 'Hide password' : 'Show password'}
      >
        {showPassword ? (
          <EyeOff size={18} strokeWidth={2} />
        ) : (
          <Eye size={18} strokeWidth={2} />
        )}
      </button>
    </div>
  );
});

PasswordInput.displayName = 'PasswordInput';

export default PasswordInput;
