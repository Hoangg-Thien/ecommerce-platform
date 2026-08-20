import './RadioBox.css';

export default function RadioBox({ 
  id, 
  name, 
  value, 
  checked, 
  onChange, 
  label, 
  description, 
  priceLabel,
  icon
}) {
  return (
    <label className={`radio-box ${checked ? 'radio-box-checked' : ''}`} htmlFor={id}>
      <div className="radio-box-left">
        <input 
          type="radio" 
          id={id} 
          name={name} 
          value={value} 
          checked={checked} 
          onChange={onChange}
          className="radio-box-input"
        />
        {icon && <div className="radio-box-icon">{icon}</div>}
        <div className="radio-box-text">
          <span className="radio-box-label">{label}</span>
          {description && <span className="radio-box-desc">{description}</span>}
        </div>
      </div>
      {priceLabel && <div className="radio-box-price">{priceLabel}</div>}
    </label>
  );
}
