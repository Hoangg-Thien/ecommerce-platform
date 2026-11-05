// No added dependency: Chrome DevTools Protocol over Node's built-in WebSocket.
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, dirname, basename } from 'node:path';
import { once } from 'node:events';

export const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
export async function until(fn, label, timeout = 10000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) { if (await fn()) return; await delay(50); }
  throw Error(`Timed out: ${label}`);
}
async function connect(url) {
  const socket = new WebSocket(url);
  await once(socket, 'open');
  let sequence = 0;
  const pending = new Map();
  const handlers = new Map();
  socket.addEventListener('message', event => {
    const data = JSON.parse(event.data);
    if (data.id) {
      const task = pending.get(data.id);
      pending.delete(data.id);
      if (data.error) task?.reject(Error(data.error.message)); else task?.resolve(data.result);
    } else handlers.get(data.method)?.(data.params);
  });
  return {
    send(method, params = {}) { return new Promise((resolve, reject) => {
      const id = ++sequence; pending.set(id, { resolve, reject }); socket.send(JSON.stringify({ id, method, params }));
    }); },
    on(method, handler) { handlers.set(method, handler); },
    close() { socket.close(); },
  };
}
export async function browser() {
  const profile = await mkdtemp(join(tmpdir(), 'promotion-ui-'));
  const vite = spawn(process.execPath, ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '5179', '--strictPort'], { windowsHide: true, stdio: 'ignore' });
  const chrome = spawn(process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--disable-background-networking',
    '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank',
  ], { windowsHide: true, stdio: 'ignore' });
  let cdp;
  const close = async () => {
    if (cdp) { try { await cdp.send('Browser.close'); } catch { /* browser already closed */ } cdp.close(); }
    chrome.kill(); vite.kill(); await delay(500);
    // Only remove the isolated profile created by this test, never the user's Chrome profile.
    if (dirname(resolve(profile)) === resolve(tmpdir()) && basename(profile).startsWith('promotion-ui-')) {
      await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }).catch(() => {});
    }
  };
  try {
    await until(async () => { try { return (await fetch('http://127.0.0.1:5179')).ok; } catch { return false; } }, 'Vite');
    let port;
    await until(async () => { try { port = (await readFile(join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]; return port; } catch { return false; } }, 'Chrome');
    const tabs = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
    cdp = await connect(tabs.find(tab => tab.type === 'page').webSocketDebuggerUrl);
    await cdp.send('Page.enable'); await cdp.send('Runtime.enable');
    const evaluate = async expression => {
      const result = await cdp.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
      if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || 'Browser evaluation failed');
      return result.result.value;
    };
    return { cdp, close, evaluate, navigate: path => cdp.send('Page.navigate', { url: `http://127.0.0.1:5179${path}` }) };
  } catch (error) { await close(); throw error; }
}
