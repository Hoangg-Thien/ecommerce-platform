import './AuthLayout.css';

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
          src="/assets/images/auth-hero-placeholder.jpg" 
          alt="Brand Visual" 
          className="auth-hero-image"
          onError={(e) => {
            e.target.style.display = 'none';
            e.target.parentElement.classList.add('fallback-bg');
          }}
        />
        <div className="auth-visual-overlay">
          <h2 className="auth-visual-title">Discover Kinetic Energy</h2>
          <p className="auth-visual-subtitle">Your premium tech-forward e-commerce experience starts here.</p>
        </div>
      </div>
    </div>
  );
}
