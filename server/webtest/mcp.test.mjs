// MCP 端到端：外部助手经 POST /mcp 增删改待办，网页客户端（真实 store）同步后应一致；
// 反向：客户端改动经 push 落库后，MCP 读取也应看到。只在真实 Go 服务端下运行（需 GOKE_URL）。
import test from 'node:test';
import assert from 'node:assert/strict';
import { createApi } from '../internal/server/web/app/js/api.js';
import { createStore } from '../internal/server/web/app/js/store.js';
import * as M from '../internal/server/web/app/js/model.js';

const base = process.env.GOKE_URL;
const user = process.env.GOKE_USER;
const password = process.env.GOKE_PASSWORD;

let token = null;
let rpcId = 0;

test.before(async () => {
  if (!base) return;
  const res = await fetch(`${base}/api/v1/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: user, password, deviceName: 'mcp-test' }),
  });
  assert.equal(res.status, 200);
  token = (await res.json()).token;
});

// mcp 调一个工具；无状态端点无需 initialize，直接 tools/call。
async function mcp(name, args, expectError = false) {
  const res = await fetch(`${base}/mcp`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json, text/event-stream',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify({ jsonrpc: '2.0', id: ++rpcId, method: 'tools/call', params: { name, arguments: args } }),
  });
  assert.equal(res.status, 200, `POST /mcp ${name} -> ${res.status}`);
  const body = await res.json();
  assert.ok(!body.error, `JSON-RPC 错误：${JSON.stringify(body.error)}`);
  const out = body.result;
  assert.equal(!!out.isError, expectError, `${name} isError=${out.isError} want ${expectError}: ${JSON.stringify(out.content)}`);
  if (out.isError) return out.content[0].text;
  return JSON.parse(out.content[0].text);
}

// 网页客户端：与 sync.test.mjs 同构的第二台「设备」。
async function webDevice(name) {
  const storage = (() => { const m = new Map(); return { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => m.set(k, String(v)), removeItem: (k) => m.delete(k) }; })();
  const api = createApi({ base, getToken: () => token });
  const login = await api.login(user, password, name);
  const store = createStore({ api, storage, debounceMs: 5, retryMs: 50 });
  store.load(login.user);
  return { store };
}

test('未带令牌的 MCP 请求被拒绝', { skip: !base }, async () => {
  const res = await fetch(`${base}/mcp`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'tools/list' }),
  });
  assert.equal(res.status, 401);
});

test('MCP 写入 → 网页客户端同步得到；客户端改动 → MCP 读到；删除成墓碑', { skip: !base }, async () => {
  // tools/list 有我们的工具
  const list = await fetch(`${base}/mcp`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
    body: JSON.stringify({ jsonrpc: '2.0', id: ++rpcId, method: 'tools/list' }),
  }).then((r) => r.json());
  const names = list.result.tools.map((t) => t.name);
  for (const n of ['create_task', 'search_tasks', 'update_task', 'complete_task', 'delete_task', 'get_agenda']) {
    assert.ok(names.includes(n), `缺少工具 ${n}`);
  }

  // 助手建清单 + 待办
  const l = await mcp('create_list', { name: 'MCP 清单' });
  const t = await mcp('create_task', {
    title: '助手建的待办',
    listId: l.id,
    due: '2026-11-10 09:00',
    priority: 3,
    remindMinutes: 15,
  });

  // 网页客户端同步 → 看到同一条
  const web = await webDevice('web-mcp');
  await web.store.sync();
  const got = web.store.get(t.id);
  assert.ok(got, '网页客户端应拉到 MCP 建的待办');
  assert.equal(got.title, '助手建的待办');
  assert.equal(got.due, '2026-11-10T09:00');
  assert.equal(got.dueHasTime, true);
  assert.equal(got.priority, 3);
  assert.equal(web.store.view().lists.some((x) => x.id === l.id), true);

  // 客户端改名并推送 → MCP 搜索应看到新标题
  web.store.put(M.touch(web.store.get(t.id), { title: '客户端改过' }));
  await web.store.sync();
  const found = await mcp('search_tasks', { query: '客户端改过', status: 'all' });
  assert.equal(found.total, 1);
  assert.equal(found.tasks[0].id, t.id);

  // MCP 完成 → 客户端同步后 done
  await mcp('complete_task', { id: t.id });
  await web.store.sync();
  assert.equal(web.store.get(t.id).done, true);

  // MCP 删除 → 墓碑 → 客户端同步后不在活动列表
  await mcp('delete_task', { id: t.id });
  await web.store.sync();
  assert.equal(web.store.view().tasks.length, 0);

  // 校验错误以工具错误返回
  await mcp('get_task', { id: 'no-such-id' }, true);
  await mcp('create_task', { title: '' }, true);
});
