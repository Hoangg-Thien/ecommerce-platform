import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { Minus, Plus, ShoppingBag, Clock } from 'lucide-react';
import MainLayout from '../components/layout/MainLayout';
import SizeSelector from '../components/product/SizeSelector';
import Accordion from '../components/ui/Accordion';
import Button from '../components/ui/Button';
import ProductCard from '../components/product/ProductCard';
import './ProductDetail.css';

// Dữ liệu giả lập
const MOCK_PRODUCT = {
  id: '1',
  name: 'TRAIL RUNNER LOW',
  category: 'GIÀY DÉP • CHẠY ĐỊA HÌNH',
  price: 2150000,
  image: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?q=80&w=1000&auto=format&fit=crop',
  description: 'Đế lug bám địa hình, phần upper dệt liền chống thấm nhẹ. Form chuẩn cho cả đi phố lẫn off-road.',
  stockCount: 3,
  sizes: ['40', '41', '42', '43', '44'],
  details: 'Thiết kế với lớp lưới thoáng khí, lót trong êm ái và đệm xốp Eva đàn hồi cao giúp tối ưu hóa từng bước chạy trên mọi địa hình hiểm trở.',
  shipping: 'Miễn phí giao hàng tiêu chuẩn cho mọi đơn hàng từ 1.000.000đ. Đổi trả dễ dàng trong vòng 30 ngày.'
};

const RECOMMENDED_PRODUCTS = [
  {
    id: 11,
    name: 'CITY RUNNER PRO',
    price: 2400000,
    image: 'https://images.unsplash.com/photo-1608231387042-66d1773070a5?q=80&w=600&auto=format&fit=crop',
    badge: null
  },
  {
    id: 12,
    name: 'TREK HIKER MID',
    price: 2850000,
    image: 'https://images.unsplash.com/photo-1520639888713-7851133b1ed0?q=80&w=600&auto=format&fit=crop',
    badge: { type: 'new', text: 'Mới' }
  },
  {
    id: 13,
    name: 'EVERYDAY COURT',
    price: 1950000,
    image: 'https://images.unsplash.com/photo-1549298916-b41d501d3772?q=80&w=600&auto=format&fit=crop',
    badge: null
  },
  {
    id: 14,
    name: 'TRAIL SPEEDSTER',
    price: 2250000,
    image: 'https://images.unsplash.com/photo-1606107557195-0e29a4b5b4aa?q=80&w=600&auto=format&fit=crop',
    badge: { type: 'stock', text: 'Sắp hết' }
  }
];

export default function ProductDetail() {
  const { id } = useParams();
  const [selectedSize, setSelectedSize] = useState('41');
  const [quantity, setQuantity] = useState(1);
  const [isAdding, setIsAdding] = useState(false);

  // Thực tế sẽ dùng ID để fetch data, ở đây dùng mock cố định
  const product = MOCK_PRODUCT; 

  const formatPrice = (price) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND'
    }).format(price);
  };

  const handleAddToCart = () => {
    setIsAdding(true);
    setTimeout(() => {
      setIsAdding(false);
      alert(`Đã thêm ${quantity} sản phẩm (Size: ${selectedSize}) vào giỏ hàng!`);
    }, 1000);
  };

  const handleUpdateQty = (delta) => {
    const newQty = quantity + delta;
    if (newQty >= 1) {
      setQuantity(newQty);
    }
  };

  return (
    <MainLayout>
      <div className="product-detail-page">
        {/* Khối Thông tin sản phẩm */}
        <div className="product-main-container">
          <div className="product-image-col">
            <div className="product-image-main-wrapper">
              <img src={product.image} alt={product.name} className="product-main-image" />
            </div>
            {/* Đã bỏ phần ảnh thu nhỏ theo yêu cầu */}
          </div>

          <div className="product-info-col">
            <span className="product-category">{product.category}</span>
            <h1 className="product-title">{product.name}</h1>
            <div className="product-price">{formatPrice(product.price)}</div>
            
            <p className="product-desc">{product.description}</p>
            
            {product.stockCount > 0 && product.stockCount <= 5 && (
              <div className="stock-warning">
                <Clock size={16} />
                Chỉ còn {product.stockCount} sản phẩm
              </div>
            )}

            <div className="product-options">
              <SizeSelector 
                sizes={product.sizes}
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
              <Accordion title="Chi tiết & Chất liệu" defaultOpen={true}>
                {product.details}
              </Accordion>
              <Accordion title="Vận chuyển & Đổi trả">
                {product.shipping}
              </Accordion>
            </div>
          </div>
        </div>
        
        {/* Khối Sản phẩm gợi ý */}
        <div className="recommended-section">
          <h3 className="recommended-title">Có thể bạn sẽ thích</h3>
          <div className="recommended-grid">
            {RECOMMENDED_PRODUCTS.map(prod => (
              <ProductCard key={prod.id} product={prod} />
            ))}
          </div>
        </div>
      </div>
    </MainLayout>
  );
}
