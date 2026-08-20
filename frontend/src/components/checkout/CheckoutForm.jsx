import { Wallet, Truck } from 'lucide-react';
import Input from '../ui/Input';
import RadioBox from '../ui/RadioBox';
import './CheckoutForm.css';

export default function CheckoutForm({ formData, onChange, shippingMethod, setShippingMethod, paymentMethod, setPaymentMethod }) {
  
  return (
    <div className="checkout-form-container">
      {/* 1. Thông tin giao hàng */}
      <section className="checkout-section">
        <div className="section-header">
          <span className="section-step">1</span>
          <h2 className="section-title">Thông tin giao hàng</h2>
        </div>
        
        <div className="section-content">
          <div className="form-grid">
            <div className="form-group-full">
              <Input
                label="Email liên hệ"
                name="email"
                type="email"
                placeholder="ban@example.com"
                value={formData.email}
                onChange={onChange}
              />
            </div>
            
            <div className="form-group-half">
              <Input
                label="Họ"
                name="firstName"
                placeholder="Họ của bạn"
                value={formData.firstName}
                onChange={onChange}
              />
            </div>
            
            <div className="form-group-half">
              <Input
                label="Tên"
                name="lastName"
                placeholder="Tên của bạn"
                value={formData.lastName}
                onChange={onChange}
              />
            </div>
            
            <div className="form-group-full">
              <Input
                label="Địa chỉ"
                name="address"
                placeholder="Số nhà, Tên đường..."
                value={formData.address}
                onChange={onChange}
              />
            </div>

            <div className="form-group-half">
              <Input
                label="Phường/Xã"
                name="ward"
                placeholder="Tên phường/xã"
                value={formData.ward}
                onChange={onChange}
              />
            </div>
            
            <div className="form-group-half">
              <Input
                label="Thành phố"
                name="city"
                placeholder="Tên thành phố"
                value={formData.city}
                onChange={onChange}
              />
            </div>
            {}
            
            <div className="form-group-full">
              <Input
                label="Số điện thoại"
                name="phone"
                type="tel"
                placeholder="Số điện thoại để cập nhật đơn hàng"
                value={formData.phone}
                onChange={onChange}
              />
            </div>
          </div>
        </div>
      </section>

      {/* 2. Phương thức vận chuyển */}
      <section className="checkout-section">
        <div className="section-header">
          <span className="section-step">2</span>
          <h2 className="section-title">Phương thức vận chuyển</h2>
        </div>
        
        <div className="section-content">
          <RadioBox
            id="shipping-standard"
            name="shippingMethod"
            value="standard"
            checked={shippingMethod === 'standard'}
            onChange={(e) => setShippingMethod(e.target.value)}
            label="Giao hàng tiêu chuẩn"
            description="3-5 Ngày làm việc"
            priceLabel="Miễn phí"
          />
          <RadioBox
            id="shipping-express"
            name="shippingMethod"
            value="express"
            checked={shippingMethod === 'express'}
            onChange={(e) => setShippingMethod(e.target.value)}
            label="Giao hàng hỏa tốc"
            description="1-2 Ngày làm việc"
            priceLabel="30.000 ₫"
          />
        </div>
      </section>

      {/* 3. Thanh toán */}
      <section className="checkout-section">
        <div className="section-header">
          <span className="section-step">3</span>
          <h2 className="section-title">Thanh toán</h2>
        </div>
        
        <div className="section-content">
          {/* Đã bỏ Credit / Debit Card theo yêu cầu */}
          <RadioBox
            id="payment-momo"
            name="paymentMethod"
            value="momo"
            checked={paymentMethod === 'momo'}
            onChange={(e) => setPaymentMethod(e.target.value)}
            label="Ví điện tử MoMo"
            icon={<Wallet size={20} />}
          />
          
          <RadioBox
            id="payment-cod"
            name="paymentMethod"
            value="cod"
            checked={paymentMethod === 'cod'}
            onChange={(e) => setPaymentMethod(e.target.value)}
            label="Thanh toán khi nhận hàng (COD)"
            icon={<Truck size={20} />}
          />
        </div>
      </section>
    </div>
  );
}
