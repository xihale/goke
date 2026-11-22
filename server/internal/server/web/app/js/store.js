// 本地数据与同步：所有记录放在内存（并缓存到浏览器存储），修改先落本地、再推送到服务器。
// 规则与 Android 端一致：按记录以 updatedAt 较大者为准；删除用墓碑（deletedAt）。

import { NetworkError, ApiError } from './api.js';
import { liveView, toWire } from './model.js';

const PUSH_BATCH = 500;
export const DEFAULT_POLL_MS = 30000;
export const LAN_POLL_MS = 4000;

/** 根据会话模式返回可见时的轮询同步间隔（毫秒）。 */
export function syncPollMs(user, lanMode = false) {
  return lanMode || user?.mode === 'lan' ? LAN_POLL_MS : DEFAULT_POLL_MS;
}

export function createStore({ api, storage = null, clock = () => Date.now(), debounceMs = 400, retryMs = 15000 } = {}) {
  const records = new Map();
  const dirty = new Set();
  let cursor = 0;
  let userKey = null;
  let status = 'idle'; // idle | syncing | offline | error
  let lastSync = null;
  let lastError = null;
  let cachedView = null;
  let running = null;
  let again = false;
  let timer = null;
  let retryTimer = null;
  let persistTimer = null;
  let pollTimer = null;
  const listeners = new Set();

  const emit = (kind) => listeners.forEach((fn) => fn(kind));
  const setStatus = (s, err = null) => {
    if (status === s && lastError === err) return;
    status = s;
    lastError = err;
    emit('status');
  };

  function persistNow() {
    if (!storage || !userKey) return;
    try {
      storage.setItem(userKey, JSON.stringify({ cursor, records: [...records.values()], dirty: [...dirty] }));
    } catch (e) { /* 配额不足时忽略，缓存只是加速 */ }
  }
  function persist() {
    if (!storage) return;
    clearTimeout(persistTimer);
    persistTimer = setTimeout(persistNow, 200);
  }

  function reset() {
    records.clear();
    dirty.clear();
    cursor = 0;
    cachedView = null;
    status = 'idle';
    lastError = null;
    lastSync = null;
  }

  function stopPolling() {
    clearInterval(pollTimer);
    pollTimer = null;
  }

  function startPolling(intervalMs = DEFAULT_POLL_MS, isVisible = () => true) {
    stopPolling();
    pollTimer = setInterval(() => {
      if (isVisible()) sync().catch(() => {});
    }, intervalMs);
  }

  function load(user) {
    clearTimeout(timer);
    clearTimeout(retryTimer);
    stopPolling();
    reset();
    userKey = `goke-cache:${user.id}`;
    if (storage) {
      try {
        const c = JSON.parse(storage.getItem(userKey) || 'null');
        if (c) {
          cursor = c.cursor || 0;
          for (const r of c.records || []) records.set(r.id, r);
          for (const id of c.dirty || []) if (records.has(id)) dirty.add(id);
        }
      } catch (e) { /* 缓存损坏就当没有，重新从服务器拉取 */ reset(); }
    }
    emit('data');
  }

  function unload(clearCache = true) {
    clearTimeout(timer);
    clearTimeout(retryTimer);
    clearTimeout(persistTimer);
    stopPolling();
    if (clearCache && storage && userKey) storage.removeItem(userKey);
    userKey = null;
    reset();
    emit('data');
  }

  /** 接收一条服务器记录。返回是否改变了本地数据。 */
  function applyRemote(w) {
    const data = { ...w.data };
    if (data.updatedAt !== w.updatedAt) data.updatedAt = w.updatedAt;
    if ((data.deletedAt ?? null) !== (w.deletedAt ?? null)) data.deletedAt = w.deletedAt ?? null;
    const local = records.get(w.id);
    if (local) {
      if (dirty.has(w.id) && local.updatedAt > w.updatedAt) return false; // 本地改动更新，稍后推送
      if (local.updatedAt === w.updatedAt && (local.deletedAt ?? null) === (data.deletedAt ?? null)) {
        dirty.delete(w.id);
        return false;
      }
      if (local.updatedAt > w.updatedAt) return false;
    }
    records.set(w.id, data);
    dirty.delete(w.id);
    return true;
  }

  async function pullAll() {
    let changed = false;
    for (;;) {
      const res = await api.pull(cursor, 500);
      for (const w of res.records || []) if (applyRemote(w)) changed = true;
      cursor = res.cursor ?? cursor;
      if (!res.more) break;
    }
    return changed;
  }

  async function pushDirty() {
    let changed = false;
    const ids = [...dirty];
    for (let i = 0; i < ids.length; i += PUSH_BATCH) {
      const batch = ids.slice(i, i + PUSH_BATCH).map((id) => records.get(id)).filter(Boolean);
      if (!batch.length) continue;
      const sent = new Map(batch.map((r) => [r.id, r.updatedAt]));
      const res = await api.push(batch.map(toWire));
      for (const id of res.accepted || []) {
        const cur = records.get(id);
        if (cur && cur.updatedAt === sent.get(id)) dirty.delete(id);
      }
      for (const w of res.rejected || []) {
        // 服务器上有更新的版本：以服务器为准
        const cur = records.get(w.id);
        if (cur && cur.updatedAt <= w.updatedAt) {
          dirty.delete(w.id);
          if (applyRemote(w)) changed = true;
        }
      }
    }
    return changed;
  }

  async function runOnce() {
    setStatus('syncing');
    try {
      let changed = await pullAll();
      if (dirty.size) {
        if (await pushDirty()) changed = true;
        if (await pullAll()) changed = true; // 取回自己推送的记录的序号，顺便拿到别处的新改动
      }
      cachedView = changed ? null : cachedView;
      lastSync = clock();
      persistNow();
      setStatus('idle');
      if (changed) emit('data');
    } catch (e) {
      if (e instanceof NetworkError) {
        setStatus('offline');
        clearTimeout(retryTimer);
        retryTimer = setTimeout(() => sync(), retryMs);
      } else if (e instanceof ApiError && e.status === 401) {
        setStatus('error', '登录已失效');
      } else {
        setStatus('error', e.message || '同步失败');
      }
      throw e;
    }
  }

  /** 立即同步一次（先拉后推）；并发调用会合并。出错时抛出。 */
  function sync() {
    if (!userKey) return Promise.resolve();
    if (running) {
      again = true;
      return running;
    }
    running = (async () => {
      try {
        do {
          again = false;
          await runOnce();
        } while (again);
      } finally {
        running = null;
      }
    })();
    return running;
  }

  function scheduleSync() {
    clearTimeout(timer);
    timer = setTimeout(() => { sync().catch(() => {}); }, debounceMs);
  }

  /** 写入（新增或修改）记录：先落本地，再异步推送。 */
  function put(...recs) {
    for (const r of recs.flat()) {
      records.set(r.id, r);
      dirty.add(r.id);
    }
    cachedView = null;
    persist();
    emit('data');
    scheduleSync();
  }

  return {
    load,
    unload,
    sync,
    startPolling,
    stopPolling,
    put,
    get: (id) => records.get(id),
    view() {
      if (!cachedView) cachedView = liveView(records);
      return cachedView;
    },
    allRecords: () => [...records.values()],
    subscribe(fn) { listeners.add(fn); return () => listeners.delete(fn); },
    get status() { return status; },
    get lastSync() { return lastSync; },
    get lastError() { return lastError; },
    get pending() { return dirty.size; },
    get cursor() { return cursor; },
    /** 用于测试：立即写入缓存。 */
    flush: persistNow,
  };
}
