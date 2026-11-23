// 局域网模式 Web 客户端行为测试：mode 探测、PIN 登录、轮询间隔与设置页裁剪。
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { ApiError, createApi } from '../internal/server/web/app/js/api.js';
import { ctx } from '../internal/server/web/app/js/ctx.js';
import { DEFAULT_POLL_MS, LAN_POLL_MS, createStore, syncPollMs } from '../internal/server/web/app/js/store.js';
import { isLanMode, settingsView } from '../internal/server/web/app/js/views/settings.js';
import { startMock } from './mock.mjs';

function startLanMock({ pin = '246810' } = {}) {
  let token = null;
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://127.0.0.1');
    const json = (code, body) => {
      res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(body));
    };
    if (req.method === 'GET' && url.pathname === '/api/v1/mode') {
      return json(200, { mode: 'lan', auth: 'pin' });
    }
    if (req.method === 'POST' && url.pathname === '/api/v1/login') {
      let b = '';
      req.on('data', (c) => { b += c; });
      req.on('end', () => {
        const body = JSON.parse(b || '{}');
        if (body.password !== pin) return json(401, { error: 'invalid_credentials', message: '连接码不正确' });
        token = 'lan-tok-1';
        return json(200, { token, user: { id: 1700000001, username: '本机 (局域网)', role: 'user', mode: 'lan' } });
      });
      return;
    }
    if (req.method === 'GET' && url.pathname === '/api/v1/sync/pull') {
      if (req.headers.authorization !== `Bearer ${token}`) return json(401, { error: 'unauthorized' });
      return json(200, { records: [], cursor: 0, more: false });
    }
    return json(404, { error: 'not_found' });
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve({
    url: `http://127.0.0.1:${server.address().port}`,
    close: () => new Promise((r) => { server.closeAllConnections?.(); server.close(r); }),
  })));
}

test('api.mode 在云端服务返回错误并回退为云端模式，在局域网服务返回 { mode: "lan" } 并支持 PIN 登录', async () => {
  const cloud = await startMock();
  const lan = await startLanMock({ pin: '135790' });
  try {
    const cloudApi = createApi({ base: cloud.url });
    await assert.rejects(cloudApi.mode(), (e) => e instanceof ApiError && (e.status === 404 || e.status === 401));

    let tok = null;
    const lanApi = createApi({ base: lan.url, getToken: () => tok });
    const mode = await lanApi.mode();
    assert.equal(mode.mode, 'lan');
    assert.equal(mode.auth, 'pin');

    await assert.rejects(lanApi.login('lan', '000000', 'test'), (e) => e instanceof ApiError && e.status === 401);
    const login = await lanApi.login('lan', '135790', 'test');
    tok = login.token;
    assert.equal(login.user.mode, 'lan');
    assert.equal(login.user.role, 'user');
  } finally {
    await cloud.close();
    await lan.close();
  }
});

test('syncPollMs 与 store.startPolling 在局域网模式下使用更快的轮询间隔', async () => {
  assert.equal(LAN_POLL_MS, 4000);
  assert.equal(DEFAULT_POLL_MS, 30000);
  assert.equal(syncPollMs({ id: 1, role: 'user' }, false), 30000);
  assert.equal(syncPollMs({ id: 1, role: 'user', mode: 'lan' }, false), 4000);
  assert.equal(syncPollMs({ id: 1, role: 'user' }, true), 4000);

  let pulls = 0;
  let visible = false;
  const fakeApi = {
    pull: async () => { pulls += 1; return { records: [], cursor: pulls, more: false }; },
    push: async () => ({ accepted: [], rejected: [], cursor: pulls }),
  };
  const store = createStore({ api: fakeApi });
  store.load({ id: 42, username: '本机 (局域网)', role: 'user', mode: 'lan' });
  store.startPolling(15, () => visible);
  await new Promise((r) => setTimeout(r, 35));
  assert.equal(pulls, 0, '不可见时不应轮询');

  visible = true;
  await new Promise((r) => setTimeout(r, 45));
  store.stopPolling();
  const countAfterStop = pulls;
  assert.ok(countAfterStop >= 1, '可见后应触发轮询同步');

  await new Promise((r) => setTimeout(r, 35));
  assert.equal(pulls, countAfterStop, 'stopPolling 后不应再触发');
  store.unload();
});

