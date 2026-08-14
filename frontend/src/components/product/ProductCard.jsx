import { ShoppingCart } from 'lucide-react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import Button from '../ui/Button';
import { useAuth } from '../../context/AuthContext';
import { useCart } from '../../context/CartContext';
import { useToast } from '../../context/ToastContext';
import './ProductCard.css';

export default function ProductCard({ product }) {
  const { id, name, price, image, imageUrl, badge } = product;
  const { user } = useAuth();
  const { addToCart } = useCart();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  // Format currency
  const formattedPrice = new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND'
  }).format(price);

  const displayImage = imageUrl || image;

  const handleAddToCart = async () => {
    if (!user) {
      alert('Vui lòng đăng nhập để thêm vào giỏ hàng!');
      navigate('/login', { state: { from: location } });
      return;
    }

    try {
      await addToCart(id, 1);
      showToast('Đã thêm vào giỏ hàng!');
    } catch (error) {
      console.error('Lỗi khi thêm vào giỏ hàng', error);
      showToast(error.response?.data?.message || 'Có lỗi xảy ra!', 'error', 4500);
    }
  };

  return (
    <div className="product-card">
      <Link to={`/product/${id}`} className="product-image-container">
        <img src={displayImage} alt={name} className="product-image" />
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
        
        <Button 
          variant="secondary" 
          className="add-to-cart-btn" 
          aria-label="Thêm vào giỏ"
          onClick={handleAddToCart}
        >
          <ShoppingCart size={18} className="btn-icon" />
          Thêm vào giỏ
        </Button>
      </div>
    </div>
  );
}
