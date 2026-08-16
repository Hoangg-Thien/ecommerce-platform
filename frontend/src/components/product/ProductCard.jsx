import { useState } from 'react';
import { ShoppingCart } from 'lucide-react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import Button from '../ui/Button';
import ConfirmModal from '../ui/ConfirmModal';
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
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);

  // Format currency
  const formattedPrice = new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND'
  }).format(price);

  const displayImage = imageUrl || image;

  const handleAddToCart = async () => {
    if (!user) {
      setIsLoginModalOpen(true);
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
          aria-label="Xem chi tiết"
          onClick={() => navigate(`/product/${id}`)}
          disabled={product.stock === 0}
        >
          <ShoppingCart size={18} className="btn-icon" />
          {product.stock === 0 ? 'Hết hàng' : 'Xem tùy chọn'}
        </Button>
      </div>
      
      <ConfirmModal
        isOpen={isLoginModalOpen}
        title="Cần đăng nhập"
        message="Có vẻ như bạn chưa đăng nhập. Đăng nhập để tiếp tục thêm vào giỏ hoặc quay lại trang chủ."
        confirmText="Đăng nhập"
        cancelText="Trở về trang chủ"
        isDanger={false}
        onConfirm={() => {
          setIsLoginModalOpen(false);
          navigate('/login', { state: { from: location } });
        }}
        onCancel={() => {
          setIsLoginModalOpen(false);
          navigate('/');
        }}
      />
    </div>
  );
}
