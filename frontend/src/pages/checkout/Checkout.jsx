import { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import MainLayout from '../../components/layout/MainLayout';
import CheckoutForm from '../../components/checkout/CheckoutForm';
import CheckoutSummary from '../../components/checkout/CheckoutSummary';
import { useCart } from '../../context/CartContext';
import { useAuth } from '../../context/AuthContext';
import checkoutApi from '../../api/checkoutApi';
import { generateIdempotencyKey } from '../../utils/generateIdempotencyKey';
import './Checkout.css';

function readPending(pendingStorage) {
  try { return JSON.parse(sessionStorage.getItem(pendingStorage)); } catch { return null; }
}

export default function Checkout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { cart, isLoading: isCartLoading, fetchCart } = useCart();
  const { user } = useAuth();
  const pendingStorage = `pending-checkout:${user?.id ?? 'guest'}`;
  const [formData, setFormData] = useState({ email: '', firstName: '', lastName: '', address: '', city: '', ward: '', phone: '' });
  const [paymentMethod, setPaymentMethod] = useState('');
  const [isSuccess, setIsSuccess] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [attempt] = useState(() => readPending(pendingStorage) || { key: generateIdempotencyKey(), createdAt: Date.now() });
  const [recoveryRequired, setRecoveryRequired] = useState(() => Boolean(readPending(pendingStorage)));
  const [needsConfirmation, setNeedsConfirmation] = useState(false);
  const [message, setMessage] = useState('');
  const [previewState, setPreviewState] = useState(null);
  const inFlight = useRef(false);
  const selectionKey = JSON.stringify(location.state?.selectedItems || []);
  const cartKey = JSON.stringify(cart?.items || []);
  const previewKey = selectionKey + cartKey;
  const quote = previewState?.key === previewKey ? previewState.quote : null;

  useEffect(() => {
    if (!isSuccess && !recoveryRequired && !isCartLoading && cart?.items?.length === 0) navigate('/cart');
  }, [cart, isCartLoading, navigate, isSuccess, recoveryRequired]);

  useEffect(() => {
    if (!cartKey || cartKey === '[]' || recoveryRequired || isSuccess) return;
    const controller = new AbortController();
    const key = selectionKey + cartKey;
    checkoutApi.preview({ cartItemIds: JSON.parse(selectionKey) }, controller.signal)
      .then(result => { if (!controller.signal.aborted) setPreviewState({ key, quote: result }); })
      .catch(error => { if (!controller.signal.aborted) setMessage(error.response?.data?.message || 'Không thể tính giá. Vui lòng tải lại trang.'); });
    return () => controller.abort();
  }, [selectionKey, cartKey, recoveryRequired, isSuccess]);

  const finish = (id, method, status, paymentUrl) => {
    sessionStorage.removeItem(pendingStorage);
    setIsSuccess(true);
    fetchCart();
    if (paymentUrl?.startsWith('/')) navigate(paymentUrl);
    else if (paymentUrl) window.location.href = paymentUrl;
    else if (method === 'MOMO' && status === 'AWAITING_PAYMENT') navigate('/mock-payment?orderId=' + id);
    else navigate('/payment-result?orderId=' + id);
  };

  const recover = async () => {
    try {
      const result = await checkoutApi.result(attempt.key);
      finish(result.id, result.paymentMethod, result.status);
    } catch (error) {
      if (error.response?.status === 404 && Date.now() - attempt.createdAt < 24 * 60 * 60 * 1000) {
        // A concurrent request may still be committing. Retry only on another click, with the same key.
        setRecoveryRequired(false);
        setNeedsConfirmation(true);
        setMessage('Chưa tìm thấy kết quả. Kiểm tra giá rồi bấm xác nhận lại; hệ thống giữ nguyên mã yêu cầu.');
      } else {
        setMessage('Chưa xác minh được kết quả đặt hàng. Vui lòng kiểm tra Đơn hàng của tôi trước khi đặt tiếp.');
      }
    }
  };

  const handlePlaceOrder = async () => {
    if (inFlight.current) return;
    if (!recoveryRequired && (!quote || !isFormValid)) return;
    inFlight.current = true;
    setIsProcessing(true);
    setMessage('');
    try {
      if (recoveryRequired) { await recover(); return; }
      const method = paymentMethod === 'cod' ? 'COD' : 'MOMO';
      sessionStorage.setItem(pendingStorage, JSON.stringify(attempt));
      const response = await checkoutApi.checkout({
        ...formData, shippingMethod: 'standard', paymentMethod: method,
        cartItemIds: JSON.parse(selectionKey), expectedTotal: quote.total
      }, attempt.key);
      finish(response.orderId, method, response.orderStatus, response.paymentUrl);
    } catch (error) {
      const data = error.response?.data;
      if (!error.response || error.response.status >= 500 ||
          data?.message === 'Request is already processing or completed') {
        setRecoveryRequired(true);
        setMessage('Chưa nhận được kết quả. Bấm kiểm tra kết quả trước khi thử lại.');
      } else {
        sessionStorage.removeItem(pendingStorage);
        if (data?.code === 'PRICE_CHANGED' || data?.code === 'VOUCHER_SOLD_OUT') {
          setPreviewState(null);
          setNeedsConfirmation(true);
          try {
            const updated = data.breakdown || await checkoutApi.preview({ cartItemIds: JSON.parse(selectionKey) });
            setPreviewState({ key: previewKey, quote: updated });
          } catch {
            setMessage('Không thể lấy giá mới. Vui lòng tải lại trang trước khi đặt hàng.');
            return;
          }
        }
        setMessage(data?.message || 'Không thể đặt hàng. Vui lòng kiểm tra thông tin.');
      }
    } finally {
      inFlight.current = false;
      setIsProcessing(false);
    }
  };

  const isFormValid = Object.values(formData).every(value => value.trim() !== '') && paymentMethod !== '';
  const cartItems = (quote?.items || []).map(item => {
    const cartItem = cart?.items?.find(line => line.id === item.cartItemId);
    return { id: item.cartItemId, name: cartItem?.productName, price: item.unitPrice,
      image: cartItem?.imageUrl, size: cartItem?.size, quantity: item.quantity };
  });

  return (
    <MainLayout>
      <div className="checkout-page"><div className="checkout-container">
        <div className="checkout-header"><h1 className="checkout-title">Thanh toán</h1>
          <p className="checkout-subtitle">Vui lòng điền thông tin để hoàn tất đơn hàng.</p></div>
        {message && <p role="alert">{message}</p>}
        {recoveryRequired ? <div>
          <button disabled={isProcessing} onClick={handlePlaceOrder}>Kiểm tra kết quả đặt hàng</button>
          <button onClick={() => navigate('/orders')}>Đơn hàng của tôi</button>
        </div> : <div className="checkout-layout">
          <div className="checkout-form-side">
            <CheckoutForm formData={formData}
              onChange={event => setFormData(previous => ({ ...previous, [event.target.name]: event.target.value }))}
              shippingFee={quote?.shippingFee} paymentMethod={paymentMethod} setPaymentMethod={setPaymentMethod} />
          </div>
          <div className="checkout-summary-side">
            {quote ? <CheckoutSummary cartItems={cartItems} subtotal={quote.subtotal}
              shippingFee={quote.shippingFee} orderDiscount={quote.orderDiscount.amount} total={quote.total}
              actionLabel={needsConfirmation ? 'Xác nhận lại và đặt hàng' : 'Hoàn tất đặt hàng'}
              onCompleteOrder={handlePlaceOrder} isProcessing={isProcessing} isFormValid={isFormValid} />
              : <p role="status">Đang lấy giá từ hệ thống…</p>}
          </div>
        </div>}
      </div></div>
    </MainLayout>
  );
}
