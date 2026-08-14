import { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useCart } from '../../context/CartContext';
import { useToast } from '../../context/ToastContext';
import { Minus, Plus, ShoppingBag, Clock } from 'lucide-react';
import MainLayout from '../../components/layout/MainLayout';
import SizeSelector from '../../components/product/SizeSelector';
import Accordion from '../../components/ui/Accordion';
import Button from '../../components/ui/Button';
import productApi from '../../api/productApi';
import './ProductDetail.css';

export default function ProductDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const { addToCart } = useCart();
  const { showToast } = useToast();
  
  const [product, setProduct] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedSize, setSelectedSize] = useState('41'); // Có thể lấy size mặc định từ product nếu có
  const [quantity, setQuantity] = useState(1);
  const [isAdding, setIsAdding] = useState(false);

  useEffect(() => {
    const fetchDetail = async () => {
      try {
        const data = await productApi.getProductById(id);
        setProduct(data);
      } catch (error) {
        console.error('Lỗi khi tải chi tiết', error);
        alert('Sản phẩm không tồn tại!');
        navigate('/'); 
      } finally {
        setIsLoading(false);
      }
    };
    
    fetchDetail();
  }, [id, navigate]);

  const formatPrice = (price) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND'
    }).format(price);
  };

  const handleAddToCart = async () => {
    // Check login
    if (!user) {
      alert('Vui lòng đăng nhập để thêm vào giỏ hàng!');
      navigate('/login', { state: { from: location } });
      return;
    }

    setIsAdding(true);
    try {
      await addToCart(product.id, quantity);
      showToast('Đã thêm vào giỏ hàng!');
    } catch (err) {
      console.error('Lỗi khi thêm vào giỏ hàng', err);
      showToast(err.response?.data?.message || 'Có lỗi xảy ra, thử lại sau!', 'error', 4000);
    } finally {
      setIsAdding(false);
    }
  };

  const handleUpdateQty = (delta) => {
    const newQty = quantity + delta;
    if (newQty >= 1) {
      setQuantity(newQty);
    }
  };

  if (isLoading) {
    return (
      <MainLayout>
        <div style={{ textAlign: 'center', padding: '100px' }}>Đang tải thông tin...</div>
      </MainLayout>
    );
  }

  if (!product) return null;

  return (
    <MainLayout>
      <div className="product-detail-page">
        {/* Khối Thông tin sản phẩm */}
        <div className="product-main-container">
          <div className="product-image-col">
            <div className="product-image-main-wrapper">
              <img src={product.imageUrl || product.image} alt={product.name} className="product-main-image" />
            </div>
          </div>

          <div className="product-info-col">
            <span className="product-category">Thời Trang</span>
            <h1 className="product-title">{product.name}</h1>
            <div className="product-price">{formatPrice(product.price)}</div>

            <p className="product-desc">{product.description}</p>

            {product.stockQuantity > 0 && product.stockQuantity <= 5 && (
              <div className="stock-warning">
                <Clock size={16} />
                Chỉ còn {product.stockQuantity} sản phẩm
              </div>
            )}

            {/* Giả định sản phẩm có size, nếu không có thì ẩn đi */}
            <div className="product-options">
              <SizeSelector
                sizes={['40', '41', '42', '43', '44']}
                selectedSize={selectedSize}
                onSelectSize={setSelectedSize}
              />
            </div>

            <div className="product-actions">
              <div className="qty-selector-lg">
                <button
                  className="qty-btn-lg"
                  onClick={() => handleUpdateQty(-1)}
                  disabled={quantity <= 1}
                >
                  <Minus size={20} />
                </button>
                <span className="qty-val-lg">{quantity}</span>
                <button
                  className="qty-btn-lg"
                  onClick={() => handleUpdateQty(1)}
                >
                  <Plus size={20} />
                </button>
              </div>

              <Button
                variant="primary"
                className="add-to-cart-btn-lg"
                onClick={handleAddToCart}
                isLoading={isAdding}
              >
                <ShoppingBag size={20} style={{ marginRight: '8px' }} />
                Thêm vào giỏ
              </Button>
            </div>

            <div className="product-accordions">
              <Accordion title="Chi tiết & Chất liệu">
                {product.details || 'Không có chi tiết'}
              </Accordion>
              <Accordion title="Vận chuyển & Đổi trả">
                Miễn phí giao hàng tiêu chuẩn cho mọi đơn hàng từ 1.000.000đ. Đổi trả dễ dàng trong vòng 30 ngày.
              </Accordion>
            </div>
          </div>
        </div>
      </div>
    </MainLayout>
  );
}
