import { Trash2, Plus, Minus } from 'lucide-react';
import { Link } from 'react-router-dom';
import './CartItem.css';

export default function CartItem({ item, onUpdateQuantity, onRemove, isSelected, onSelect}) {
  const formattedPrice = new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND'
  }).format(item.price);

  return (
    <div className="cart-item">
      {}
      <div className="cart-item-checkbox-wrapper" style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
        <input 
          type="checkbox" 
          checked={isSelected}
          onChange={(e) => onSelect(item.id, e.target.checked)}
          style={{ width: '20px', height: '20px', cursor: 'pointer' }}
        />
        <Link to={`/product/${item.productId}`} className="cart-item-image-wrapper">
          <img 
            src={item.image} 
            alt={item.name} 
            className="cart-item-image" 
            style={isSelected ? { boxShadow: '0 0 0 2px var(--color-primary)' } : {}}
          />
        </Link>
      </div>
      
      <div className="cart-item-details">
        <div className="cart-item-header">
          <Link to={`/product/${item.productId}`} className="cart-item-title-link">
            <h3 className="cart-item-title">{item.name}</h3>
          </Link>
          <button 
            className="cart-item-remove" 
            onClick={() => onRemove(item.id)}
            aria-label="Xóa sản phẩm"
          >
            <Trash2 size={18} />
          </button>
        </div>
        
        {item.variant && <p className="cart-item-variant">Size: {item.variant}</p>}
        
        <div className="cart-item-footer">
          <div className="quantity-selector">
            <button 
              className="quantity-btn" 
              onClick={() => onUpdateQuantity(item.id, item.quantity - 1)}
              disabled={item.quantity <= 1}
              aria-label="Giảm số lượng"
            >
              <Minus size={16} />
            </button>
            <span className="quantity-value">{item.quantity}</span>
            <button 
              className="quantity-btn" 
              onClick={() => onUpdateQuantity(item.id, item.quantity + 1)}
              aria-label="Tăng số lượng"
            >
              <Plus size={16} />
            </button>
          </div>
          
          <div className="cart-item-price">{formattedPrice}</div>
        </div>
      </div>
    </div>
  );
}
