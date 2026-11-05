import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { browser, delay, until } from './browser.mjs';

// Contract fixtures isolate UI behavior; PostgreSQL semantics are tested by backend integration tests.
const image = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" fill="gray"/></svg>';
const product = { id: 1, name: 'Giày sale', price: 200000, salePrice: 160000, discountPercent: 20, saleEndsAt: '2030-01-02T00:00:00Z', imageUrl: image, stock: 10, variants: [{ id: 1, size: 'M', stock: 10 }] };
const plain = { ...product, id: 2, name: 'Giày thường', salePrice: 200000, discountPercent: 0, saleEndsAt: null };
const cart = { items: [product, plain].map(p => ({ id: p.id, productId: p.id, variantId: p.id, productName: p.name, imageUrl: image, size: 'M', quantity: 1, productPrice: p.price, unitPrice: p.salePrice, subTotal: p.salePrice, originalSubTotal: p.price, productDiscountPercent: p.discountPercent, saleEndsAt: p.saleEndsAt })), totalPrice: 360000, originalTotalPrice: 400000 };
function quote(code = '', total = 390000) {
  const voucher = !code ? null : code === 'BAD' || code === 'SOLD' ? { code, applied: false, reason: code === 'BAD' ? 'NOT_FOUND' : 'SOLD_OUT', message: 'Mã không hợp lệ hoặc hết lượt.' } : code === 'AUTO' ? { code, applied: false, reason: 'NOT_BETTER_THAN_ORDER_DISCOUNT', message: 'Ưu đãi tự động có lợi hơn mã giảm giá.' } : { code, applied: true, reason: null };
  return { items: cart.items.map(i => ({ cartItemId: i.id, productId: i.productId, quantity: i.quantity, originalUnitPrice: i.productPrice, unitPrice: i.unitPrice, productDiscountPercent: i.productDiscountPercent, lineTotal: i.subTotal })), originalSubtotal: 400000, productDiscountTotal: 40000, subtotal: 360000,
    orderDiscount: { source: voucher?.applied ? 'VOUCHER' : code === 'AUTO' ? 'ORDER_DISCOUNT' : null, code, amount: voucher?.applied || code === 'AUTO' ? 30000 : 0, percent: 10 },
    shippingFee: 30000, total: total - (voucher?.applied || code === 'AUTO' ? 30000 : 0), voucher, vnpay: {enabled:true,available:true,reason:null} };
}

