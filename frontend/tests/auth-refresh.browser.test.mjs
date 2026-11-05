import test from 'node:test';
import assert from 'node:assert/strict';
import { browser, delay } from './browser.mjs';

test('shared refresh flight, delayed unauthorized response and failure recovery', { timeout: 40000 }, async t => {
  const b = await browser(); t.after(b.close);
  let refreshes = 0, failRefresh = false;
  const probes = new Map();
  await b.cdp.send('Fetch.enable', { patterns: [{ urlPattern: '*://localhost:8080/*' }] });
  b.cdp.on('Fetch.requestPaused', async ({ requestId, request }) => {
    const path = new URL(request.url).pathname;
    let status = 200, data = {};
    if (request.method !== 'OPTIONS') {
      if (path.endsWith('/auth/refresh')) {
        refreshes++;
        await delay(150);
        status = failRefresh ? 401 : 200;
        // JWT access tokens may be identical for two issuances within one second.
        data = { accessToken: 'same-access-token', id: 1, email: 'test@example.com', role: 'USER' };
      } else if (path.includes('/probe-')) {
        const count = (probes.get(path) || 0) + 1;
        probes.set(path, count);
        if (count === 1) {
          if (path.endsWith('slow')) await delay(500);
          status = 401;
        } else data = { ok: true };
      } else if (path.endsWith('/products')) data = { content: [], totalPages: 0 };
      else if (path.endsWith('/categories')) data = [];
    }
    await b.cdp.send('Fetch.fulfillRequest', { requestId, responseCode: status,
      responseHeaders: [{ name:'Content-Type', value:'application/json' },
        { name:'Access-Control-Allow-Origin', value:'http://127.0.0.1:5179' },
        { name:'Access-Control-Allow-Credentials', value:'true' },
        { name:'Access-Control-Allow-Headers', value:'Content-Type, Authorization' },
        { name:'Access-Control-Allow-Methods', value:'GET,POST,OPTIONS' }],
      body: Buffer.from(JSON.stringify(data)).toString('base64') });
  });
  await b.navigate('/login');
  const js = b.evaluate;
  const results = await js(`(async () => {
    const {default:auth}=await import('/src/api/authApi.js');
    const {default:client,setAccessToken}=await import('/src/api/axiosClient.js');
    setAccessToken('same-access-token');
    return Promise.all([auth.refreshToken(),auth.refreshToken(),client.get('/probe-fast'),client.get('/probe-slow')]).then(r=>r.map(Boolean));
  })()`);
  assert.deepEqual(results, [true,true,true,true]);
  assert.equal(refreshes, 1, 'restore session and concurrent/delayed 401s must share one rotation');
  assert.equal(probes.get('/api/v1/probe-slow'), 2, 'late request retries once with the refreshed session');
  failRefresh = true;
  const rejected = await js(`(async()=>{const {default:a}=await import('/src/api/authApi.js');return (await Promise.allSettled([a.refreshToken(),a.refreshToken()])).map(r=>r.status);})()`);
  assert.deepEqual(rejected, ['rejected','rejected']);
  assert.equal(refreshes, 2, 'failed refresh is also single-flight and cannot recurse');
  failRefresh = false;
  assert.equal(await js(`(async()=>{const {default:a}=await import('/src/api/authApi.js');return Boolean((await a.refreshToken()).accessToken);})()`), true);
  assert.equal(refreshes, 3, 'settled failure must clear flight for a subsequent attempt');
});
