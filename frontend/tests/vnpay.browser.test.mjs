import test from 'node:test';
import assert from 'node:assert/strict';
import { browser, until, delay } from './browser.mjs';

// UI contract fixtures only; this test never calls the real Sandbox or changes an application database.
test('VNPay checkout and result browser contract', { timeout: 120000 }, async t => {
  const b = await browser(); t.after(b.close);
  const { cdp, evaluate: js } = b;
  let total = 5000, enabled = true, payment = { paymentMethod: 'VNPAY', paymentStatus: 'PENDING' }, failStatus = false, lostCheckout = false;
  const requests = [], exceptions = [], redirects = [];
  let loads = 0;
  cdp.on('Page.loadEventFired', () => loads++);
  const go = async path => { const before = loads; await b.navigate(path); await until(() => loads > before, 'page load'); };
  const url = 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=FIXTURE123';
  const cart = { items: [{ id: 1, productId: 1, variantId: 1, productName: 'Test', size: 'M', quantity: 1, productPrice: 5000, unitPrice: 5000, subTotal: 5000 }], totalPrice: 5000 };
  cdp.on('Runtime.exceptionThrown', event => exceptions.push(event.exceptionDetails));
  await cdp.send('Page.addScriptToEvaluateOnNewDocument', { source: "localStorage.setItem('user', JSON.stringify({id:1,email:'ui@test.local',role:'USER'}));" });
  await cdp.send('Fetch.enable', { patterns: [{ urlPattern: '*://localhost:8080/*' }, { urlPattern: 'https://sandbox.vnpayment.vn/*' }] });
  cdp.on('Fetch.requestPaused', async ({ request, requestId }) => {
    try {
      if (request.url.startsWith('https://sandbox.vnpayment.vn/')) {
        if (new URL(request.url).pathname === '/paymentv2/vpcpay.html') redirects.push(request.url);
        return await cdp.send('Fetch.fulfillRequest', { requestId, responseCode: 200, responseHeaders: [{ name: 'Content-Type', value: 'text/html' }], body: Buffer.from('<h1>Sandbox navigation fixture</h1>').toString('base64') });
      }
      const path = new URL(request.url).pathname.replace('/api/v1', '');
      const body = request.postData ? JSON.parse(request.postData) : {};
      requests.push({ path, method: request.method, body });
      const reply = (data, status = 200) => cdp.send('Fetch.fulfillRequest', { requestId, responseCode: status,
        responseHeaders: [{ name: 'Content-Type', value: 'application/json' }, { name: 'Access-Control-Allow-Origin', value: 'http://127.0.0.1:5179' }, { name: 'Access-Control-Allow-Credentials', value: 'true' }, { name: 'Access-Control-Allow-Headers', value: 'Content-Type, Authorization, Idempotency-Key' }, { name: 'Access-Control-Allow-Methods', value: 'GET,POST,OPTIONS' }], body: Buffer.from(JSON.stringify(data)).toString('base64') });
      if (request.method === 'OPTIONS') return await reply({});
      if (path === '/auth/refresh') return await reply({ accessToken: 'fixture-token' });
      if (path === '/carts') return await reply(cart);
      if (path === '/categories') return await reply([]);
      if (path === '/checkout/preview') {
        const amount = body.voucherCode === 'LOW' ? 4999 : total;
        return await reply({ total: amount, subtotal: amount, shippingFee: 0, originalSubtotal: amount, productDiscountTotal: 0,
          orderDiscount: { amount: 0, source: null }, items: [{ cartItemId: 1, productId: 1, quantity: 1, unitPrice: amount }],
          vnpay: { enabled, available: enabled && amount >= 5000, reason: amount < 5000 ? 'VNPay yêu cầu tổng tiền tối thiểu 5.000đ.' : null } });
      }
      if (path === '/checkout') {
        if (lostCheckout) return await cdp.send('Fetch.failRequest', { requestId, errorReason: 'TimedOut' });
        return await reply({ orderId: 42, orderStatus: body.paymentMethod === 'COD' ? 'PENDING' : 'AWAITING_PAYMENT', paymentUrl: body.paymentMethod === 'COD' ? null : url }, 201);
      }
      if (path === '/checkout/result') return await reply({ id: 42, paymentMethod: 'VNPAY', status: 'AWAITING_PAYMENT' });
      if (path === '/orders/42/retry-payment') return await reply({ orderId: 42, paymentUrl: url });
      if (path.startsWith('/payments/order/')) {
        if (failStatus) return await cdp.send('Fetch.failRequest', { requestId, errorReason: 'Failed' });
        return await reply(payment);
      }
      return await reply({ content: [], totalPages: 0 });
    } catch (e) { if (!/Invalid InterceptionId|Invalid requestId/.test(e.message)) exceptions.push(e.message); }
  });
  const waitText = value => until(async () => (await js('document.body.innerText')).includes(value), value);
  const fill = (selector, value) => js(`(() => { const e=document.querySelector(${JSON.stringify(selector)}); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,${JSON.stringify(value)}); e.dispatchEvent(new Event('input',{bubbles:true})); })()`);
  const click = async label => {
    const expr = `[...document.querySelectorAll('button')].find(e=>e.textContent.trim()===${JSON.stringify(label)}&&!e.disabled)`;
    await until(() => js(`Boolean(${expr})`), label); await js(`${expr}.click()`);
  };
  const checkout = async () => {
    await go('/checkout'); await waitText('VNPay Sandbox');
    await until(() => js('Boolean(document.querySelector("#voucher-code"))'), 'quote');
    for (const name of ['email', 'firstName', 'lastName', 'address', 'city', 'ward', 'phone']) await fill(`[name="${name}"]`, name === 'email' ? 'ui@test.local' : name === 'phone' ? '0123456789' : 'Test');
  };

  await t.test('disabled VNPay never renders a MoMo fallback and COD remains available', async () => {
    enabled = false; await checkout();
    assert.equal(await js('document.querySelector("#payment-vnpay").disabled'), true);
    assert.equal(await js('document.querySelector("#payment-momo") === null'), true);
    assert.equal(await js('document.querySelector("#payment-cod").disabled'), false);
    await waitText('VNPay hiện chưa khả dụng'); enabled = true;
  });
  await t.test('minimum 0, 4999, 5000 disables only VNPay and explains why', async () => {
    for (const value of [0, 4999, 5000]) {
      total = value; await checkout();
      assert.equal(await js('document.querySelector("#payment-vnpay").disabled'), value < 5000);
      assert.equal(await js('document.querySelector("#payment-momo") === null'), true);
      assert.equal(await js('document.querySelector("#payment-cod").disabled'), false);
      if (value < 5000) await waitText('tối thiểu 5.000');
    }
  });
  await t.test('price reduction after selection blocks submit without changing method', async () => {
    await js('document.querySelector("#payment-vnpay").click()');
    await fill('#voucher-code', 'LOW'); await click('Áp dụng'); await waitText('Vui lòng chọn lại phương thức');
    assert.equal(await js('document.querySelector("#payment-vnpay").checked'), true);
    assert.equal(await js('document.querySelector("#payment-cod").checked'), false);
    assert.equal(await js('[...document.querySelectorAll("button")].find(e=>e.textContent.includes("Hoàn tất đặt hàng")).disabled'), true);
  });
  await t.test('COD acceptance is distinct from online payment', async () => {
    total = 5000; payment = { paymentMethod: 'COD', paymentStatus: 'PENDING' };
    await checkout(); await js('document.querySelector("#payment-cod").click()'); await click('Hoàn tất đặt hàng');
    await waitText('Đặt hàng COD thành công');
    assert.equal(requests.filter(r => r.path === '/checkout' && r.method === 'POST').at(-1).body.paymentMethod, 'COD');
  });
  await t.test('VNPay redirects to the unchanged backend URL', async () => {
    await checkout(); await js('document.querySelector("#payment-vnpay").click()'); await click('Hoàn tất đặt hàng');
    await until(() => redirects.length === 1, 'Sandbox redirect'); assert.equal(redirects[0], url);
  });
  await t.test('lost response uses key lookup and same-session retry, no second checkout', async () => {
    lostCheckout = true;
    await checkout(); await js('document.querySelector("#payment-vnpay").click()'); await click('Hoàn tất đặt hàng');
    await waitText('Kiểm tra kết quả đặt hàng');
    const count = requests.filter(r => r.path === '/checkout' && r.method === 'POST').length;
    await click('Kiểm tra kết quả đặt hàng'); await until(() => redirects.length === 2, 'recovered redirect');
    assert.equal(redirects[1], url);
    assert.equal(requests.filter(r => r.path === '/checkout' && r.method === 'POST').length, count);
  });
  await t.test('Return query does not confirm payment and unmount stops polls', async () => {
    payment = { paymentMethod: 'VNPAY', paymentStatus: 'PENDING' };
    await go('/payment-result?orderId=42&vnp_ResponseCode=00'); await waitText('Đang xử lý giao dịch');
    await click('Xem đơn hàng'); await delay(300);
    const count = requests.filter(r => r.path === '/payments/order/42').length;
    await delay(2300); assert.equal(requests.filter(r => r.path === '/payments/order/42').length, count);
  });
  await t.test('legacy MoMo paid result keeps its mock identity', async () => {
    payment = { paymentMethod: 'MOMO', paymentStatus: 'PAID' };
    await go('/payment-result?orderId=42'); await waitText('Kết quả MoMo mô phỏng');
    assert.equal((await js('document.body.innerText')).includes('VNPay đã xác nhận'), false);
  });
  await t.test('backend PAID/canceled is manual; network errors do not say failed', async () => {
    payment = { paymentMethod: 'VNPAY', paymentStatus: 'PAID', orderStatus: 'CANCELLED', refundStatus: 'REQUIRED_MANUAL' };
    await go('/payment-result?orderId=42'); await waitText('Đã thanh toán, cần xử lý thủ công');
    failStatus = true; await go('/payment-result?orderId=43'); await waitText('Đang xử lý giao dịch'); await delay(500);
    assert.equal((await js('document.body.innerText')).includes('Thanh toán thất bại'), false);
  });
  assert.deepEqual(exceptions, []);
});
