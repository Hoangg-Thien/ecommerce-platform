import { useEffect, useRef, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import MainLayout from '../../components/layout/MainLayout';
import paymentApi from '../../api/paymentApi';
import Button from '../../components/ui/Button';
import { useCart } from '../../context/CartContext';
import { paymentOutcome } from '../../utils/payment';
import { CheckCircle2, XCircle, CircleHelp } from 'lucide-react';
import './PaymentResult.css';

export default function PaymentResult() {
  const [searchParams] = useSearchParams();
  const orderId = searchParams.get('orderId');
  return <PaymentResultBody key={orderId || 'invalid'} orderId={orderId} />;
}

function PaymentResultBody({ orderId }) {
  const navigate = useNavigate();
  const { fetchCart } = useCart();
  const refreshCart = useRef(fetchCart);
  useEffect(() => { refreshCart.current = fetchCart; }, [fetchCart]);
  const [result, setResult] = useState({ status: 'PENDING' });

  useEffect(() => {
    let active = true, timer, attempts = 0;
    const abort = new AbortController();
    const check = async () => {
      if (!orderId || !/^\d+$/.test(orderId)) { setResult({ status: 'UNVERIFIED' }); return; }
      try {
        const payment = await paymentApi.getPaymentStatus(orderId, abort.signal);
        if (!active) return;
        const status = paymentOutcome(payment);
        setResult({ status, paid: payment.paymentStatus === 'PAID', method: payment.paymentMethod });
        if (status !== 'PENDING') {
          if (status === 'COD' || status === 'PAID') refreshCart.current();
          return;
        }
      } catch {
        if (!active) return;
        // A network/auth error says nothing about whether VNPay received money.
      }
      attempts++;
      if (attempts >= 8) { setResult({ status: 'UNVERIFIED' }); return; }
      timer = setTimeout(check, Math.min(2000 * 2 ** (attempts - 1), 8000));
    };
    check();
    return () => { active = false; abort.abort(); clearTimeout(timer); };
  }, [orderId]);

  const messages = {
    COD: ['Đặt hàng COD thành công', 'Đơn đã được ghi nhận. Bạn thanh toán khi nhận hàng.'],
    PAID: [result.method === 'VNPAY' ? 'VNPay đã xác nhận thanh toán' : 'Thanh toán thành công',
      result.method === 'MOMO' ? 'Kết quả MoMo mô phỏng đã được ghi nhận.' : 'Đơn hàng của bạn đã được ghi nhận thanh toán thành công.'],
    MANUAL: [result.paid ? 'Đã thanh toán, cần xử lý thủ công' : 'Giao dịch cần đối soát',
      'Hệ thống cần xác minh hoặc xử lý đơn này. Chưa có xác nhận hoàn tiền. Vui lòng liên hệ hỗ trợ.'],
    FAILED: ['Thanh toán thất bại', 'Backend đã ghi nhận kết quả thanh toán thất bại. Hãy kiểm tra đơn trước khi đặt lại.'],
    UNVERIFIED: ['Chưa xác minh được thanh toán', 'Không thể kết luận giao dịch thất bại. Bạn có thể xem đơn hàng hoặc quay lại kiểm tra sau.'],
    PENDING: ['Đang xử lý giao dịch...', 'Đang chờ kết quả từ backend. Bạn có thể đóng trang và kiểm tra lại trong Đơn hàng của tôi.'],
  };
  const [title, message] = messages[result.status];
  return (
    <MainLayout>
      <div className="payment-result-page"><div className="payment-result-card" aria-live="polite">
        <div className={`result-icon-wrapper ${result.status === 'FAILED' ? 'failed' : ['PAID', 'COD'].includes(result.status) ? 'success' : 'pending'}`}>
          {result.status === 'PENDING' ? <div className="spinner" /> : result.status === 'FAILED' ? <XCircle size={40} />
            : ['PAID', 'COD'].includes(result.status) ? <CheckCircle2 size={40} /> : <CircleHelp size={40} />}
        </div>
        <h1 className="result-title">{title}</h1>
        <p className="result-message">{message}</p>
        {orderId && <p>Đơn hàng #{orderId}</p>}
        <div className="result-actions">
          <Button onClick={() => navigate('/orders')} variant="primary">Xem đơn hàng</Button>
          <Button onClick={() => navigate('/')} variant="outline">Tiếp tục mua sắm</Button>
        </div>
      </div></div>
    </MainLayout>
  );
}
