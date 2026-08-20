import './AuthLayout.css';
import backgroundImage from '../../assets/images/background.jpg';

export default function AuthLayout({ children }) {
  return (
    <div className="auth-layout">
      <div className="auth-form-container">
        <div className="auth-form-wrapper">
          {children}
        </div>
      </div>
      <div className="auth-visual-container">
        <img 
          src={backgroundImage} 
          alt="Brand Visual" 
          className="background-image"
          onError={(e) => {
            e.target.style.display = 'none';
            e.target.parentElement.classList.add('fallback-bg');
          }}
        />
      </div>
    </div>
  );
}
