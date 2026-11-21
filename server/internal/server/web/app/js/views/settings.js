// 设置：账号与同步、云端 MCP、外观、学期、数据导入导出。

import { ctx } from '../ctx.js';
import { confirmDialog, h, icon, toast } from '../dom.js';
import { openPasswordForm, openSemesterForm } from '../forms.js';
import { segmented } from '../formkit.js';
import { TYPES, currentSemester, tombstone } from '../model.js';
import { PRESETS, applyTheme, hexToHsl, loadTheme, palette, saveTheme } from '../theme.js';
import { fmtTime } from '../util.js';
import { setCurrentSemester } from '../actions.js';

export function syncText(store) {
  if (store.status === 'syncing') return '同步中…';
  if (store.status === 'offline') return store.pending ? `离线，${store.pending} 项改动待同步` : '离线';
  if (store.status === 'error') return store.lastError || '同步失败';
  if (store.pending) return `${store.pending} 项改动待同步`;
  if (store.lastSync) {
    const t = new Date(store.lastSync);
    return `已同步 ${fmtTime(t.getHours() * 60 + t.getMinutes())}`;
  }
  return '未同步';
}

function section(title, ...kids) {
  return h('section', { class: 'card settings-sec' }, h('h2', {}, title), ...kids);
}

function row(label, value, ...actions) {
  return h('div', { class: 'set-row' }, h('div', { class: 'set-label' }, h('b', {}, label), value != null ? h('span', {}, value) : null), h('div', { class: 'set-actions' }, actions));
}

export function isLanMode(ctxLike) {
  return Boolean(ctxLike?.lanMode || ctxLike?.user?.mode === 'lan');
}

