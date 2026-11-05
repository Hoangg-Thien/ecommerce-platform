import { useState } from 'react';
import ProductPicker from './ProductPicker';

export default function RuleForm({ type, rule, busy, onSave, onCancel }) {
  const [form, setForm] = useState({ code: rule?.code || '', discountPercent: rule?.discountPercent ?? '',
    minimumAmount: rule?.minimumAmount ?? 0, maximumDiscount: rule?.maximumDiscount ?? '', quantity: rule?.quantity ?? 1 });
  const [ids, setIds] = useState([]);
  const change = e => setForm({ ...form, [e.target.name]: e.target.value });
  const isProduct = type === 'product-discounts';
  return <form onSubmit={event => {
    event.preventDefault();
    const data = { discountPercent: Number(form.discountPercent) };
    if (!isProduct) Object.assign(data, { minimumAmount: Number(form.minimumAmount), maximumDiscount: form.maximumDiscount === '' ? null : Number(form.maximumDiscount) });
    if (type === 'vouchers') Object.assign(data, { code: form.code.trim().toUpperCase(), quantity: Number(form.quantity) });
    if (isProduct && !rule) data.productIds = ids;
    onSave(data);
  }}>
    <fieldset disabled={busy} className="promotion-form">
      <legend>{rule ? `Sửa ưu đãi #${rule.id}` : 'Thêm ưu đãi'}</legend>
      {type === 'vouchers' && <label>Mã voucher<input name="code" required maxLength={50} value={form.code} onChange={change} /></label>}
      <label>Giảm (%)<input name="discountPercent" type="number" required min="0.01" max="100" step="0.01" value={form.discountPercent} onChange={change} /></label>
      {!isProduct && <>
        <label>Tiền hàng tối thiểu (VND)<input name="minimumAmount" type="number" required min="0" step="0.01" value={form.minimumAmount} onChange={change} /></label>
        <label>Giảm tối đa (bỏ trống nếu không giới hạn)<input name="maximumDiscount" type="number" min="0.01" step="0.01" value={form.maximumDiscount} onChange={change} /></label>
      </>}
      {type === 'vouchers' && <label>Số lượt (đã dùng/giữ: {rule?.usedQuantity ?? 0})<input name="quantity" type="number" required min={Math.max(1, rule?.usedQuantity ?? 0)} step="1" value={form.quantity} onChange={change} /></label>}
      {isProduct && !rule && <ProductPicker ids={ids} onChange={setIds} />}
      <div className="promotion-actions"><button disabled={isProduct && !rule && !ids.length}>Lưu ưu đãi</button><button type="button" onClick={onCancel}>Đóng</button></div>
    </fieldset>
  </form>;
}
