import Button from '../ui/Button';
import './OrderSummary.css';

export default function OrderSummary({ subtotal, shippingFee = 30000, onCheckout, isCheckingOut }) {
  const total = subtotal + (subtotal > 0 ? shippingFee : 0);

  const formatPrice = (price) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND'
    }).format(price);
  };

  return (
    <div className="order-summary">
      <h2 className="summary-title">Tóm tắt đơn hàng</h2>
      
      <div className="summary-content">
        <div className="summary-row">
          <span className="summary-label">Tạm tính</span>
          <span className="summary-value">{formatPrice(subtotal)}</span>
        </div>
        
        <div className="summary-row">
          <span className="summary-label">Phí vận chuyển</span>
          <span className="summary-value">
            {subtotal > 0 ? formatPrice(shippingFee) : formatPrice(0)}
          </span>
        </div>
        
        <div className="summary-divider"></div>
        
        <div className="summary-row summary-total">
          <span className="summary-label">Tổng cộng</span>
          <span className="summary-value">{formatPrice(total)}</span>
        </div>
      </div>
      
      <Button 
        variant="primary" 
        onClick={onCheckout}
        disabled={subtotal === 0 || isCheckingOut}
        isLoading={isCheckingOut}
        className="checkout-btn"
      >
        Tiến hành thanh toán
      </Button>
      
      <p className="summary-note">
        Thuế và mã giảm giá có thể được áp dụng ở bước tiếp theo.
      </p>
    </div>
  );
}
