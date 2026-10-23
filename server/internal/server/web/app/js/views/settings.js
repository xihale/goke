// 设置：账号与同步、外观、学期、数据导出。

import { ctx } from '../ctx.js';
import { confirmDialog, h, icon, toast } from '../dom.js';
import { openPasswordForm, openSemesterForm } from '../forms.js';
import { segmented } from '../formkit.js';
import { currentSemester } from '../model.js';
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

export const settingsView = {
  id: 'settings',
  head: () => ({ eyebrow: '', title: '设置' }),
  add: null,
  render() {
    const { store, user, api } = ctx;
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
    const custom = h('input', { type: 'color', class: 'color-input', value: hexToHsl(theme.seed) ? theme.seed : '#4FA796', 'aria-label': '自定义主题色', onchange: (e) => change({ seed: e.target.value }) });

    const account = section('账号与同步',
      row(user.username, user.role === 'admin' ? '管理员' : '普通用户'),
      row('同步状态', syncText(store),
        h('button', { type: 'button', class: 'btn tonal small', onclick: async () => { try { await store.sync(); toast('已同步'); } catch (e) { toast(`同步失败：${e.message}`); } } }, icon('sync', 16), '立即同步')),
      h('div', { class: 'set-buttons' },
        h('button', { type: 'button', class: 'btn tonal', onclick: openPasswordForm }, '修改密码'),
        user.role === 'admin' ? h('a', { class: 'btn tonal', href: '/admin/' }, icon('external', 16), '管理后台') : null,
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
    const data = section('数据',
      row('已同步的数据', Object.entries(counts).map(([k, v]) => `${k} ${v}`).join(' · ')),
      h('div', { class: 'set-buttons' }, h('button', { type: 'button', class: 'btn tonal', onclick: exportJson }, icon('download', 16), '导出 JSON 备份')),
      h('p', { class: 'hint' }, '所有数据都存放在你自己的服务器上，这个页面不会连接任何其他服务。'));

    return h('div', { class: 'settings' }, account, appearance, semester, data);
  },
};

function exportJson() {
  const recs = ctx.store.allRecords().filter((r) => r.deletedAt == null);
  const blob = new Blob([JSON.stringify({ app: 'mintcal-web', exportedAt: new Date().toISOString(), records: recs }, null, 2)], { type: 'application/json' });
  const a = h('a', { href: URL.createObjectURL(blob), download: `mintcal-${new Date().toISOString().slice(0, 10)}.json` });
  document.body.append(a);
  a.click();
  setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
}
