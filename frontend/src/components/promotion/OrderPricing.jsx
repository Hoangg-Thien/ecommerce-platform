import { money } from '../../utils/promotion';
import './promotion.css';

export default function OrderPricing({ order }) {
  return <dl className="promotion-breakdown" aria-label="Giá đã chốt khi đặt hàng">
    <div><dt>Tạm tính sau giảm sản phẩm</dt><dd>{money(order.subtotalPrice)}</dd></div>
    {Number(order.discountAmount) > 0 && <div>
      <dt>{order.orderDiscountSource === 'VOUCHER' ? `Mã ${order.voucherCode}` : 'Ưu đãi tự động'} ({order.orderDiscountPercent}%)</dt>
      <dd>−{money(order.discountAmount)}</dd>
    </div>}
    <div><dt>Phí vận chuyển</dt><dd>{money(order.shippingFee)}</dd></div>
  </dl>;
}
