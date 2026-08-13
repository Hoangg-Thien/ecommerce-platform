import { useNavigate } from 'react-router-dom';
import MainLayout from '../../components/layout/MainLayout';
import Button from '../../components/ui/Button';
import { useCart } from '../../context/CartContext';
import { useAuth } from '../../context/AuthContext';
import './Cart.css';

export default function Cart() {
  const { user } = useAuth();
  const { cart, removeCartItem, isLoading } = useCart();
  const navigate = useNavigate();

  // Redirect nếu vào cart mà chưa đăng nhập
  if (!user) {
    return (
      <MainLayout>
        <div className="container" style={{ padding: '0 20px', maxWidth: '1200px', margin: '0 auto' }}>
          <div className="adidas-cart-header">
            <h1 className="adidas-cart-title">GIỎ HÀNG CỦA BẠN <span className="adidas-cart-count">(0 các sản phẩm)</span></h1>
          </div>
          
          <div className="adidas-empty-msg">
            Giỏ hàng của bạn trống
          </div>

          <button className="adidas-btn-box" onClick={() => navigate('/')}>
            <span>Bắt đầu</span>
            <span className="arrow-icon">→</span>
          </button>

          <p className="adidas-login-text">
            Không thấy sản phẩm của bạn? Đăng nhập để xem giỏ hàng.
          </p>

          <button className="adidas-btn-black" onClick={() => navigate('/login')}>
            <span>Đăng nhập</span>
            <span className="arrow-icon">→</span>
          </button>
        </div>
      </MainLayout>
    );
  }

  return (
    <MainLayout>
      <div className="cart-page">
        <div className="container">
          <h1 className="cart-title">Giỏ hàng của bạn</h1>
          
          {isLoading ? (
            <div>Đang tải giỏ hàng...</div>
          ) : !cart || !cart.items || cart.items.length === 0 ? (
            <div className="empty-cart">
              <p>Giỏ hàng của bạn đang trống.</p>
              <Button onClick={() => navigate('/')}>Tiếp tục mua sắm</Button>
            </div>
          ) : (
            <div className="cart-content">
              {/* Danh sách CartItem */}
              <div className="cart-items">
                {cart.items.map(item => (
                  <div key={item.id} className="cart-item">
                    <img src={item.productImageUrl} alt={item.productName} width={80} />
                    <div className="item-details">
                      <h3>{item.productName}</h3>
                      <p>Đơn giá: {item.price}đ</p>
                      <p>Số lượng: {item.quantity}</p>
                    </div>
                    <Button variant="outline" onClick={() => removeCartItem(item.id)}>
                      Xóa
                    </Button>
                  </div>
                ))}
              </div>

              {/* CartSummary */}
              <div className="cart-summary">
                <h3>Tóm tắt đơn hàng</h3>
                <div className="summary-row">
                  <span>Tổng cộng:</span>
                  <span className="total-price">{cart.totalPrice}đ</span>
                </div>
                <Button variant="primary" onClick={() => navigate('/checkout')}>
                  Tiến hành thanh toán
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>
    </MainLayout>
  );
}
