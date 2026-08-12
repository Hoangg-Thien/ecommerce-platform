import '../ui/Button.css';
import './SocialButton.css';

export default function SocialButton({ provider = 'google', onClick, disabled }) {
  return (
    <button
      type="button"
      className="btn btn-secondary social-btn"
      onClick={onClick}
      disabled={disabled}
    >
      <img 
        src={`https://www.svgrepo.com/show/475656/google-color.svg`} 
        alt="Google Logo" 
        className="social-icon" 
      />
      Continue with Google
    </button>
  );
}
