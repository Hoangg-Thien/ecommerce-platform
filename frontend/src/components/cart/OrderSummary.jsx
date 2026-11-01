import { Link } from 'react-router-dom';
import Button from '../ui/Button';
import './OrderSummary.css';

export default function OrderSummary({ subtotal, hasItems, onCheckout, isCheckingOut }) {


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
        
        <div className="summary-divider"></div>
        
        <div className="summary-row summary-total">
          <span className="summary-label">Tiền hàng</span>
          <span className="summary-value">{formatPrice(subtotal)}</span>
        </div>
      </div>
      
      <p>Phí vận chuyển và ưu đãi đơn được tính tại bước thanh toán.</p>
      <div className = "checkout-btn-wrapper">
        <Button 
          variant="primary" 
          onClick={onCheckout}
          disabled={!hasItems || isCheckingOut}
          isLoading={isCheckingOut}
          className="checkout-btn"
          style={{ width: '100%' }}
        >
          Tiến hành thanh toán
        </Button>
      </div>
    </div>
  );
}
