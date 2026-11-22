// 入口：登录、外壳（导航 / 顶栏）、路由、自动同步。

import { ApiError, NetworkError, createApi } from './api.js';
import { ctx } from './ctx.js';
import { h, icon, toast } from './dom.js';
import { createStore, syncPollMs } from './store.js';
import { applyTheme, loadTheme } from './theme.js';
import { calendarView } from './views/calendar.js';
import { settingsView, syncText } from './views/settings.js';
import { timetableView } from './views/timetable.js';
import { todayView } from './views/today.js';
import { todoView } from './views/todo.js';

const TOKEN_KEY = 'goke-web-token';
const USER_KEY = 'goke-web-user';
const VIEWS = { today: todayView, timetable: timetableView, calendar: calendarView, todo: todoView, settings: settingsView };
const NAV = [['today', '今日', 'sun'], ['timetable', '课表', 'grid'], ['calendar', '日历', 'calendar'], ['todo', '待办', 'check'], ['settings', '设置', 'settings']];

const root = document.getElementById('root');
let token = localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY);
let shell = null;
let route = 'today';
let timers = [];
let lanMode = false;

const api = createApi({ getToken: () => token, onUnauthorized: () => logout(true) });
const store = createStore({ api, storage: localStorage });
Object.assign(ctx, { store, api, lanMode: false, rerender: render, go, logout });

applyTheme(loadTheme());
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => { applyTheme(loadTheme()); if (shell) render(); });

// ---------- 路由 ----------

