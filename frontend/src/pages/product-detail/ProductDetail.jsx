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
import ConfirmModal from '../../components/ui/ConfirmModal';
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
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);

  useEffect(() => {
    const fetchDetail = async () => {
      try {
        const data = await productApi.getProductById(id);
        setProduct(data);
        if (data.variants && data.variants.length > 0) {
          const availableVariant = data.variants.find(v => v.stock > 0);
          if (availableVariant) {
            setSelectedSize(availableVariant.size);
          } else {
            setSelectedSize(data.variants[0].size); // Fallback to first if all out of stock
          }
        }
      } catch (error) {
        console.error('Lỗi khi tải chi tiết', error);
        showToast('Sản phẩm không tồn tại!', 'error');
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
      setIsLoginModalOpen(true);
      return;
    }

    const variant = product.variants?.find(v => v.size === selectedSize);
    if (!variant) {
      showToast('Vui lòng chọn kích cỡ hợp lệ!', 'error');
      return;
    }

    if (variant.stock <= 0) {
      showToast('Kích cỡ này đã hết hàng!', 'error');
      return;
    }

    if (quantity > variant.stock) {
      showToast(`Rất tiếc, chỉ còn ${variant.stock} sản phẩm cho kích cỡ này!`, 'error');
      return;
    }

    setIsAdding(true);
    try {
      await addToCart(variant.id, quantity);
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
            <span className="product-category">{product.categoryName || 'Sản phẩm'}</span>
            <h1 className="product-title">{product.name}</h1>
            <div className="product-price">{formatPrice(product.price)}</div>

            <p className="product-desc">{product.description}</p>

            {product.stock > 0 && product.stock <= 5 && (
              <div className="stock-warning">
                <Clock size={16} />
                Chỉ còn {product.stock} sản phẩm
              </div>
            )}

            {/* Giả định sản phẩm có size, nếu không có thì ẩn đi */}
            {product.variants && product.variants.length > 0 && (
              <div className="product-options">
                <SizeSelector
                  variants={product.variants}
                  selectedSize={selectedSize}
                  onSelectSize={setSelectedSize}
                />
              </div>
            )}

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
    </MainLayout>
  );
}
