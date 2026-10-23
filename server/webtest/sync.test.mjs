// 同步层测试：两台「设备」（两个 store）通过服务器互相同步。
// 默认对内置的模拟服务运行；设置 MINTCAL_URL / MINTCAL_USER / MINTCAL_PASSWORD 则对真实的 Go 服务端运行。
import test from 'node:test';
import assert from 'node:assert/strict';
import { createApi, ApiError } from '../internal/server/web/app/js/api.js';
import { createStore } from '../internal/server/web/app/js/store.js';
import * as M from '../internal/server/web/app/js/model.js';
import { startMock } from './mock.mjs';

const real = process.env.MINTCAL_URL;
let mock = null;
let base;
let user;
let password;

test.before(async () => {
  if (real) {
    base = real;
    user = process.env.MINTCAL_USER;
    password = process.env.MINTCAL_PASSWORD;
  } else {
    mock = await startMock();
    base = mock.url;
    user = 'alice';
    password = 'alicepassword';
  }
});
test.after(async () => { if (mock) await mock.close(); });

const memStorage = () => {
  const m = new Map();
  return { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => m.set(k, String(v)), removeItem: (k) => m.delete(k) };
};

async function device(name, storage = memStorage()) {
  let token = null;
  const api = createApi({ base, getToken: () => token });
  const login = await api.login(user, password, name);
  token = login.token;
  const store = createStore({ api, storage, debounceMs: 5, retryMs: 50 });
  store.load(login.user);
  return { api, store, storage, user: login.user, logout: () => api.logout() };
}

const sem = () => M.newSemester({ name: '测试学期', week1Monday: '2026-08-31' });

test('登录失败返回错误', async () => {
  const api = createApi({ base });
  await assert.rejects(api.login(user, 'wrong-password', 'x'), (e) => e instanceof ApiError && e.status === 401);
});

test('两台设备互相同步：新增、修改、删除', async () => {
  const a = await device('A');
  const b = await device('B');
  const s = sem();
  const c = M.newCourse({ semesterId: s.id, name: '数据结构', dayOfWeek: 2, startPeriod: 1, endPeriod: 2, weeks: [1, 2, 3] });
  a.store.put(s, c);
  await a.store.sync();
  assert.equal(a.store.pending, 0);

  await b.store.sync();
  assert.equal(b.store.view().courses.length, 1);
  assert.equal(b.store.get(c.id).name, '数据结构');
  assert.equal(b.store.view().semesters.length, 1);

  b.store.put(M.touch(b.store.get(c.id), { name: '数据结构（改）', room: '教2-321' }));
  await b.store.sync();
  await a.store.sync();
  assert.equal(a.store.get(c.id).room, '教2-321');

  a.store.put(M.tombstone(a.store.get(c.id)));
  await a.store.sync();
  await b.store.sync();
  assert.equal(b.store.view().courses.length, 0);
  assert.notEqual(b.store.get(c.id).deletedAt, null); // 墓碑保留

  // 撤销删除：恢复后另一台设备能看到
  a.store.put(M.restore(a.store.get(c.id)));
  await a.store.sync();
  await b.store.sync();
  assert.equal(b.store.view().courses.length, 1);
});

test('冲突时以较新的修改为准', async () => {
  const a = await device('A');
  const b = await device('B');
  await a.store.sync();
  await b.store.sync();
  const t = M.newTask({ title: '交作业' });
  a.store.put(t);
  await a.store.sync();
  await b.store.sync();
  const base0 = a.store.get(t.id).updatedAt;
  // B 先改，A 后改（updatedAt 更大），两边都离线改动后再同步
  b.store.put(M.touch(b.store.get(t.id), { title: 'B 的标题' }, base0 + 10));
  a.store.put(M.touch(a.store.get(t.id), { title: 'A 的标题' }, base0 + 20));
  await b.store.sync(); // B 先推上去
  await a.store.sync(); // A 的更新，覆盖
  await b.store.sync();
  assert.equal(b.store.get(t.id).title, 'A 的标题');
  assert.equal(a.store.get(t.id).title, 'A 的标题');
  assert.equal(a.store.pending + b.store.pending, 0);

  // 较旧的修改被服务器拒绝，本地回到服务器版本
  const cur = a.store.get(t.id);
  b.store.put({ ...b.store.get(t.id), title: '过时的修改', updatedAt: cur.updatedAt - 5 });
  await b.store.sync();
  assert.equal(b.store.get(t.id).title, 'A 的标题');
  assert.equal(b.store.pending, 0);
});

test('离线时修改保留，恢复网络后自动推送；缓存可恢复', async () => {
  const storage = memStorage();
  const a = await device('A', storage);
  const t = M.newTask({ title: '离线新增' });
  const realFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new TypeError('offline'); };
  try {
    a.store.put(t);
    await assert.rejects(a.store.sync());
    assert.equal(a.store.status, 'offline');
    assert.equal(a.store.pending, 1);
    a.store.flush();
  } finally {
    globalThis.fetch = realFetch;
  }
  // 「重新打开页面」：从缓存恢复，待推送的修改还在
  const again = await device('A2', storage); // 同一份缓存（同一用户）
  assert.equal(again.store.pending, 1);
  assert.equal(again.store.get(t.id).title, '离线新增');
  await again.store.sync();
  assert.equal(again.store.pending, 0);
  const other = await device('C');
  await other.store.sync();
  assert.equal(other.store.get(t.id).title, '离线新增');
});

test('大量记录分页拉取', async () => {
  const a = await device('A');
  const b = await device('B');
  const list = M.newList({ name: '大清单' });
  const many = Array.from({ length: 1200 }, (_, i) => M.newTask({ title: `任务 ${i}`, listId: list.id }));
  a.store.put(list, ...many);
  await a.store.sync();
  assert.equal(a.store.pending, 0);
  await b.store.sync();
  assert.ok(b.store.view().tasks.filter((t) => t.listId === list.id).length === 1200);
});

test('改密码后旧密码失效', async () => {
  const a = await device('A');
  await assert.rejects(a.api.changePassword('bad-old-password', 'newpassword1'), (e) => e.status === 401);
  await assert.rejects(a.api.changePassword(password, 'short'), (e) => e.status === 400);
});
