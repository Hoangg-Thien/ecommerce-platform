import { ShoppingCart } from 'lucide-react';
import { Link } from 'react-router-dom';
import Button from '../ui/Button';
import './ProductCard.css';

export default function ProductCard({ product }) {
  const { id, name, price, image, badge } = product;

  // Format currency
  const formattedPrice = new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND'
  }).format(price);

  return (
    <div className="product-card">
      <Link to={`/product/${id}`} className="product-image-container">
        <img src={image} alt={name} className="product-image" />
        {badge && (
          <div className={`product-badge badge-${badge.type}`}>
            {badge.text}
          </div>
        )}
      </Link>
      
      <div className="product-info">
        <Link to={`/product/${id}`} className="product-name-link">
          <h3 className="product-name" title={name}>{name}</h3>
        </Link>
        <p className="product-price">{formattedPrice}</p>
        
        <Button variant="secondary" className="add-to-cart-btn" aria-label="Thêm vào giỏ">
          <ShoppingCart size={18} className="btn-icon" />
          Thêm vào giỏ
        </Button>
      </div>
    </div>
  );
}
