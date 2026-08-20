import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import MainLayout from '../../components/layout/MainLayout';
import paymentApi from '../../api/paymentApi';
import Button from '../../components/ui/Button';
import { useCart } from '../../context/CartContext';
import { CheckCircle2, XCircle, ShoppingBag, ArrowLeft } from 'lucide-react';
import './PaymentResult.css';

export default function PaymentResult() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { fetchCart } = useCart();
  
  const orderId = searchParams.get('orderId');
  const [status, setStatus] = useState('PENDING'); // PENDING, SUCCESS, FAILED
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!orderId) {
      navigate('/');
      return;
    }

    const checkPaymentStatus = async () => {
      try {
        const response = await paymentApi.getPaymentStatus(orderId);
        const payment = response;
        
        if (payment.paymentStatus === 'PAID' || payment.paymentMethod === 'COD') {
          setStatus('SUCCESS');
          fetchCart(); // Cập nhật lại giỏ hàng trên Header
        } else if (payment.paymentStatus === 'FAILED' || payment.paymentStatus === 'CANCELLED') {
          setStatus('FAILED');
        } else {
          // Nếu vẫn PENDING (do IPN của MoMo chưa gọi kịp), chờ 2 giây rồi check lại (Polling)
          setTimeout(checkPaymentStatus, 2000);
          return;
        }
      } catch (error) {
        console.error('Không tìm thấy giao dịch', error);
        setStatus('FAILED');
      } finally {
        setIsLoading(false);
      }
    };

    checkPaymentStatus();
  }, [orderId, navigate]);

  return (
    <MainLayout>
      <div className="payment-result-page">
        <div className="payment-result-card">
          {isLoading || status === 'PENDING' ? (
            <>
              <div className="result-icon-wrapper pending">
                <div className="spinner"></div>
              </div>
              <h1 className="result-title">Đang xử lý giao dịch...</h1>
              <p className="result-message">
                Vui lòng đợi trong giây lát và không đóng trình duyệt lúc này.
              </p>
            </>
          ) : status === 'SUCCESS' ? (
            <>
              <div className="result-icon-wrapper success">
                <CheckCircle2 size={40} />
              </div>
              <h1 className="result-title">Đặt hàng thành công!</h1>
              <p className="result-message">
                Cảm ơn bạn đã mua sắm. Đơn hàng <strong>#{orderId}</strong> của bạn đã được ghi nhận.
              </p>
              <div className="result-actions">
                <Button onClick={() => navigate('/orders')} variant="primary" className="btn">
                  <ShoppingBag size={18} style={{ marginRight: '8px' }} /> Xem đơn hàng
                </Button>
                <Button onClick={() => navigate('/')} variant="outline" className="btn">
                  Tiếp tục mua sắm
                </Button>
              </div>
            </>
          ) : (
            <>
              <div className="result-icon-wrapper failed">
                <XCircle size={40} />
              </div>
              <h1 className="result-title">Thanh toán thất bại</h1>
              <p className="result-message">
                Rất tiếc, giao dịch của bạn đã bị từ chối hoặc đã bị hủy. Vui lòng thử lại với phương thức thanh toán khác.
              </p>
              <div className="result-actions">
                <Button onClick={() => navigate('/cart')} variant="primary" className="btn">
                  <ArrowLeft size={18} style={{ marginRight: '8px' }} /> Quay lại giỏ hàng
                </Button>
              </div>
            </>
          )}
        </div>
      </div>
    </MainLayout>
  );
}
