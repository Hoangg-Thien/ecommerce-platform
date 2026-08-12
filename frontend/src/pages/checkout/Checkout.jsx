import { useState } from 'react';
import MainLayout from '../../components/layout/MainLayout';
import CheckoutForm from '../../components/checkout/CheckoutForm';
import CheckoutSummary from '../../components/checkout/CheckoutSummary';
import './Checkout.css';

// Dữ liệu giỏ hàng giả lập cho trang thanh toán (có thể lấy từ context/state management sau này)
const MOCK_CHECKOUT_ITEMS = [
  {
    id: 1,
    name: 'Áo Khoác Nam Thể Thao Đa Năng',
    price: 450000,
    image: 'https://images.unsplash.com/photo-1556821840-3a63f95609a7?q=80&w=600&auto=format&fit=crop',
    quantity: 1
  },
  {
    id: 2,
    name: 'Giày Chạy Bộ Nam Siêu Nhẹ',
    price: 1200000,
    image: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?q=80&w=600&auto=format&fit=crop',
    quantity: 2
  }
];

export default function Checkout() {
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
  const [paymentMethod, setPaymentMethod] = useState('cod');
  const [isProcessing, setIsProcessing] = useState(false);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleCompleteOrder = () => {
    setIsProcessing(true);
    // Giả lập gọi API đặt hàng
    setTimeout(() => {
      setIsProcessing(false);
      alert('Đặt hàng thành công!');
    }, 2000);
  };

  const subtotal = MOCK_CHECKOUT_ITEMS.reduce((sum, item) => sum + (item.price * item.quantity), 0);
  const shippingFee = shippingMethod === 'express' ? 30000 : 0;

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
                cartItems={MOCK_CHECKOUT_ITEMS}
                subtotal={subtotal}
                shippingFee={shippingFee}
                onCompleteOrder={handleCompleteOrder}
                isProcessing={isProcessing}
              />
            </div>
          </div>
        </div>
      </div>
    </MainLayout>
  );
}
