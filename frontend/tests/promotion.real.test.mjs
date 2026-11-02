import test from 'node:test';
import assert from 'node:assert/strict';
import { browser, until, delay } from './browser.mjs';

test('real Spring Boot + PostgreSQL + React promotion acceptance', { timeout: 140000 }, async t => {
  const base = process.env.VITE_API_BASE_URL;
  assert.match(base || '', /^http:\/\/127\.0\.0\.1:\d+\/api\/v1$/); // supplied only by the isolated JUnit harness
  const b = await browser(); t.after(b.close);
  const { evaluate: js } = b;
  const exceptions = [];
  let refreshRequests = 0;
  await b.cdp.send('Network.enable');
  b.cdp.on('Network.requestWillBeSent', ({ request }) => {
    if (request.method === 'POST' && request.url.endsWith('/auth/refresh')) refreshRequests++;
  });
  b.cdp.on('Runtime.exceptionThrown', e => exceptions.push(e.exceptionDetails));
  const waitText = value => until(async () => (await js('document.body.innerText')).includes(value), value);
  const click = async label => {
    const selector = `[...document.querySelectorAll('button')].find(b => b.textContent.trim() === ${JSON.stringify(label)} && !b.disabled)`;
    await until(() => js(`Boolean(${selector})`), label); await js(`${selector}.click()`);
  };
  const fill = (selector, value) => js(`(() => { const e=document.querySelector(${JSON.stringify(selector)}); if(!e) throw Error('Missing input'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,${JSON.stringify(value)}); e.dispatchEvent(new Event('input',{bubbles:true})); })()`);
  await b.navigate('/login'); await until(() => js('Boolean(document.querySelector("input[type=email]"))'), 'login');
  await fill('input[type=email]', 'browser@test.local'); await fill('input[type=password]', 'BrowserDemo123!');
  await click('Đăng nhập'); await waitText('E2E shoe');
  // Use the same authenticated frontend client for setup/assertions; all calls are real HTTP, none intercepted.
  const api = (method, path, body, extra = {}) => js(`(async () => {
    const {default: client}=await import('/src/api/axiosClient.js');
    try { return { status:200, data:await client({method:${JSON.stringify(method)},url:${JSON.stringify(path)},data:${JSON.stringify(body)},...${JSON.stringify(extra)}}) }; }
    catch(e) { return {status:e.response?.status,data:e.response?.data}; }
  })()`);
  const ok = async (...args) => { const r = await api(...args); assert.equal(r.status, 200, JSON.stringify(r)); return r.data; };
  const shipping = async () => {
    for (const [name, value] of Object.entries({ email:'browser@test.local', firstName:'Test', lastName:'Buyer', address:'Street', ward:'Ward', city:'City', phone:'0123456789' })) await fill(`[name="${name}"]`, value);
  };
  const checkoutPage = async (voucher = '') => {
    const before = refreshRequests;
    await b.navigate('/checkout'); await waitText('Tiền hàng gốc'); await shipping();
    assert.equal(refreshRequests - before, 1, 'StrictMode reload must share exactly one refresh');
    await js("document.querySelector('#payment-momo').click()");
    if (voucher) { await fill('#voucher-code', voucher); await click('Áp dụng'); await waitText('Đã áp dụng mã ' + voucher); }
  };
  const place = async (label = 'Hoàn tất đặt hàng') => {
    await click(label); await waitText('Môi trường mô phỏng');
    return Number(await js('new URLSearchParams(location.search).get("orderId")'));
  };
  const add = () => ok('post', '/carts/add', { variantId:1, quantity:1 });

  const before = refreshRequests;
  assert.equal(await js(`(async()=>{
    const {default:auth}=await import('/src/api/authApi.js');
    const {default:client,setAccessToken}=await import('/src/api/axiosClient.js');
    setAccessToken('expired-test-access-token');
    await Promise.all([auth.refreshToken(), auth.refreshToken(), client.get('/carts'), client.get('/orders')]);
    return true;
  })()`), true);
  assert.equal(refreshRequests - before, 1, 'real session restore + concurrent unauthorized requests share one rotation');
  console.log('PASS real simultaneous refresh with rotation and retained session');

  // No promotion COD through the browser, including real stock/cart updates.
  await add(); await checkoutPage(); await js("document.querySelector('#payment-cod').click()");
  await click('Hoàn tất đặt hàng'); await until(() => js('location.pathname === "/payment-result"'), 'COD result');
  const codId = Number(await js('new URLSearchParams(location.search).get("orderId")'));
  assert.equal((await ok('get', '/orders/' + codId)).totalPrice, 230000);
  assert.equal((await ok('get', '/carts')).items.length, 0);
  console.log('PASS no-promotion COD, backend shipping, stock/cart');

  // Real admin UI creates program; child rules created via the same real admin API.
  await b.navigate('/admin/promotions'); await waitText('Tạo chương trình'); await click('Tạo chương trình');
  await fill('[name=name]', 'Browser promotion'); await fill('[name=startDate]', '2020-01-01T07:00'); await fill('[name=endDate]', '2099-01-01T07:00');
  await click('Lưu chương trình'); await waitText('Thêm voucher');
  const program = (await ok('get', '/admin/promotions')).content[0];
  const root = '/admin/promotions/' + program.id;
  const rules = await ok('post', root + '/product-discounts', { productIds:[1], discountPercent:20 });
  const tier = await ok('post', root + '/order-discounts', { minimumAmount:0, discountPercent:5 });
  const voucher = await ok('post', root + '/vouchers', { code:'SALE10', minimumAmount:0, discountPercent:10, maximumDiscount:30000, quantity:3 });
  await add(); await b.navigate('/cart'); await waitText('Giảm 20%');
  assert.equal(await js('document.querySelector(".promotion-price time").dateTime'), '2099-01-01T00:00:00Z');
  await checkoutPage('SALE10');
  // Simulate another admin editing a live price after preview, while this browser is idle.
  await ok('put', root + '/product-discounts/' + rules[0].id, { discountPercent:15 });
  await click('Hoàn tất đặt hàng'); await waitText('Xác nhận lại và đặt hàng');
  assert.equal((await ok('get', '/orders')).totalElements, 1, '409 created no order');
  const first = await place('Xác nhận lại và đặt hàng');
  assert.equal((await ok('get', '/orders/' + first)).totalPrice, 183000);
  await ok('put', root + '/product-discounts/' + rules[0].id, { discountPercent:25 });
  assert.equal((await ok('get', '/orders/' + first)).items[0].price, 170000, 'old snapshot stays 15%');
  await click('Thanh toán thành công');
  await until(async () => (await ok('get', '/payments/order/' + first)).paymentStatus === 'PAID', 'mock success');
  await ok('patch', '/admin/orders/' + first + '/status', { orderStatus:'CANCELLED' });
  assert.equal((await ok('get', '/payments/order/' + first)).paymentStatus, 'PAID', 'paid mock cancellation keeps payment history');
  assert.equal((await ok('get', root)).vouchers[0].usedQuantity, 0);
  console.log('PASS real admin, sale/cart timezone, PRICE_CHANGED, snapshots, SUCCESS and paid cancellation');

  await add(); await checkoutPage('SALE10'); const failed = await place();
  await click('Thanh toán thất bại');
  await until(async () => (await ok('get', '/orders/' + failed)).status === 'CANCELLED', 'mock fail');
  assert.equal((await ok('get', root)).vouchers[0].usedQuantity, 0);
  await checkoutPage('SALE10'); const pending = await place();
  await click('Đang xử lý (Pending)'); await delay(200);
  assert.equal((await ok('get', '/orders/' + pending)).status, 'AWAITING_PAYMENT');
  await ok('patch', '/admin/orders/' + pending + '/status', { orderStatus:'CANCELLED' });
  const late = await api('post', '/mock-payments/' + pending + '/simulate', { scenario:'SUCCESS' });
  assert.equal(late.status, 400);
  assert.equal((await ok('get', '/payments/order/' + pending)).paymentStatus, 'FAILED');
  assert.equal((await ok('get', root)).vouchers[0].usedQuantity, 0);
  assert.equal((await api('delete', root + '/vouchers/' + voucher.id)).status, 409);
  assert.equal((await api('delete', root + '/order-discounts/' + tier.id)).status, 200); // unused tier
  await ok('patch', root + '/status', { status:'INACTIVE' });
  const plain = await ok('get', '/products/1'); assert.equal(plain.salePrice, 200000); assert.equal(plain.saleEndsAt, null);
  assert.equal((await ok('get', '/carts')).items[0].saleEndsAt, null);
  console.log('PASS FAIL/PENDING, voucher reuse/release, late SUCCESS guard, history409, live cache after disable');
  assert.deepEqual(exceptions, []);
});
