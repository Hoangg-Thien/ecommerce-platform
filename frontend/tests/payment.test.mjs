import test from 'node:test';
import assert from 'node:assert/strict';
import { paymentOutcome, paymentMethodLabel, paymentSummary, restrictVnpayTransitions, canRetryVnpay } from '../src/utils/payment.js';

test('COD is order acceptance, not evidence of online payment', () => {
  assert.equal(paymentOutcome({ paymentMethod: 'COD', paymentStatus: 'PENDING' }), 'COD');
});
test('paid canceled and reconciliation require separate handling', () => {
  assert.equal(paymentOutcome({ paymentMethod: 'VNPAY', paymentStatus: 'PAID', orderStatus: 'CANCELLED' }), 'MANUAL');
  assert.equal(paymentOutcome({ paymentMethod: 'VNPAY', paymentStatus: 'PENDING', refundStatus: 'RECONCILIATION_REQUIRED' }), 'MANUAL');
});
test('only backend payment data determines success or failure', () => {
  assert.equal(paymentOutcome({ paymentMethod: 'VNPAY', paymentStatus: 'PAID' }), 'PAID');
  assert.equal(paymentOutcome({ paymentMethod: 'VNPAY', paymentStatus: 'FAILED' }), 'FAILED');
  assert.equal(paymentOutcome({ paymentMethod: 'VNPAY', paymentStatus: 'PENDING', vnp_ResponseCode: '00' }), 'PENDING');
});

test('method and reconciliation labels never confuse VNPay with COD or pending with failed', () => {
  assert.equal(paymentMethodLabel('VNPAY'), 'VNPay Sandbox');
  assert.match(paymentMethodLabel('MOMO'), /MoMo/);
  for (const paymentStatus of ['REFUNDING', 'REFUNDED'])
    assert.match(paymentSummary({ paymentMethod: 'MOMO', paymentStatus }), /MoMo \(mô phỏng\).*mô phỏng hoàn tiền/);
  assert.match(paymentSummary({ paymentMethod: 'VNPAY', paymentStatus: 'PENDING', refundStatus: 'RECONCILIATION_REQUIRED' }), /Chưa xác minh/);
  assert.match(paymentSummary({ paymentMethod: 'VNPAY', paymentStatus: 'PAID', refundStatus: 'REQUIRED_MANUAL' }), /Đã thanh toán.*thủ công/);
});
test('VNPay admin options cannot bypass awaiting or unproven stock states', () => {
  assert.deepEqual(restrictVnpayTransitions({ paymentMethod: 'VNPAY', status: 'AWAITING_PAYMENT', paymentStatus: 'PENDING' }, ['CONFIRMED','CANCELLED']), ['CANCELLED']);
  assert.deepEqual(restrictVnpayTransitions({ paymentMethod: 'VNPAY', status: 'PENDING' }, ['CANCELLED']), []);
  assert.deepEqual(restrictVnpayTransitions({ paymentMethod: 'VNPAY', status: 'CONFIRMED', paymentStatus: 'PAID', paidAt: '2026-11-05T00:00:00Z', refundStatus: 'RECONCILIATION_REQUIRED' }, ['SHIPPING','CANCELLED']), ['CANCELLED']);
  assert.deepEqual(restrictVnpayTransitions({ paymentMethod: 'COD', status: 'PENDING' }, ['CONFIRMED','CANCELLED']), ['CONFIRMED','CANCELLED']);
});
test('retry availability respects deadline, paid evidence and terminal order state', () => {
  const order = { paymentMethod: 'VNPAY', status: 'AWAITING_PAYMENT', paymentStatus: 'PENDING', expiresAt: '2026-11-05T00:15:00Z' };
  const now = Date.parse('2026-11-05T00:00:00Z');
  assert.equal(canRetryVnpay(order, now), true);
  assert.equal(canRetryVnpay(order, now + 900000), false);
  assert.equal(canRetryVnpay({ ...order, status: 'CANCELLED' }, now), false);
  assert.equal(canRetryVnpay({ ...order, paidAt: '2026-11-05T00:00:00Z' }, now), false);
});