export const settingsView = {
  id: 'settings',
  head: () => ({ eyebrow: '', title: '设置' }),
  add: null,
  render() {
    const { store, user, api } = ctx;
    const lan = isLanMode(ctx);
    const view = store.view();
    const theme = loadTheme();
    const change = (patch) => {
      const t = { ...loadTheme(), ...patch };
      saveTheme(t);
      applyTheme(t);
      ctx.rerender();
    };

    const mode = segmented([['system', '跟随系统'], ['light', '浅色'], ['dark', '深色']], theme.mode, (v) => change({ mode: v }));
    const dark = document.documentElement.dataset.theme === 'dark';
    const swatches = h('div', { class: 'theme-swatches', role: 'group', 'aria-label': '主题色' }, PRESETS.map((p) => h('button', {
      type: 'button', class: 'theme-swatch', 'aria-pressed': String(theme.seed.toLowerCase() === p.seed.toLowerCase()), 'aria-label': p.name, title: p.name,
      style: { '--sw': palette(p.seed, dark).primary }, onclick: () => change({ seed: p.seed }),
    }, h('span', {}, p.name))));
    const custom = h('input', { type: 'color', class: 'color-input', value: hexToHsl(theme.seed) ? theme.seed : '#2E776C', 'aria-label': '自定义主题色', onchange: (e) => change({ seed: e.target.value }) });

    const account = section('账号与同步',
      row(
        user.username,
        lan ? '当前会话通过手机局域网提供服务' : (user.role === 'admin' ? '管理员' : '普通用户'),
        lan ? h('span', { class: 'tag' }, '手机局域网直连') : null,
      ),
      row('同步状态', syncText(store),
        h('button', { type: 'button', class: 'btn tonal small', onclick: async () => { try { await store.sync(); toast('已同步'); } catch (e) { toast(`同步失败：${e.message}`); } } }, icon('sync', 16), '立即同步')),
      h('div', { class: 'set-buttons' },
        !lan ? h('button', { type: 'button', class: 'btn tonal', onclick: openPasswordForm }, '修改密码') : null,
        !lan && user.role === 'admin' ? h('a', { class: 'btn tonal', href: '/admin/' }, icon('external', 16), '管理后台') : null,
        h('button', { type: 'button', class: 'btn danger-ghost', onclick: async () => {
          if (store.pending && !(await confirmDialog(`还有 ${store.pending} 项改动没有同步，退出后会丢失。确定退出吗？`, '退出', { danger: true }))) return;
          ctx.logout();
        } }, icon('logout', 16), '退出登录')));

    const appearance = section('外观', row('深浅色', null, mode.el), h('div', { class: 'set-block' }, h('b', {}, '主题色'), swatches,
      h('label', { class: 'custom-color' }, custom, h('span', {}, '自定义颜色'))));

    const sems = view.semesters;
    const cur = currentSemester(view);
    const semester = section('学期',
      sems.length ? sems.map((s) => row(`${s.name}${s.id === cur?.id ? '（当前）' : ''}`, `第 1 周周一 ${s.week1Monday} · ${s.totalWeeks} 周 · ${s.periods.length} 节`,
        s.id !== cur?.id ? h('button', { type: 'button', class: 'btn tonal small', onclick: () => setCurrentSemester(s) }, '设为当前') : null,
        h('button', { type: 'button', class: 'btn tonal small', onclick: () => openSemesterForm({ semester: s }) }, icon('edit', 16), '编辑')))
        : h('p', { class: 'hint' }, '还没有学期。'),
      h('div', { class: 'set-buttons' }, h('button', { type: 'button', class: 'btn tonal', onclick: () => openSemesterForm({ create: true }) }, icon('plus', 16), '新建学期')));

    const counts = {
      课程: view.courses.length, 日程: view.events.length, 待办: view.tasks.length,
    };
    const fileInput = h('input', {
      type: 'file',
      accept: 'application/json,.json',
      hidden: true,
      onchange: async () => {
        const file = fileInput.files?.[0];
        fileInput.value = '';
        if (!file) return;
        try {
          const parsed = parseBackup(await file.text());
          const msg = `将用备份覆盖当前全部数据（${parsed.counts.courses} 门课、${parsed.counts.events} 个日程、${parsed.counts.tasks} 项待办）。确定恢复吗？`;
          if (!(await confirmDialog(msg, '覆盖并恢复', { danger: true }))) return;
          restoreBackupRecords(store, parsed.records);
          toast('已恢复备份');
        } catch (e) {
          toast(`导入失败：${e.message || '无法读取这个备份文件'}`);
        }
      },
    });
    const data = section('数据',
      row('已同步的数据', Object.entries(counts).map(([k, v]) => `${k} ${v}`).join(' · ')),
      fileInput,
      h('div', { class: 'set-buttons' },
        h('button', { type: 'button', class: 'btn tonal', onclick: exportJson }, icon('download', 16), '导出 JSON 备份'),
        h('button', { type: 'button', class: 'btn tonal', onclick: () => fileInput.click() }, icon('list', 16), '导入 JSON 备份')),
      h('p', { class: 'hint' }, lan
        ? '支持 MintCal 通用备份（Android / Web）与旧版网页备份；改动会直接同步到你的手机本地。'
        : '支持 MintCal 通用备份（Android / Web）与旧版网页备份；所有数据都存放在你自己的服务器上。'));

    const origin = typeof location !== 'undefined' ? location.origin : '';
    const endpoint = `${origin}/mcp`;
    const mcpConfig = buildMcpConfig(origin);
    const cloudMcp = user && !lan ? section('云端 MCP',
      row('端点地址', endpoint,
        h('button', { type: 'button', class: 'btn tonal small', onclick: () => copyText(endpoint, '已复制 MCP 端点地址') }, '复制地址')),
      h('pre', { class: 'code-box', tabindex: '0', 'aria-label': 'MCP 客户端配置 JSON' }, mcpConfig),
      h('div', { class: 'set-buttons' },
        h('button', { type: 'button', class: 'btn tonal', onclick: () => copyText(mcpConfig, '已复制 MCP 客户端配置') }, icon('sparkle', 16), '复制客户端配置')),
      h('p', { class: 'hint' }, '支持 Claude Code、Cursor 等 Streamable HTTP 客户端。请将 <token> 替换为通过 /api/v1/login 获取的账号 Bearer 令牌。')) : null;

    return h('div', { class: 'settings' }, account, cloudMcp, appearance, semester, data);
  },
};

