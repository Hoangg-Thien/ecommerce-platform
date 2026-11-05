import test from 'node:test';
import assert from 'node:assert/strict';
import { browser, until } from './browser.mjs';

// HTTP fixtures only: this verifies UI contracts, never the VNPay gateway or a live database.
test('VNPay Phase 2 review and order browser contracts', { timeout: 120000 }, async t => {
  const b = await browser(); t.after(b.close);
  const { cdp, evaluate: js } = b;
  const requests = [], exceptions = [];
  const base = { id: 42, userEmail: 'fixture@test.local', totalPrice: 9000, subtotalPrice: 10000,
    discountAmount: 1000, shippingFee: 0, paymentMethod: 'VNPAY', paymentStatus: 'PENDING',
    refundStatus: 'NONE', status: 'AWAITING_PAYMENT', expiresAt: '2000-01-01T00:00:00Z',
    createdAt: '2026-11-05T00:00:00Z', items: [] };
  let order = { ...base };
  let loads = 0;
  cdp.on('Page.loadEventFired', () => loads++);
  cdp.on('Runtime.exceptionThrown', e => exceptions.push(e.exceptionDetails));
  await cdp.send('Page.addScriptToEvaluateOnNewDocument', { source: "localStorage.setItem('user', JSON.stringify({id:1,email:'fixture@test.local',role:'ADMIN'}));" });
  await cdp.send('Fetch.enable', { patterns: [{ urlPattern: '*://localhost:8080/*' }] });
  cdp.on('Fetch.requestPaused', async ({ request, requestId }) => {
    try {
      const url = new URL(request.url), path = url.pathname.replace('/api/v1', '');
      requests.push({ path, query: Object.fromEntries(url.searchParams), method: request.method });
      let data = { content: [], totalPages: 0 };
      if (path === '/auth/refresh') data = { accessToken: 'fixture-token' };
      else if (path === '/carts') data = { items: [], totalPrice: 0 };
      else if (path === '/categories') data = [];
      else if (path === '/admin/orders' || path === '/orders') data = { content: [order], totalElements: 1, totalPages: 1, pageNo: 0 };
      else if (path === '/admin/orders/42') data = order;
      else if (path === '/payments/order/42') data = { ...order, orderStatus: order.status };
      await cdp.send('Fetch.fulfillRequest', { requestId, responseCode: 200,
        responseHeaders: [{ name: 'Content-Type', value: 'application/json' }, { name: 'Access-Control-Allow-Origin', value: 'http://127.0.0.1:5179' },
          { name: 'Access-Control-Allow-Credentials', value: 'true' }, { name: 'Access-Control-Allow-Headers', value: 'Content-Type, Authorization' },
          { name: 'Access-Control-Allow-Methods', value: 'GET,OPTIONS' }], body: Buffer.from(JSON.stringify(data)).toString('base64') });
    } catch (e) { if (!/Invalid InterceptionId|Invalid requestId/.test(e.message)) exceptions.push(e.message); }
  });
  const go = async path => { const before = loads; await b.navigate(path); await until(() => loads > before, 'page load'); };
  const text = value => until(async () => (await js('document.body.innerText')).includes(value), value);
  const openAdmin = async () => {
    await go('/admin/orders');
    await until(() => js('Boolean(document.querySelector(".admin-table__action-btn"))'), 'admin data');
    await js('document.querySelector(".admin-table__action-btn").click()');
    await until(() => js('Boolean(document.querySelector(".order-detail-modal"))'), 'admin detail');
  };
  await t.test('admin cannot confirm awaiting VNPay; filters are sent together', async () => {
    await openAdmin();
    assert.deepEqual(await js('[...document.querySelector(".order-detail-modal__status-select").options].map(e=>e.value)'), ['AWAITING_PAYMENT', 'CANCELLED']);
    assert.equal(await js('document.querySelector("#btn-update-order-status").disabled'), true);
    await js('document.querySelector(".admin-modal__close").click()');
    for (const [label, value] of [['Lọc phương thức thanh toán', 'VNPAY'], ['Lọc trạng thái thanh toán', 'PENDING'], ['Lọc đối soát', 'RECONCILIATION_REQUIRED']]) {
      await js(`(() => {const e=document.querySelector('select[aria-label="${label}"]'); e.value='${value}'; e.dispatchEvent(new Event('change',{bubbles:true}));})()`);
    }
    await until(() => requests.some(r => r.path === '/admin/orders' && r.query.paymentMethod === 'VNPAY' && r.query.paymentStatus === 'PENDING' && r.query.refundStatus === 'RECONCILIATION_REQUIRED'), 'combined filters');
  });
  await t.test('paid canceled details show manual handling and original gateway evidence', async () => {
    order = { ...base, status: 'CANCELLED', paymentStatus: 'PAID', paidAt: '2026-11-05T00:00:01Z',
      refundStatus: 'REQUIRED_MANUAL', reason: 'LATE_PAID_AFTER_CANCEL', providerTxnRef: 'REVIEW42', providerTransactionNo: '123456', responseCode: '00', transactionStatus: '00' };
    await openAdmin(); await text('Đã thanh toán · Cần xử lý thủ công'); await text('Chưa có xác nhận hoàn tiền'); await text('REVIEW42');
    assert.equal(await js('document.querySelector(".order-detail-modal__status-select") === null'), true);
    for (const width of [1440, 390]) {
      await cdp.send('Emulation.setDeviceMetricsOverride', { width, height: 900, deviceScaleFactor: 1, mobile: false });
      const size = await js('(() => {const r=document.querySelector(".admin-modal").getBoundingClientRect();return {left:r.left,right:r.right,width:innerWidth};})()');
      assert.ok(size.left >= -1 && size.right <= size.width + 1, JSON.stringify(size));
    }
  });
  await t.test('customer sees payment independently; canceled paid never retries', async () => {
    await go('/orders'); await text('Xem chi tiết');
    await js('[...document.querySelectorAll("button")].find(e=>e.textContent.trim()==="Xem chi tiết").click()');
    await text('VNPay Sandbox'); await text('Đã thanh toán · Cần xử lý thủ công'); await text('Chưa có xác nhận hoàn tiền');
    assert.equal(await js('document.body.innerText.includes("Mở lại VNPay Sandbox")'), false);
    await js('[...document.querySelectorAll("button")].find(e=>e.textContent.trim()==="Kiểm tra thanh toán").click()');
    await text('Đã thanh toán, cần xử lý thủ công');
  });
  await t.test('expired pending order can check results but cannot reopen payment', async () => {
    order = { ...base }; await go('/orders'); await text('Xem chi tiết');
    await js('[...document.querySelectorAll("button")].find(e=>e.textContent.trim()==="Xem chi tiết").click()');
    await text('Hạn thanh toán'); await text('Chờ kết quả thanh toán');
    assert.equal(await js('document.body.innerText.includes("Mở lại VNPay Sandbox")'), false);
    assert.equal(await js('document.body.innerText.includes("Kiểm tra thanh toán")'), true);
    assert.equal(requests.some(r => r.path.endsWith('/retry-payment')), false);
  });
  await t.test('legacy MoMo pending is history without a retry action', async () => {
    order = { ...base, paymentMethod:'MOMO', expiresAt:null };
    await go('/orders'); await text('Xem chi tiết');
    await js('[...document.querySelectorAll("button")].find(e=>e.textContent.trim()==="Xem chi tiết").click()');
    await text('MoMo (mô phỏng)');
    assert.equal(await js('document.body.innerText.includes("Thanh toán lại")'), false);
    assert.equal(await js('document.body.innerText.includes("Mở lại VNPay")'), false);
    assert.equal(requests.some(r => r.path.endsWith('/retry-payment')), false);
  });
  assert.deepEqual(exceptions, []);
});
