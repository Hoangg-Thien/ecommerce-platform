import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import MainLayout from '../../components/layout/MainLayout';
import paymentApi from '../../api/paymentApi';
import Button from '../../components/ui/Button';

export default function PaymentResult() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  
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
      <div className="container" style={{ textAlign: 'center', padding: '100px 20px' }}>
        {isLoading || status === 'PENDING' ? (
          <div>
            <h2>Đang kiểm tra kết quả giao dịch...</h2>
            <p>Vui lòng không đóng trình duyệt!</p>
          </div>
        ) : status === 'SUCCESS' ? (
          <div style={{ color: 'green' }}>
            <h1 style={{ fontSize: '48px', marginBottom: '20px' }}>✅</h1>
            <h2>Đã đặt hàng thành công!</h2>
            <p>Đơn hàng của bạn đã được ghi nhận.</p>
            <div style={{ marginTop: '20px', display: 'flex', gap: '10px', justifyContent: 'center' }}>
                <Button onClick={() => navigate('/orders')} variant="outline">Xem đơn hàng</Button>
                <Button onClick={() => navigate('/')}>Tiếp tục mua sắm</Button>
            </div>
          </div>
        ) : (
          <div style={{ color: 'red' }}>
            <h1 style={{ fontSize: '48px', marginBottom: '20px' }}>❌</h1>
            <h2>Thanh toán Thất Bại hoặc Hủy</h2>
            <p>Vui lòng thử đặt hàng lại!</p>
            <Button onClick={() => navigate('/cart')} style={{ marginTop: '20px' }}>Quay lại Giỏ hàng</Button>
          </div>
        )}
      </div>
    </MainLayout>
  );
}
