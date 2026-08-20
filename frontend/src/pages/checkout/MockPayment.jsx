import { useState } from 'react';
import { useSearchParams, useNavigate, Navigate } from 'react-router-dom';
import MainLayout from '../../components/layout/MainLayout';
import Button from '../../components/ui/Button';
import mockPaymentApi from '../../api/mockPaymentApi';
import './MockPayment.css';

export default function MockPayment() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const orderId = searchParams.get('orderId');

  const [isLoading, setIsLoading] = useState(false);
  const [activeScenario, setActiveScenario] = useState(null);
  const [error, setError] = useState('');

  if (!orderId) {
    return (
      <MainLayout>
        <div className="mock-payment-error-container">
          <div className="mock-payment-card">
            <h2>Lỗi</h2>
            <p>Không tìm thấy mã đơn hàng.</p>
          </div>
        </div>
      </MainLayout>
    );
  }

  const handleSimulate = async (scenario) => {
    setIsLoading(true);
    setActiveScenario(scenario);
    setError('');

    try {
      await mockPaymentApi.simulatePayment(orderId, { scenario });
      
      // Navigate on success
      navigate(`/payment-result?orderId=${orderId}`);
    } catch (err) {
      console.error('Mock payment error:', err);
      if (err.response?.status === 400) {
        setError(err.response.data?.message || 'Đơn hàng này không còn ở trạng thái chờ thanh toán.');
      } else if (err.response?.status === 403) {
        setError('Bạn không có quyền thực hiện thanh toán cho đơn hàng này.');
      } else if (err.response?.status === 404) {
        setError('Không tìm thấy giao dịch thanh toán cho đơn hàng này.');
      } else {
        setError('Không thể kết nối đến máy chủ. Vui lòng thử lại.');
      }
      setIsLoading(false);
      setActiveScenario(null);
    }
  };

  return (
    <MainLayout>
      <div className="mock-payment-page">
        <div className="mock-payment-container">
          <div className="mock-payment-card">
            
            <div className="mock-payment-header">
              <span className="mock-badge">DEMO PAYMENT</span>
              <h1 className="mock-title">Thanh toán đơn hàng</h1>
              <p className="mock-subtitle">Môi trường mô phỏng thanh toán MoMo</p>
            </div>

            {error && (
              <div className="mock-alert mock-alert-error">
                {error}
              </div>
            )}

            <div className="mock-order-info">
              <div className="mock-info-row">
                <span className="mock-label">Mã đơn hàng:</span>
                <span className="mock-value">#{orderId}</span>
              </div>
              <div className="mock-info-row">
                <span className="mock-label">Phương thức:</span>
                <span className="mock-value">MoMo (Demo)</span>
              </div>
            </div>

            <div className="mock-actions">
              <p className="mock-actions-title">Vui lòng chọn trạng thái giả lập:</p>
              
              <Button 
                onClick={() => handleSimulate('SUCCESS')}
                disabled={isLoading}
                className="mock-btn-success"
              >
                {isLoading && activeScenario === 'SUCCESS' ? 'Đang xử lý...' : 'Thanh toán thành công'}
              </Button>

              <Button 
                onClick={() => handleSimulate('FAIL')}
                disabled={isLoading}
                variant="outline"
                className="mock-btn-fail"
              >
                {isLoading && activeScenario === 'FAIL' ? 'Đang xử lý...' : 'Thanh toán thất bại'}
              </Button>

              <Button 
                onClick={() => handleSimulate('PENDING')}
                disabled={isLoading}
                variant="outline"
                className="mock-btn-pending"
              >
                {isLoading && activeScenario === 'PENDING' ? 'Đang xử lý...' : 'Đang xử lý (Pending)'}
              </Button>
            </div>

            <div className="mock-footer">
              <p>Lưu ý: Mọi giao dịch tại đây không dùng tiền thật và chỉ nhằm mục đích minh họa.</p>
            </div>

          </div>
        </div>
      </div>
    </MainLayout>
  );
}
