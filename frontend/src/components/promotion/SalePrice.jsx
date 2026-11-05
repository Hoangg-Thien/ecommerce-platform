import { money, vietnamTime } from '../../utils/promotion';
import './promotion.css';

export default function SalePrice({ price, salePrice, discountPercent, saleEndsAt }) {
  const onSale = Number(discountPercent) > 0 && salePrice != null;
  return <span className="promotion-price">
    {onSale && <del aria-label="Giá gốc">{money(price)}</del>}
    <strong>{money(onSale ? salePrice : price)}</strong>
    {onSale && <span className="promotion-percent">Giảm {discountPercent}%</span>}
    {onSale && saleEndsAt && <small>Kết thúc: <time dateTime={saleEndsAt}>{vietnamTime(saleEndsAt)}</time> (VN)</small>}
  </span>;
}