const SNAPSHOT_TYPES = [
  ['semesters', 'semester'],
  ['courses', 'course'],
  ['exceptions', 'courseException'],
  ['events', 'event'],
  ['tasks', 'task'],
  ['lists', 'taskList'],
];

export function buildExportBundle(view, now = Date.now()) {
  return {
    app: 'mintcal',
    version: 1,
    exportedAt: now,
    snapshot: {
      semesters: view.semesters || [],
      courses: view.courses || [],
      exceptions: view.exceptions || [],
      events: view.events || [],
      tasks: view.tasks || [],
      lists: view.lists || [],
    },
  };
}

export function parseBackup(input) {
  let obj = input;
  if (typeof input === 'string') {
    try { obj = JSON.parse(input); } catch { throw new Error('备份文件不是有效的 JSON'); }
  }
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) throw new Error('不支持的备份文件格式');

  const raw = [];
  if (obj.snapshot && typeof obj.snapshot === 'object' && !Array.isArray(obj.snapshot)) {
    if (obj.app != null && obj.app !== 'mintcal') throw new Error('不支持的备份应用标识');
    for (const [key, type] of SNAPSHOT_TYPES) {
      const list = obj.snapshot[key] ?? [];
      if (!Array.isArray(list)) throw new Error(`备份字段 ${key} 格式无效`);
      for (const r of list) {
        if (r && typeof r === 'object' && typeof r.id === 'string' && r.id && r.deletedAt == null) {
          raw.push({ ...r, _t: type, deletedAt: null });
        }
      }
    }
  } else if (Array.isArray(obj.records)) {
    if (obj.app != null && obj.app !== 'mintcal-web' && obj.app !== 'mintcal') throw new Error('不支持的备份应用标识');
    for (const r of obj.records) {
      if (r && typeof r === 'object' && typeof r.id === 'string' && r.id && TYPES[r._t] && r.deletedAt == null) {
        raw.push({ ...r, deletedAt: null });
      }
    }
  } else {
    throw new Error('不支持的备份文件格式');
  }

  const byId = new Map();
  for (const r of raw) byId.set(r.id, r);
  const records = [...byId.values()];
  const counts = { semesters: 0, courses: 0, exceptions: 0, events: 0, tasks: 0, lists: 0 };
  const keyByType = Object.fromEntries(SNAPSHOT_TYPES.map(([k, t]) => [t, k]));
  for (const r of records) {
    const k = keyByType[r._t];
    if (k) counts[k] += 1;
  }
  return { records, counts };
}

export function restoreBackupRecords(store, records, now = Date.now()) {
  const keepIds = new Set(records.map((r) => r.id));
  const dead = store.allRecords()
    .filter((r) => r.deletedAt == null && !keepIds.has(r.id))
    .map((r) => tombstone(r, now));
  const upserts = records.map((r) => {
    const cur = store.get(r.id);
    const updatedAt = Math.max(now, (cur?.updatedAt || 0) + 1, (r.updatedAt || 0) + 1);
    return { ...r, deletedAt: null, updatedAt };
  });
  const batch = [...dead, ...upserts];
  if (batch.length) store.put(...batch);
  return batch;
}

export function buildMcpConfig(origin) {
  const base = String(origin || '').replace(/\/+$/, '');
  return JSON.stringify({
    mcpServers: {
      mintcal: {
        type: 'http',
        url: `${base}/mcp`,
        headers: {
          Authorization: 'Bearer <token>',
        },
      },
    },
  }, null, 2);
}

async function copyText(text, okMsg) {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      toast(okMsg);
      return;
    }
  } catch { /* 降级到 execCommand */ }
  const ta = h('textarea', { class: 'copy-scratch', readonly: true, value: text });
  document.body.append(ta);
  ta.select();
  try {
    document.execCommand('copy');
    toast(okMsg);
  } catch {
    toast('复制失败，请手动选中复制');
  } finally {
    ta.remove();
  }
}

function exportJson() {
  const bundle = buildExportBundle(ctx.store.view(), Date.now());
  const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' });
  const a = h('a', { href: URL.createObjectURL(blob), download: `mintcal-${new Date().toISOString().slice(0, 10)}.json` });
  document.body.append(a);
  a.click();
  setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
}
