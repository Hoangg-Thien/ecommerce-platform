export function paymentOutcome(payment) {
  if (payment.paymentMethod === 'COD') return 'COD';
  if (payment.refundStatus === 'REQUIRED_MANUAL' || payment.refundStatus === 'RECONCILIATION_REQUIRED'
      || payment.orderStatus === 'CANCELLED' && payment.paymentStatus === 'PAID') return 'MANUAL';
  if (payment.paymentStatus === 'PAID') return 'PAID';
  if (payment.paymentStatus === 'FAILED') return 'FAILED';
  return 'PENDING';
}

export const paymentMethodLabel = method => ({ COD: 'COD', MOMO: 'MoMo (mô phỏng)', VNPAY: 'VNPay Sandbox' }[method] || method || 'Chưa rõ');

export function paymentSummary(payment) {
  const paid = payment.paymentStatus === 'PAID';
  if (payment.paymentMethod === 'COD') return paid ? 'COD · Đã ghi nhận thanh toán' : 'COD · Thanh toán khi nhận hàng';
  if (payment.paymentMethod === 'MOMO' && payment.paymentStatus === 'REFUNDED') return 'MoMo (mô phỏng) · Đã mô phỏng hoàn tiền';
  if (payment.paymentMethod === 'MOMO' && payment.paymentStatus === 'REFUNDING') return 'MoMo (mô phỏng) · Đang mô phỏng hoàn tiền';
  if (payment.refundStatus === 'REQUIRED_MANUAL' || payment.paymentMethod === 'VNPAY' && paid && payment.status === 'CANCELLED')
    return 'Đã thanh toán · Cần xử lý thủ công';
  if (payment.refundStatus === 'RECONCILIATION_REQUIRED') return paid ? 'Đã thanh toán · Cần đối soát' : 'Chưa xác minh thanh toán · Cần đối soát';
  if (paid) return 'Đã thanh toán';
  return payment.paymentStatus === 'FAILED' ? 'Thanh toán thất bại' : 'Chờ kết quả thanh toán';
}

export function restrictVnpayTransitions(order, options) {
  if (order.paymentMethod !== 'VNPAY') return options;
  if (order.status === 'AWAITING_PAYMENT') return order.paymentStatus === 'PENDING' && !order.paidAt ? options.filter(s => s === 'CANCELLED') : [];
  if (order.status === 'PENDING' || order.paymentStatus !== 'PAID' || !order.paidAt) return [];
  return order.refundStatus === 'NONE' ? options : options.filter(s => s === 'CANCELLED');
}

export function canRetryVnpay(order, now = Date.now()) {
  return order.paymentMethod === 'VNPAY' && order.status === 'AWAITING_PAYMENT' && order.paymentStatus === 'PENDING'
    && !order.paidAt && Boolean(order.expiresAt) && Date.parse(order.expiresAt) > now;
}
