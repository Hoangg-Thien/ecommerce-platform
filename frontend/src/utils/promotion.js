export const money = value => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value ?? 0);
export const vietnamTime = value => new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short',
}).format(new Date(value));
export const voucherBlocksCheckout = voucher => Boolean(voucher?.reason && voucher.reason !== 'NOT_BETTER_THAN_ORDER_DISCOUNT');
export const apiError = error => {
  const data = error.response?.data;
  return data?.message || (data?.messages && Object.values(data.messages).join('. ')) || 'Không thể thực hiện. Vui lòng thử lại.';
};