function installDomShim() {
  const prevDoc = globalThis.document;
  const prevNode = globalThis.Node;
  const prevLocalDesc = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  const prevMatch = globalThis.matchMedia;

  class FakeNode {
    constructor() { this.children = []; this.dataset = {}; this.attributes = {}; this.className = ''; }
    append(...nodes) {
      for (const n of nodes) this.children.push(typeof n === 'string' ? { textContent: n, children: [] } : n);
    }
    setAttribute(k, v) { this.attributes[k] = v; }
    addEventListener() {}
    remove() {}
  }
  class FakeElement extends FakeNode {
    constructor(tag) {
      super();
      this.tagName = tag.toUpperCase();
      this.style = { setProperty() {} };
      this.classList = { add() {} };
    }
  }
  const ls = new Map();
  globalThis.Node = FakeNode;
  Object.defineProperty(globalThis, 'localStorage', {
    value: { getItem: (k) => ls.get(k) ?? null, setItem: (k, v) => ls.set(k, String(v)), removeItem: (k) => ls.delete(k) },
    configurable: true,
    writable: true,
  });
  globalThis.matchMedia = () => ({ matches: false });
  globalThis.document = {
    documentElement: { dataset: {} },
    createElement: (tag) => new FakeElement(tag),
    createElementNS: (_ns, tag) => new FakeElement(tag),
    createTextNode: (t) => ({ textContent: String(t), children: [] }),
  };

  return () => {
    globalThis.document = prevDoc;
    globalThis.Node = prevNode;
    if (prevLocalDesc) Object.defineProperty(globalThis, 'localStorage', prevLocalDesc);
    else delete globalThis.localStorage;
    globalThis.matchMedia = prevMatch;
  };
}

function collectText(node, out = []) {
  if (!node) return out;
  if (typeof node.textContent === 'string' && (!node.children || node.children.length === 0)) out.push(node.textContent);
  for (const c of node.children || []) collectText(c, out);
  return out;
}

test('settingsView 在 LAN 模式下显示“手机局域网直连”并隐藏“修改密码”与“云端 MCP”', () => {
  assert.equal(isLanMode({ user: { mode: 'lan' } }), true);
  assert.equal(isLanMode({ lanMode: true, user: { role: 'user' } }), true);
  assert.equal(isLanMode({ lanMode: false, user: { role: 'user' } }), false);

  const restoreDom = installDomShim();
  const prevCtx = { ...ctx };
  try {
    const store = createStore({ api: {} });
    store.load({ id: 1, username: 'alice', role: 'user' });
    ctx.store = store;
    ctx.api = {};

    ctx.lanMode = false;
    ctx.user = { id: 1, username: 'alice', role: 'user' };
    const cloudText = collectText(settingsView.render()).join('\n');
    assert.ok(!cloudText.includes('手机局域网直连'));
    assert.ok(cloudText.includes('修改密码'));
    assert.ok(cloudText.includes('云端 MCP'));
    assert.ok(cloudText.includes('实验功能（EXP）'));

    ctx.lanMode = true;
    ctx.user = { id: 2, username: '本机 (局域网)', role: 'user', mode: 'lan' };
    const lanText = collectText(settingsView.render()).join('\n');
    assert.ok(lanText.includes('手机局域网直连'));
    assert.ok(!lanText.includes('修改密码'));
    assert.ok(!lanText.includes('云端 MCP'));
    assert.ok(!lanText.includes('实验功能（EXP）'));
  } finally {
    Object.assign(ctx, prevCtx);
    restoreDom();
  }
});
