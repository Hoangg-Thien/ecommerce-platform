import { Lock } from 'lucide-react';
import Button from '../ui/Button';
import './CheckoutSummary.css';

export default function CheckoutSummary({ cartItems, subtotal, shippingFee, onCompleteOrder, isProcessing, isFormValid = true }) {
  const total = subtotal + shippingFee;

  const formatPrice = (price) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND'
    }).format(price);
  };

  return (
    <div className="checkout-summary">
      <h2 className="summary-title">Tóm tắt đơn hàng</h2>
      
      <div className="checkout-item-list">
        {cartItems.map((item) => (
          <div key={item.id} className="checkout-mini-item">
            <div className="mini-item-image-wrapper">
              <img src={item.image} alt={item.name} className="mini-item-image" />
            </div>
            <div className="mini-item-details">
              <h4 className="mini-item-name">{item.name}</h4>
              <div className="mini-item-meta" style={{ display: 'flex', gap: '8px', fontSize: '13px', color: '#666', marginTop: '4px' }}>
                <span className="mini-item-size">Size: {item.size}</span>
              </div>
              <div className="mini-item-meta" style={{ display: 'flex', gap: '8px', fontSize: '13px', color: '#666', marginTop: '4px' }}>
                  <span className="mini-item-qty">Số lượng: {item.quantity}</span>
              </div>
            </div>
            <div className="mini-item-price">{formatPrice(item.price * item.quantity)}</div>
          </div>
        ))}
      </div>
      
      <div className="summary-divider"></div>
      
      <div className="summary-content">
        <div className="summary-row">
          <span className="summary-label">Tạm tính: </span>
          <span className="summary-value">{formatPrice(subtotal)}</span>
        </div>
        
        <div className="summary-row">
          <span className="summary-label">Phí vận chuyển: </span>
          <span className="summary-value">
            {shippingFee === 0 ? 'Miễn phí' : formatPrice(shippingFee)}
          </span>
        </div>
        
        <div className="summary-divider"></div>
        
        <div className="summary-row summary-total">
          <span className="summary-label">Tổng cộng: </span>
          <span className="summary-value">{formatPrice(total)}</span>
        </div>
      </div>
      
      <Button 
        variant="primary" 
        onClick={onCompleteOrder}
        disabled={isProcessing || !isFormValid}
        isLoading={isProcessing}
        className="complete-order-btn"
      >
        <span>Hoàn tất đặt hàng</span>
      </Button>

      {!isFormValid && (
        <p style={{ color: '#ef4444', fontSize: '13px', marginTop: '12px', textAlign: 'center', fontWeight: '500' }}>
          Vui lòng điền đầy đủ thông tin để tiếp tục.
        </p>
      )}
    </div>
  );
}
