import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ShoppingBag } from 'lucide-react';
import MainLayout from '../../components/layout/MainLayout';
import CartItem from '../../components/cart/CartItem';
import OrderSummary from '../../components/cart/OrderSummary';
import Button from '../../components/ui/Button';
import './Cart.css';

// Dữ liệu giỏ hàng giả lập
const INITIAL_CART = [
  {
    id: 1,
    name: 'Áo Khoác Nam Thể Thao Đa Năng',
    price: 450000,
    image: 'https://images.unsplash.com/photo-1556821840-3a63f95609a7?q=80&w=600&auto=format&fit=crop',
    quantity: 1,
    variant: 'Size L / Đen'
  },
  {
    id: 2,
    name: 'Giày Chạy Bộ Nam Siêu Nhẹ',
    price: 1200000,
    image: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?q=80&w=600&auto=format&fit=crop',
    quantity: 2,
    variant: 'Size 42 / Xanh Neon'
  }
];

export default function Cart() {
  const [cartItems, setCartItems] = useState(INITIAL_CART);
  const [isCheckingOut, setIsCheckingOut] = useState(false);

  const handleUpdateQuantity = (id, newQuantity) => {
    if (newQuantity < 1) return;
    setCartItems(prev => prev.map(item => 
      item.id === id ? { ...item, quantity: newQuantity } : item
    ));
  };

  const handleRemoveItem = (id) => {
    setCartItems(prev => prev.filter(item => item.id !== id));
  };

  const handleCheckout = () => {
    setIsCheckingOut(true);
    setTimeout(() => {
      setIsCheckingOut(false);
      alert('Chuyển hướng đến trang thanh toán...');
    }, 1500);
  };

  const subtotal = cartItems.reduce((sum, item) => sum + (item.price * item.quantity), 0);

  return (
    <MainLayout>
      <div className="cart-page">
        <div className="cart-container">
          <div className="cart-header">
            <h1 className="cart-title">Giỏ hàng của bạn</h1>
            <span className="cart-count">({cartItems.length} sản phẩm)</span>
          </div>

          {cartItems.length > 0 ? (
            <div className="cart-layout">
              <div className="cart-items-section">
                <div className="cart-items-list">
                  {cartItems.map(item => (
                    <CartItem 
                      key={item.id} 
                      item={item} 
                      onUpdateQuantity={handleUpdateQuantity}
                      onRemove={handleRemoveItem}
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
  );
}
