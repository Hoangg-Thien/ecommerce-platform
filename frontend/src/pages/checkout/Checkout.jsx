import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import MainLayout from '../../components/layout/MainLayout';
import CheckoutForm from '../../components/checkout/CheckoutForm';
import CheckoutSummary from '../../components/checkout/CheckoutSummary';
import { useCart } from '../../context/CartContext';
import checkoutApi from '../../api/checkoutApi';
import { generateIdempotencyKey } from '../../utils/generateIdempotencyKey';
import './Checkout.css';

export default function Checkout() {
  const navigate = useNavigate();
  const { cart, isLoading: isCartLoading, fetchCart, clearCart } = useCart();

  const [formData, setFormData] = useState({
    email: '',
    firstName: '',
    lastName: '',
    address: '',
    city: '',
    ward: '',
    phone: ''
  });

  const [shippingMethod, setShippingMethod] = useState('standard');
  const [paymentMethod, setPaymentMethod] = useState('');
  const [isSuccess, setIsSuccess] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);

  // Sinh Idempotency Key 1 lần duy nhất khi load trang Checkout (chống double click)
  const [idempotencyKey] = useState(() => generateIdempotencyKey());

  // Bắt buộc phải có giỏ hàng mới cho vào trang này
  useEffect(() => {
    if (!isSuccess && !isCartLoading && (!cart || !cart.items || cart.items.length === 0)) {
      navigate('/checkout');
    }
  }, [cart, isCartLoading, navigate, isSuccess]);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handlePlaceOrder = async () => {
    setIsProcessing(true);
    try {
      const payload = {
        ...formData,
        shippingMethod: shippingMethod,
        shippingFee: shippingMethod === 'express' ? 30000 : 0,
        paymentMethod: paymentMethod === 'cod' ? 'COD' : (paymentMethod === 'momo' ? 'MOMO' : paymentMethod)
      };

      console.log('Sending Idempotency-Key:', idempotencyKey);
      const response = await checkoutApi.checkout(payload, idempotencyKey);

      setIsSuccess(true);
      clearCart(); // Xóa giỏ hàng khỏi state ngay lập tức

      // Nếu là COD -> Chuyển thẳng tới trang thành công
      if (payload.paymentMethod === 'COD') {
        navigate(`/payment-result?orderId=${response.orderId}`);
      }
      // Nếu là MoMo -> Backend sẽ trả về paymentUrl -> Redirect tới MoMo (hoặc Mock)
      else if (payload.paymentMethod === 'MOMO' && (response.paymentUrl || response.payUrl)) {
        window.location.href = response.paymentUrl || response.payUrl;
      }
    } catch (error) {
      console.error('Lỗi khi đặt hàng', error);
      alert(error.response?.data?.message || 'Có lỗi xảy ra, thử lại sau!');
    } finally {
      setIsProcessing(false);
    }
  };

  if (!cart) return null;

  const subtotal = cart.totalPrice || 0;
  const shippingFee = shippingMethod === 'express' ? 30000 : 0;

  // Format items for CheckoutSummary
  const cartItemsFormatted = cart.items.map(item => ({
    id: item.id,
    name: item.productName,
    price: item.productPrice,
    image: item.imageUrl,
    quantity: item.quantity,
    size: item.size
  }));

  // Kiểm tra xem tất cả các field đã được nhập và phương thức thanh toán đã được chọn chưa
  const isFormValid = Object.values(formData).every(value => value.trim() !== '') && paymentMethod !== '';

  return (
    <MainLayout>
      <div className="checkout-page">
        <div className="checkout-container">
          <div className="checkout-header">
            <h1 className="checkout-title">Thanh toán</h1>
            <p className="checkout-subtitle">Vui lòng điền thông tin để hoàn tất đơn hàng.</p>
          </div>

          <div className="checkout-layout">
            <div className="checkout-form-side">
              <CheckoutForm
                formData={formData}
                onChange={handleInputChange}
                shippingMethod={shippingMethod}
                setShippingMethod={setShippingMethod}
                paymentMethod={paymentMethod}
                setPaymentMethod={setPaymentMethod}
              />
            </div>

            <div className="checkout-summary-side">
              <CheckoutSummary
                cartItems={cartItemsFormatted}
                subtotal={subtotal}
                shippingFee={shippingFee}
                onCompleteOrder={handlePlaceOrder}
                isProcessing={isProcessing}
                isFormValid={isFormValid}
              />
            </div>
          </div>
        </div>
      </div>
    </MainLayout>
  );
}