test('Promotion V1 browser acceptance (isolated API fixtures)', { timeout: 120000 }, async t => {
  const b = await browser();
  t.after(b.close);
  const { cdp, evaluate: js } = b;
  const requests = [], exceptions = [];
  let lastMethod = 'VNPAY';
  let mode = 'normal', lookup = 'found', failPreview = false, soldCode = null;
  let program = { id: 1, name: 'Sale demo', description: 'Demo', startDate: '2026-01-01T00:00:00Z', endDate: '2030-01-01T00:00:00Z', status: 'ACTIVE', phase: 'RUNNING', vouchers: [], orderDiscounts: [], productDiscounts: [] };
  const order = { id: 42, paymentMethod: 'VNPAY', paymentStatus: 'PENDING', refundStatus: 'NONE', status: 'AWAITING_PAYMENT', createdAt: '2026-11-01T10:00:00', totalPrice: 360000, subtotalPrice: 360000, discountAmount: 30000, orderDiscountSource: 'VOUCHER', orderDiscountPercent: 10, voucherCode: 'OLD10', shippingFee: 30000,
    items: [{ id: 1, productId: 1, productName: 'Giày sale', imageUrl: image, price: 160000, originalPrice: 200000, productDiscountPercent: 20, size: 'M', quantity: 1, subTotal: 160000 }] };
  cdp.on('Runtime.exceptionThrown', data => exceptions.push(data.exceptionDetails));
  cdp.on('Page.javascriptDialogOpening', () => cdp.send('Page.handleJavaScriptDialog', { accept: true }));
  await cdp.send('Page.addScriptToEvaluateOnNewDocument', { source: `if (!localStorage.getItem('user')) localStorage.setItem('user', JSON.stringify({ id: 1, email: 'ui@test.local', role: 'ADMIN' }));` });
  await cdp.send('Fetch.enable', { patterns: [{ urlPattern: '*://localhost:8080/*' }] });
  cdp.on('Fetch.requestPaused', async event => {
    const { request, requestId } = event;
    const path = new URL(request.url).pathname.replace('/api/v1', '');
    const body = request.postData ? JSON.parse(request.postData) : {};
    requests.push({ path, method: request.method, body, headers: request.headers });
    const reply = async (value, status = 200) => cdp.send('Fetch.fulfillRequest', { requestId, responseCode: status,
      responseHeaders: [{ name: 'Content-Type', value: 'application/json' }, { name: 'Access-Control-Allow-Origin', value: 'http://127.0.0.1:5179' }, { name: 'Access-Control-Allow-Credentials', value: 'true' }, { name: 'Access-Control-Allow-Headers', value: 'Content-Type, Authorization, Idempotency-Key' }, { name: 'Access-Control-Allow-Methods', value: 'GET,POST,PUT,PATCH,DELETE,OPTIONS' }], body: Buffer.from(JSON.stringify(value)).toString('base64') });
    try {
      if (request.method === 'OPTIONS') return await reply({});
      if (path === '/auth/refresh') return await reply({ accessToken: 'fixture-token' });
      if (path === '/carts') return await reply(cart);
      if (path === '/categories') return await reply([]);
      if (path === '/products') return await reply({ content: [product, plain], totalPages: 1, totalElements: 2 });
      if (path === '/products/1') return await reply(product);
      if (path === '/checkout/preview') {
        if (failPreview) return await reply({ message: 'Preview temporarily unavailable' }, 500);
        if (body.voucherCode === 'SLOW') await delay(500);
        if (body.voucherCode === soldCode) return await reply(quote('SOLD'));
        return await reply(quote(body.voucherCode));
      }
      if (path === '/checkout') {
        lastMethod=body.paymentMethod;
        if (mode === 'lost') return await cdp.send('Fetch.failRequest', { requestId, errorReason: 'TimedOut' });
        if (mode === 'changed') { mode = 'normal'; return await reply({ code: 'PRICE_CHANGED', message: 'Giá đã thay đổi', breakdown: quote(body.voucherCode, 400000) }, 409); }
        if (mode === 'sold') { mode = 'normal'; soldCode = body.voucherCode; return await reply({ code: 'VOUCHER_SOLD_OUT', message: 'Mã đã hết lượt' }, 409); }
        return await reply({ orderId: 42, orderStatus: 'AWAITING_PAYMENT', paymentUrl: '/payment-result?orderId=42' }, 201);
      }
      if (path === '/checkout/result') return await reply(lookup === 'found' ? order : { message: 'Not found' }, lookup === 'found' ? 200 : 404);
      if (path === '/orders/42/retry-payment') return await reply({orderId:42,paymentUrl:'/payment-result?orderId=42'});
      if (path === '/orders' || path === '/admin/orders') return await reply({ content: [order], totalPages: 1, totalElements: 1 });
      if (path === '/admin/orders/42') return await reply(order);
      if (path.startsWith('/payments')) return await reply({ paymentMethod:lastMethod, paymentStatus:'PENDING' });
      if (path === '/admin/promotions' && request.method === 'GET') return await reply({ content: [program], totalPages: 1 });
      if (path === '/admin/promotions' && request.method === 'POST') { program = { ...program, ...body }; return await reply(program, 201); }
      if (path === '/admin/promotions/1' && request.method === 'GET') return await reply(program);
      if (path === '/admin/promotions/1' && request.method === 'PUT') { program = { ...program, ...body }; return await reply(program); }
      if (path === '/admin/promotions/1/status') { program.status = body.status; return await reply(program); }
      if (request.method === 'DELETE') return await reply({ message: 'Promotion has history; deactivate it instead' }, 409);
      const match = path.match(/\/admin\/promotions\/1\/(vouchers|order-discounts|product-discounts)(?:\/(\d+))?(?:\/status)?$/);
      if (match) {
        const field = { vouchers: 'vouchers', 'order-discounts': 'orderDiscounts', 'product-discounts': 'productDiscounts' }[match[1]];
        if (match[2]) program[field] = program[field].map(r => r.id === Number(match[2]) ? { ...r, ...body } : r);
        else program[field].push(...(body.productIds || [null]).map((id, i) => ({ id: program[field].length + i + 1, ...body, productId: id, usedQuantity: 0, status: 'ACTIVE' })));
        return await reply(program[field], match[2] ? 200 : 201);
      }
      exceptions.push(`Unexpected API request: ${request.method} ${path}`);
      return await reply({ message: 'Unexpected API request in browser test' }, 404);
    } catch (error) { if (!/Invalid InterceptionId|Invalid requestId/.test(error.message)) exceptions.push(error.message); }
  });
  const text = () => js('document.body.innerText');
  const waitText = async value => { try { await until(async () => (await text()).includes(value), value); } catch (error) { console.log('PAGE', await text(), 'REQUESTS', requests.slice(-5)); throw error; } };
  const click = async value => {
    await until(() => js(`[...document.querySelectorAll('button')].some(b => b.textContent.trim() === ${JSON.stringify(value)} && !b.disabled)`), `button ${value}`);
    return js(`[...document.querySelectorAll('button')].find(b => b.textContent.trim() === ${JSON.stringify(value)} && !b.disabled).click()`);
  };
  const fill = (selector, value) => js(`(() => { const e = document.querySelector(${JSON.stringify(selector)}); if (!e) throw Error('Input missing'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,${JSON.stringify(value)}); e.dispatchEvent(new Event('input',{bubbles:true})); })()`);
  const navigate = async path => { await b.navigate(path); await waitText(path.includes('admin') ? 'Khuyến mãi' : ''); await delay(200); };
  const apply = async code => { await fill('#voucher-code', code); await click('Áp dụng'); };
  const form = async () => {
    for (const [key, value] of Object.entries({ email: 'ui@test.local', firstName: 'Test', lastName: 'Buyer', address: 'Street', city: 'City', ward: 'Ward', phone: '0123456789' })) await fill(`[name="${key}"]`, value);
    await js("document.querySelector('#payment-vnpay').click()");
  };
  const screenshot = async name => {
    await mkdir('../backend/target', { recursive: true });
    const { data } = await cdp.send('Page.captureScreenshot');
    await writeFile(`../backend/target/phase8-${name}.png`, Buffer.from(data, 'base64'));
  };
  await t.test('product/cart sale, null sale, VN timezone, responsive widths', async () => {
    await navigate('/'); await waitText('Giày sale');
    assert.equal(await js('document.querySelectorAll(".promotion-price del").length'), 1);
    assert.match(await text(), /07:00/);
    await navigate('/product/1'); await waitText('Giảm 20%');
    await navigate('/cart'); await waitText('Giày thường');
    assert.equal(await js('document.querySelectorAll(".promotion-price time").length'), 1);
    for (const width of [320, 768, 1024, 1440]) {
      await cdp.send('Emulation.setDeviceMetricsOverride', { width, height: 900, deviceScaleFactor: 1, mobile: false });
      await delay(300); // Wait for layout/sidebar CSS transitions before measuring.
      // Existing shared header has a separate 320px overflow; verify this phase's cart content.
      assert.ok(await js('[...document.querySelectorAll(".cart-page, .cart-page *")].every(e => e.getBoundingClientRect().right <= innerWidth + 1)'), `cart content overflow at ${width}`);
      if (width === 320) await screenshot('cart-mobile');
    }
  });
  await t.test('voucher validation, automatic winner, stale preview, retry', async () => {
    await navigate('/checkout'); await waitText('Tiền hàng gốc'); await form();
    await apply('BAD'); await waitText('Hãy bỏ mã');
    assert.equal(await js('document.querySelector(".complete-order-btn").disabled'), true);
    await apply('AUTO'); await waitText('Ưu đãi tự động có lợi');
    assert.equal(await js('document.querySelector(".complete-order-btn").disabled'), false);
    await apply('SLOW'); await apply('FAST'); await waitText('Đã áp dụng mã FAST'); await delay(650);
    assert.ok(!(await text()).includes('Đã áp dụng mã SLOW'));
    failPreview = true; await apply('ERROR'); await waitText('Preview temporarily unavailable');
    failPreview = false; await click('Thử lại giá'); await waitText('Đã áp dụng mã ERROR');
    await click('Bỏ mã'); await waitText('Tiền hàng gốc');
    for (const width of [320, 768, 1024, 1440]) {
      await cdp.send('Emulation.setDeviceMetricsOverride', { width, height: 900, deviceScaleFactor: 1, mobile: false });
      await delay(300);
      assert.ok(await js('[...document.querySelectorAll(".checkout-page, .checkout-page *")].every(e => e.getBoundingClientRect().right <= innerWidth + 1)'), `checkout overflow at ${width}`);
    }
  });
  await t.test('PRICE_CHANGED requires explicit confirmation and sends new expectedTotal', async () => {
    mode = 'changed'; const before = requests.filter(r => r.path === '/checkout' && r.method === 'POST').length;
    await click('Hoàn tất đặt hàng'); await waitText('Xác nhận lại và đặt hàng'); await delay(200);
    assert.equal(requests.filter(r => r.path === '/checkout' && r.method === 'POST').length, before + 1);
    await click('Xác nhận lại và đặt hàng'); await waitText('Đang xử lý giao dịch');
    assert.equal(requests.filter(r => r.path === '/checkout' && r.method === 'POST').at(-1).body.expectedTotal, 400000);
  });
  await t.test('SOLD_OUT refreshes breakdown, blocks invalid voucher and requires confirmation', async () => {
    await navigate('/checkout'); await waitText('Tiền hàng gốc'); await form(); await apply('LAST'); await waitText('Đã áp dụng mã LAST');
    mode = 'sold'; await click('Hoàn tất đặt hàng'); await waitText('Xác nhận lại và đặt hàng');
    assert.equal(await js('document.querySelector(".complete-order-btn").disabled'), true);
    const count = requests.filter(r => r.path === '/checkout' && r.method === 'POST').length;
    await click('Bỏ mã'); await waitText('Tiền hàng gốc');
    assert.equal(requests.filter(r => r.path === '/checkout' && r.method === 'POST').length, count);
    await click('Xác nhận lại và đặt hàng'); await waitText('Đang xử lý giao dịch');
  });
  await t.test('lost response looks up the committed order, no duplicate POST', async () => {
    await navigate('/checkout'); await waitText('Tiền hàng gốc'); await form(); mode = 'lost';
    await click('Hoàn tất đặt hàng'); await waitText('Kiểm tra kết quả đặt hàng');
    await navigate('/checkout'); await waitText('Kiểm tra kết quả đặt hàng');
    const count = requests.filter(r => r.path === '/checkout' && r.method === 'POST').length;
    await click('Kiểm tra kết quả đặt hàng'); await waitText('Đang xử lý giao dịch');
    assert.equal(requests.filter(r => r.path === '/checkout' && r.method === 'POST').length, count);
  });
  await t.test('missing key permits only confirmed retry with same key', async () => {
    await navigate('/checkout'); await waitText('Tiền hàng gốc'); await form();
    await click('Hoàn tất đặt hàng'); await waitText('Kiểm tra kết quả đặt hàng'); lookup = 'missing';
    const first = requests.filter(r => r.path === '/checkout' && r.method === 'POST').at(-1);
    await click('Kiểm tra kết quả đặt hàng'); await waitText('Xác nhận lại và đặt hàng'); mode = 'normal';
    await click('Xác nhận lại và đặt hàng'); await waitText('Đang xử lý giao dịch');
    const last = requests.filter(r => r.path === '/checkout' && r.method === 'POST').at(-1);
    assert.ok(first.headers['Idempotency-Key']);
    assert.equal(last.headers['Idempotency-Key'], first.headers['Idempotency-Key']);
  });
  await t.test('order details show historic voucher and item prices', async () => {
    await navigate('/orders'); await waitText('Xem chi tiết'); await click('Xem chi tiết'); await waitText('OLD10');
    assert.ok((await text()).includes('Giảm 20%'));
    await navigate('/admin/orders');
    await until(() => js(`Boolean(document.querySelector('[aria-label="Xem chi tiết"]'))`), 'admin order details');
    await js(`document.querySelector('[aria-label="Xem chi tiết"]').click()`);
    await waitText('OLD10'); assert.ok((await text()).includes('Giảm 20%'));
  });
  await t.test('selected cart lines, COD and free cart items remain usable', async () => {
    await navigate('/cart'); await waitText('Giày thường');
    await js('document.querySelector(".cart-item input[type=checkbox]").click()');
    await click('Tiến hành thanh toán'); await waitText('Tiền hàng gốc');
    assert.deepEqual(requests.filter(r => r.path === '/checkout/preview' && r.method === 'POST').at(-1).body.cartItemIds, [1]);
    await form(); await js('document.querySelector("#payment-cod").click()');
    await click('Hoàn tất đặt hàng'); await until(() => js('location.pathname === "/payment-result"'), 'COD result');
    assert.equal(requests.filter(r => r.path === '/checkout' && r.method === 'POST').at(-1).body.paymentMethod, 'COD');
    const original = structuredClone(cart);
    cart.items.forEach(i => { i.unitPrice = 0; i.subTotal = 0; i.productDiscountPercent = 100; }); cart.totalPrice = 0;
    await navigate('/cart'); await waitText('Giảm 100%');
    assert.equal(await js('document.querySelector(".checkout-btn").disabled'), false);
    Object.assign(cart, original);
  });
  await t.test('removed mock route never presents payment simulation controls', async () => {
    await navigate('/mock-payment?orderId=42'); await waitText('Giày sale');
    assert.equal((await js('document.body.innerText')).includes('Môi trường mô phỏng'), false);
    assert.equal(requests.some(r => r.path.includes('/mock-payments/')), false);
  });
  await t.test('admin CRUD, bulk products, status and history conflict', async () => {
    await navigate('/admin/promotions'); await waitText('Chi tiết #1'); await click('Chi tiết #1'); await waitText('Thêm voucher');
    await click('Thêm voucher');
    for (const [key, value] of Object.entries({ code: 'NEW10', discountPercent: '10', minimumAmount: '0', quantity: '5' })) await fill(`[name="${key}"]`, value);
    await click('Lưu ưu đãi'); await waitText('NEW10 —');
    await click('Sửa #1'); await fill('[name="discountPercent"]', '12'); await click('Lưu ưu đãi'); await waitText('Giảm 12%');
    await click('Tắt mã'); await waitText('Bật mã');
    await click('Thêm giảm đơn tự động'); await fill('[name="discountPercent"]', '5'); await click('Lưu ưu đãi'); await waitText('Tối thiểu');
    await click('Thêm giảm sản phẩm'); await fill('[name="discountPercent"]', '20'); await waitText('Giày thường');
    await js('document.querySelectorAll(".promotion-choice input").forEach(e => e.click())');
    await click('Lưu ưu đãi'); await waitText('Sản phẩm #2');
    assert.deepEqual(requests.filter(r => r.path.endsWith('/product-discounts') && r.method === 'POST').at(-1).body.productIds, [1, 2]);
    await click('Sửa chương trình'); await fill('[name="name"]', 'Updated sale'); await click('Lưu chương trình'); await waitText('Updated sale');
    assert.equal(requests.filter(r => r.path === '/admin/promotions/1' && r.method === 'PUT').at(-1).body.startDate, '2026-01-01T00:00:00Z');
    await click('Tắt chương trình'); await waitText('Bật chương trình');
    await click('Xóa chương trình'); await waitText('Promotion has history');
    for (const width of [320, 768, 1024, 1440]) {
      await cdp.send('Emulation.setDeviceMetricsOverride', { width, height: 900, deviceScaleFactor: 1, mobile: false });
      await delay(300);
      if (width === 320) await screenshot('admin-mobile');
      assert.ok(await js('document.documentElement.scrollWidth <= innerWidth + 1'), `admin overflow at ${width}: ${await js('JSON.stringify([...document.querySelectorAll("body *")].filter(e => e.getBoundingClientRect().right > innerWidth + 1).map(e => e.className).slice(-15))')}`);
      if (width === 1440) await screenshot('admin-desktop');
    }
    await click('Tạo chương trình');
    await fill('[name="name"]', 'New program'); await fill('[name="startDate"]', '2030-01-01T07:00'); await fill('[name="endDate"]', '2030-01-02T07:00');
    await click('Lưu chương trình'); await waitText('New program');
    assert.equal(requests.filter(r => r.path === '/admin/promotions' && r.method === 'POST').at(-1).body.startDate, '2030-01-01T00:00:00.000Z');
    await cdp.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9 });
    await cdp.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9 });
    assert.ok(await js('document.activeElement !== document.body'), 'keyboard focus reaches controls');
  });
  await t.test('USER cannot open admin promotions', async () => {
    const before = requests.filter(r => r.path.startsWith('/admin/promotions')).length;
    await js("localStorage.setItem('user',JSON.stringify({id:1,email:'ui@test.local',role:'USER'}))");
    await b.navigate('/admin/promotions'); await waitText('Giày sale');
    assert.equal(await js('location.pathname'), '/');
    assert.equal(requests.filter(r => r.path.startsWith('/admin/promotions')).length, before);
  });
  assert.deepEqual(exceptions, [], 'no runtime exceptions');
});
