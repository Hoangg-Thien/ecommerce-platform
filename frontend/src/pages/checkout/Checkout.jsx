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
  const { cart, isLoading: isCartLoading } = useCart();
  
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
  const [paymentMethod, setPaymentMethod] = useState('COD');
  const [isProcessing, setIsProcessing] = useState(false);

  // Sinh Idempotency Key 1 lần duy nhất khi load trang Checkout (chống double click)
  const [idempotencyKey] = useState(() => generateIdempotencyKey());

  // Bắt buộc phải có giỏ hàng mới cho vào trang này
  useEffect(() => {
    if (!isCartLoading && (!cart || !cart.items || cart.items.length === 0)) {
      alert("Giỏ hàng của bạn đang trống!");
      navigate('/cart');
    }
  }, [cart, isCartLoading, navigate]);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handlePlaceOrder = async () => {
    setIsProcessing(true);
    try {
      const payload = {
        paymentMethod: paymentMethod === 'cod' ? 'COD' : (paymentMethod === 'momo' ? 'MOMO' : paymentMethod)
      };

      const response = await checkoutApi.checkout(payload, idempotencyKey);
      
      // Nếu là COD -> Chuyển thẳng tới trang thành công
      if (payload.paymentMethod === 'COD') {
        alert('Đặt hàng thành công!');
        navigate(`/payment-result?orderId=${response.orderId}`);
      } 
      // Nếu là MoMo -> Backend sẽ trả về payUrl -> Redirect tới MoMo
      else if (payload.paymentMethod === 'MOMO' && response.payUrl) {
        window.location.href = response.payUrl;
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
    image: item.productImageUrl,
    quantity: item.quantity
  }));

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
              />
            </div>
          </div>
        </div>
      </div>
    </MainLayout>
  );
}