function routeFromHash() {
  const r = location.hash.replace(/^#\/?/, '');
  return VIEWS[r] ? r : 'today';
}

function go(r) {
  if (location.hash === `#/${r}`) { route = r; render(); window.scrollTo(0, 0); } else location.hash = `#/${r}`;
}

window.addEventListener('hashchange', () => {
  if (!shell) return;
  route = routeFromHash();
  render();
  window.scrollTo(0, 0);
});

// ---------- 渲染 ----------

let rafId = 0;
const scheduleRender = () => { if (!rafId) rafId = requestAnimationFrame(() => { rafId = 0; render(); }); };

function render() {
  if (!shell) return;
  const view = VIEWS[route];
  const head = view.head();
  shell.eyebrow.textContent = head.eyebrow || '';
  shell.eyebrow.hidden = !head.eyebrow;
  shell.title.textContent = head.title;
  document.title = `${head.title} · Goke`;
  shell.navItems.forEach((b) => {
    if (b.dataset.route === route) b.setAttribute('aria-current', 'page'); else b.removeAttribute('aria-current');
  });
  shell.addBtn.hidden = !view.add;
  shell.fab.hidden = !view.add;
  updateSyncChip();

  // 重新渲染前记住输入框的焦点与内容（快速添加框），渲染后恢复
  const ae = document.activeElement;
  const keep = ae && ae.dataset && ae.dataset.keep;
  const keepValue = keep ? ae.value : null;
  const keepPos = keep ? ae.selectionStart : null;
  shell.view.replaceChildren(view.render());
  shell.view.dataset.view = route;
  if (keep) {
    const el = shell.view.querySelector(`[data-keep="${keep}"]`);
    if (el) { el.value = keepValue; el.focus(); try { el.setSelectionRange(keepPos, keepPos); } catch (e) { /* 忽略 */ } }
  }
}

function updateSyncChip() {
  if (!shell) return;
  const s = store.status;
  const chip = shell.sync;
  chip.className = `sync-chip ${s}${store.pending ? ' pending' : ''}`;
  chip.replaceChildren(icon(s === 'offline' ? 'cloudOff' : 'sync', 16), h('span', {}, syncText(store)));
  chip.title = '点击立即同步';
}

function buildShell(user) {
  const navItems = NAV.map(([id, label, ic]) => h('a', { class: `nav-item${id === 'settings' ? ' nav-last' : ''}`, href: `#/${id}`, dataset: { route: id } }, icon(ic, 24), h('span', {}, label)));
  const eyebrow = h('div', { class: 'eyebrow' });
  const title = h('h1', {});
  const sync = h('button', { type: 'button', class: 'sync-chip', onclick: () => { store.sync().catch((e) => toast(`同步失败：${e.message}`)); } });
  const addBtn = h('button', { type: 'button', class: 'btn primary add-btn', onclick: () => VIEWS[route].add && VIEWS[route].add() }, icon('plus', 18), '添加');
  const fab = h('button', { type: 'button', class: 'fab', 'aria-label': '添加', onclick: () => VIEWS[route].add && VIEWS[route].add() }, icon('plus', 26));
  const view = h('main', { class: 'view', id: 'view' });
  const el = h('div', { class: 'shell' },
    h('nav', { class: 'nav', 'aria-label': '主导航' }, h('div', { class: 'nav-brand', 'aria-hidden': 'true' }, h('img', { src: '/app/icon.svg', alt: '', width: 40, height: 40 })), navItems),
    h('div', { class: 'content' },
      h('header', { class: 'topbar' }, h('div', { class: 'titles' }, eyebrow, title), h('div', { class: 'top-actions' }, sync, addBtn)),
      view),
    fab);
  root.replaceChildren(el);
  return { el, eyebrow, title, navItems, sync, addBtn, fab, view, user };
}

// ---------- 登录 / 退出 ----------

function showLogin(message = '') {
  stopTimers();
  shell = null;
  const err = h('p', { class: 'form-error', role: 'alert', hidden: !message }, message);
  const btn = h('button', { type: 'submit', class: 'btn primary wide-btn' }, lanMode ? '连接' : '登录');

  if (lanMode) {
    const pin = h('input', {
      class: 'input',
      name: 'pin',
      type: 'text',
      inputmode: 'numeric',
      autocomplete: 'one-time-code',
      maxlength: 6,
      pattern: '[0-9]{6}',
      placeholder: '输入 6 位数字连接码',
      required: true,
      autocapitalize: 'none',
      spellcheck: false,
    });
    const form = h('form', { class: 'login-card card', onsubmit: async (e) => {
      e.preventDefault();
      err.hidden = true;
      btn.disabled = true;
      try {
        const device = `网页（${navigator.userAgentData?.platform || navigator.platform || '浏览器'}）`;
        const res = await api.login('lan', pin.value.trim(), device);
        token = res.token;
        localStorage.removeItem(TOKEN_KEY);
        sessionStorage.removeItem(TOKEN_KEY);
        localStorage.setItem(TOKEN_KEY, token);
        const loggedUser = res.user?.mode ? res.user : { ...res.user, mode: 'lan' };
        localStorage.setItem(USER_KEY, JSON.stringify(loggedUser));
        startApp(loggedUser);
      } catch (ex) {
        err.textContent = ex instanceof NetworkError ? '无法连接手机服务，请检查网络或确认共享仍处于开启状态' : ex.message || '连接码错误';
        err.hidden = false;
        btn.disabled = false;
        pin.value = '';
        pin.focus();
      }
    } },
    h('img', { class: 'login-logo', src: '/app/icon.svg', alt: '', width: 72, height: 72 }),
    h('h1', {}, 'Goke'),
    h('p', { class: 'login-sub' }, '手机局域网直连：输入手机端显示的 6 位连接码，实时管理课表与待办'),
    h('label', { class: 'field' }, h('span', { class: 'field-label' }, '6 位连接码（PIN）'), pin),
    err, btn,
    h('p', { class: 'hint center' }, '连接码仅本次共享有效；20 分钟无操作手机端将自动停止。'));
    root.replaceChildren(h('main', { class: 'login' }, form));
    document.title = '连接手机 · Goke';
    pin.focus();
    return;
  }

  const user = h('input', { class: 'input', name: 'username', autocomplete: 'username', required: true, autocapitalize: 'none', spellcheck: false });
  const pass = h('input', { class: 'input', name: 'password', type: 'password', autocomplete: 'current-password', required: true });
  const keepBox = h('input', { type: 'checkbox', checked: true });
  const form = h('form', { class: 'login-card card', onsubmit: async (e) => {
    e.preventDefault();
    err.hidden = true;
    btn.disabled = true;
    try {
      const device = `网页（${navigator.userAgentData?.platform || navigator.platform || '浏览器'}）`;
      const res = await api.login(user.value.trim(), pass.value, device);
      token = res.token;
      localStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(TOKEN_KEY);
      (keepBox.checked ? localStorage : sessionStorage).setItem(TOKEN_KEY, token);
      localStorage.setItem(USER_KEY, JSON.stringify(res.user));
      startApp(res.user);
    } catch (ex) {
      err.textContent = ex instanceof NetworkError ? '无法连接服务器，请检查网络' : ex.message || '登录失败';
      err.hidden = false;
      btn.disabled = false;
      pass.value = '';
      pass.focus();
    }
  } },
  h('img', { class: 'login-logo', src: '/app/icon.svg', alt: '', width: 72, height: 72 }),
  h('h1', {}, 'Goke'),
  h('p', { class: 'login-sub' }, '登录你自己的服务器账号，查看并编辑课表、日程与待办'),
  h('label', { class: 'field' }, h('span', { class: 'field-label' }, '用户名'), user),
  h('label', { class: 'field' }, h('span', { class: 'field-label' }, '密码'), pass),
  h('label', { class: 'check-row' }, keepBox, h('span', {}, '在这台设备上保持登录')),
  err, btn,
  h('p', { class: 'hint center' }, '没有账号？请联系服务器管理员创建，这里不提供注册。'));
  root.replaceChildren(h('main', { class: 'login' }, form));
  document.title = '登录 · Goke';
  user.focus();
}

async function logout(auto = false) {
  if (!token) return;
  const t = token;
  token = null;
  if (!auto && t) {
    try { await createApi({ getToken: () => t }).logout(); } catch (e) { /* 离线时也要能退出 */ }
  }
  localStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  store.unload(!auto); // 主动退出清掉本机缓存；登录失效时保留，重新登录后继续推送未同步的改动
  showLogin(auto ? '登录已失效，请重新登录' : '');
}

// ---------- 启动 ----------

function stopTimers() {
  timers.forEach(clearInterval);
  timers = [];
  store.stopPolling();
}

function startApp(user) {
  ctx.user = user;
  ctx.lanMode = lanMode || user?.mode === 'lan';
  store.load(user);
  route = routeFromHash();
  shell = buildShell(user);
  render();
  store.sync().catch(() => {});
  stopTimers();
  store.startPolling(syncPollMs(user, ctx.lanMode), () => !document.hidden);
  timers.push(setInterval(() => { if (!document.hidden) render(); }, 60000)); // 「现在」随时间推移
}

store.subscribe((kind) => {
  if (!shell) return;
  if (kind === 'data') scheduleRender();
  else { updateSyncChip(); if (route === 'settings') scheduleRender(); }
});

document.addEventListener('visibilitychange', () => { if (!document.hidden && shell) { store.sync().catch(() => {}); render(); } });
window.addEventListener('online', () => { if (shell) store.sync().catch(() => {}); });

document.addEventListener('keydown', (e) => {
  if (!shell || e.metaKey || e.ctrlKey || e.altKey) return;
  if (document.querySelector('dialog[open]')) return;
  const t = e.target;
  if (t && (t.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(t.tagName))) return;
  const view = VIEWS[route];
  if (view.onKey && view.onKey(e)) { e.preventDefault(); return; }
  if (e.key === 'n' && view.add) { e.preventDefault(); view.add(); }
});

async function detectMode() {
  try {
    const info = await api.mode();
    lanMode = info?.mode === 'lan';
  } catch {
    lanMode = false;
  }
  ctx.lanMode = lanMode;
  return lanMode;
}

async function boot() {
  await detectMode();
  if (!token) { showLogin(); return; }
  let cached = null;
  try { cached = JSON.parse(localStorage.getItem(USER_KEY) || 'null'); } catch (e) { cached = null; }
  try {
    const me = await api.me();
    const mode = me.mode || (lanMode ? 'lan' : undefined);
    const user = { id: me.id, username: me.username, role: me.role, ...(mode ? { mode } : {}) };
    if (user.mode === 'lan') {
      lanMode = true;
      ctx.lanMode = true;
    }
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    if (token) startApp(user);
  } catch (e) {
    if (e instanceof NetworkError && cached) startApp(cached); // 离线打开：用本机缓存
    else if (e instanceof ApiError && e.status === 401) { if (token) logout(true); }
    else showLogin(e instanceof NetworkError ? '无法连接服务器' : e.message);
  }
}

boot();
