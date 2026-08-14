import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ShoppingBag } from 'lucide-react';
import MainLayout from '../../components/layout/MainLayout';
import CartItem from '../../components/cart/CartItem';
import OrderSummary from '../../components/cart/OrderSummary';
import Button from '../../components/ui/Button';
import ConfirmModal from '../../components/ui/ConfirmModal';
import { useCart } from '../../context/CartContext';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import './Cart.css';

export default function Cart() {
  const { user } = useAuth();
  const { cart, removeCartItem, addToCart, updateQuantity, isLoading } = useCart();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [isCheckingOut, setIsCheckingOut] = useState(false);
  const [itemToRemove, setItemToRemove] = useState(null);

  if (!user) {
    return (
      <MainLayout>
        <div className="cart-page">
          <div className="cart-container">
            <div className="cart-header">
              <h1 className="cart-title">Giỏ hàng của bạn</h1>
            </div>
            <div className="cart-empty-state">
              <div className="empty-icon-wrapper">
                <ShoppingBag size={48} className="empty-icon" />
              </div>
              <h2 className="empty-title">Bạn chưa đăng nhập</h2>
              <p className="empty-subtitle">Vui lòng đăng nhập để xem giỏ hàng hoặc bắt đầu mua sắm.</p>
              <div style={{ display: 'flex', gap: '15px', justifyContent: 'center', marginTop: '20px' }}>
                <Link to="/login">
                  <Button variant="primary">Đăng nhập</Button>
                </Link>
                <Link to="/">
                  <Button variant="outline">Bắt đầu mua sắm</Button>
                </Link>
              </div>
            </div>
          </div>
        </div>
      </MainLayout>
    );
  }

  const handleUpdateQuantity = async (id, newQuantity, oldQuantity) => {
    if (newQuantity < 1) return;

    // Tìm item trong cart
    const item = cart?.items.find(i => i.id === id);
    if (!item) return;

    try {
      await updateQuantity(item.productId, newQuantity);
    } catch (error) {
      console.error('Lỗi khi cập nhật số lượng', error);
      showToast(error.response?.data?.message || 'Có lỗi xảy ra!', 'error', 4000);
    }
  };

  const handleRemoveClick = (id) => {
    setItemToRemove(id);
  };

  const confirmRemoveItem = async () => {
    if (!itemToRemove) return;
    try {
      await removeCartItem(itemToRemove);
    } catch (error) {
      console.error('Lỗi khi xóa sản phẩm', error);
      alert('Có lỗi xảy ra khi xóa sản phẩm');
    } finally {
      setItemToRemove(null);
    }
  };

  const handleCheckout = () => {
    setIsCheckingOut(true);
    navigate('/checkout');
    setIsCheckingOut(false);
  };

  const cartItems = cart?.items || [];
  const subtotal = cart?.totalPrice || 0;

  const formattedCartItems = cartItems.map(item => ({
    id: item.id,
    productId: item.productId,
    name: item.productName,
    price: item.productPrice,
    image: item.productImageUrl,
    quantity: item.quantity,
    variant: '' // Placeholder vì backend chưa có variant
  }));

  return (
    <>
      <MainLayout>
        <div className="cart-page">
          <div className="cart-container">
            <div className="cart-header">
              <h1 className="cart-title">Giỏ hàng của bạn</h1>
              <span className="cart-count">({cartItems.length} sản phẩm)</span>
            </div>

            {isLoading ? (
              <div style={{ textAlign: 'center', padding: '50px' }}>Đang tải giỏ hàng...</div>
            ) : cartItems.length > 0 ? (
              <div className="cart-layout">
                <div className="cart-items-section">
                  <div className="cart-items-list">
                    {formattedCartItems.map(item => (
                      <CartItem
                        key={item.id}
                        item={item}
                        onUpdateQuantity={(id, newQty) => handleUpdateQuantity(id, newQty, item.quantity)}
                        onRemove={handleRemoveClick}
                      />
                    ))}
                  </div>
                </div>

                <div className="cart-summary-section">
                  <OrderSummary
                    subtotal={subtotal}
                    shippingFee={30000}
                    onCheckout={handleCheckout}
                    isCheckingOut={isCheckingOut}
                  />
                </div>
              </div>
            ) : (
              <div className="cart-empty-state">
                <div className="empty-icon-wrapper">
                  <ShoppingBag size={48} className="empty-icon" />
                </div>
                <h2 className="empty-title">Giỏ hàng trống</h2>
                <p className="empty-subtitle">Có vẻ như bạn chưa thêm sản phẩm nào vào giỏ hàng.</p>
                <Link to="/">
                  <Button variant="primary">Tiếp tục mua sắm</Button>
                </Link>
              </div>
            )}
          </div>
        </div>
      </MainLayout>

      <ConfirmModal 
        isOpen={!!itemToRemove}
        title="Xóa sản phẩm"
        message="Bạn có chắc chắn muốn xóa sản phẩm này khỏi giỏ hàng không?"
        confirmText="Xóa"
        onConfirm={confirmRemoveItem}
        onCancel={() => setItemToRemove(null)}
        isDanger={true}
      />
    </>
  );
}
