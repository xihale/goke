// Goke · 公开课表分享查看页（原生 ES 模块，零构建，严格 CSP 安全）。

const SVG_NS = 'http://www.w3.org/2000/svg';
const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
const THEME_KEY = 'goke-share-theme';

const MINT = {
  light: {
    primary: '#2E776C', onPrimary: '#FFFFFF', primaryContainer: '#CDEBE3', onPrimaryContainer: '#1B473F',
    secondaryContainer: '#FBE3D6', onSecondaryContainer: '#7A4630',
    background: '#F2F7F5', surface: '#FFFFFF', surfaceHigh: '#F7FAF9', surfaceVariant: '#E9F1EE',
    onSurface: '#24312D', onSurfaceVariant: '#5E6F69', outline: '#6E7F7A', outlineVariant: '#E4ECE9',
    error: '#B23E50', errorContainer: '#FCE9E7', onErrorContainer: '#7A2A22',
  },
  dark: {
    primary: '#7CCDBA', onPrimary: '#0B2A24', primaryContainer: '#24413B', onPrimaryContainer: '#BFEDE2',
    secondaryContainer: '#4A3329', onSecondaryContainer: '#F6D5C3',
    background: '#141A19', surface: '#1E2624', surfaceHigh: '#26302D', surfaceVariant: '#2A3431',
    onSurface: '#E6EEEB', onSurfaceVariant: '#93A39E', outline: '#6C7C77', outlineVariant: '#2A3431',
    error: '#FF8F84', errorContainer: '#4A2A27', onErrorContainer: '#FFD9D5',
  },
};

const COURSE_COLORS = {
  light: [[0xD9E8EF, 0x3B6173], [0xBFDCD5, 0x2C5B53], [0xDAD8EE, 0x4F4D7C], [0xF3EBCF, 0x75662F], [0xF7DFD2, 0x85503A], [0xDFE8DA, 0x4B5F49], [0xF4D9E0, 0x83465A], [0xD2D8EA, 0x454F7A]],
  dark: [[0x2B4350, 0xBFE0F0], [0x24463F, 0xB2E3D8], [0x38355A, 0xD3D0F5], [0x4A4224, 0xF0E5B6], [0x503629, 0xF5D2BF], [0x33442F, 0xCFE5C8], [0x56323E, 0xF4CBD7], [0x303A5E, 0xC9D3F5]],
};

const ICONS = {
  sun: 'M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4M12 8a4 4 0 100 8 4 4 0 000-8z',
  moon: 'M20 14.5A8.5 8.5 0 019.5 4a7 7 0 1010.5 10.5z',
  left: 'M15 5l-7 7 7 7',
  right: 'M9 5l7 7-7 7',
  x: 'M6 6l12 12M18 6L6 18',
  clock: 'M12 3a9 9 0 100 18 9 9 0 000-18zM12 7v5l3 2',
  pin: 'M12 21s6-5.5 6-11a6 6 0 10-12 0c0 5.5 6 11 6 11zM12 7.5a2.5 2.5 0 100 5 2.5 2.5 0 000-5z',
  user: 'M12 4a4 4 0 100 8 4 4 0 000-8zM4 20c0-4 3.6-6 8-6s8 2 8 6',
  calendar: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4',
  repeat: 'M17 3l3 3-3 3M4 11V9a3 3 0 013-3h13M7 21l-3-3 3-3M20 13v2a3 3 0 01-3 3H4',
  download: 'M12 4v11M7 11l5 5 5-5M5 20h14',
  copy: 'M9 9h10v10H9zM5 15H4a1 1 0 01-1-1V4a1 1 0 011-1h10a1 1 0 011 1v1',
  alert: 'M12 9v4M12 17h.01M10.3 3.9L1.8 18a2 2 0 001.7 3h17a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z',
  edit: 'M4 20h4L19 9l-4-4L4 16z',
};

function h(tag, props, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'style') for (const [p, val] of Object.entries(v)) el.style.setProperty(p, String(val));
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k in el && !k.includes('-')) el[k] = v;
    else el.setAttribute(k, v === true ? '' : String(v));
  }
  for (const k of kids.flat(Infinity)) {
    if (k == null || k === false) continue;
    el.append(k instanceof Node ? k : document.createTextNode(String(k)));
  }
  return el;
}

function icon(name, size = 18) {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('width', size);
  svg.setAttribute('height', size);
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '1.8');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');
  svg.classList.add('icon');
  const p = document.createElementNS(SVG_NS, 'path');
  p.setAttribute('d', ICONS[name] || ICONS.alert);
  svg.append(p);
  return svg;
}

const pad = (n, w = 2) => String(n).padStart(w, '0');
const hex = (n) => `#${n.toString(16).padStart(6, '0')}`;
const camelToKebab = (s) => s.replace(/[A-Z]/g, (m) => `-${m.toLowerCase()}`);

function getThemeMode() {
  try {
    const v = localStorage.getItem(THEME_KEY);
    if (v === 'light' || v === 'dark') return v;
  } catch (e) { /* 忽略 */ }
  return 'system';
}

function applyTheme(mode) {
  const sysDark = typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches;
  const dark = mode === 'dark' || (mode === 'system' && sysDark);
  const root = document.documentElement;
  root.dataset.theme = dark ? 'dark' : 'light';
  const pal = MINT[dark ? 'dark' : 'light'];
  for (const [k, v] of Object.entries(pal)) root.style.setProperty(`--${camelToKebab(k)}`, v);
  COURSE_COLORS[dark ? 'dark' : 'light'].forEach(([bg, fg], i) => {
    root.style.setProperty(`--c${i}-bg`, hex(bg));
    root.style.setProperty(`--c${i}-fg`, hex(fg));
  });
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', pal.background);
  return dark;
}

function dayNum(y, m, d) { return Math.round(Date.UTC(y, m - 1, d) / 864e5); }
function parseDate(s) {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s || '');
  return m ? dayNum(+m[1], +m[2], +m[3]) : NaN;
}
function ymd(n) {
  const t = new Date(n * 864e5);
  return { y: t.getUTCFullYear(), m: t.getUTCMonth() + 1, d: t.getUTCDate() };
}
function fmtDate(n) { const { y, m, d } = ymd(n); return `${pad(y, 4)}-${pad(m)}-${pad(d)}`; }
function md(n) { const { m, d } = ymd(n); return `${m}/${d}`; }
function shortDate(n) { const { m, d } = ymd(n); return `${m} 月 ${d} 日`; }
const dow = (n) => ((((n + 3) % 7) + 7) % 7) + 1;
const mondayOf = (n) => n - (dow(n) - 1);
function parseTime(s) {
  const m = /^(\d{1,2}):(\d{2})/.exec(s || '');
  return m ? +m[1] * 60 + +m[2] : NaN;
}
const fmtTime = (min) => `${pad(Math.floor(min / 60))}:${pad(min % 60)}`;
function todayNum(now = new Date()) { return dayNum(now.getFullYear(), now.getMonth() + 1, now.getDate()); }

function formatWeeks(weeks) {
  const s = [...new Set(weeks)].sort((a, b) => a - b);
  if (!s.length) return '';
  const parts = [];
  let a = s[0];
  let p = a;
  for (let i = 1; i <= s.length; i++) {
    const cur = s[i];
    if (cur !== undefined && cur === p + 1) { p = cur; continue; }
    parts.push(a === p ? `${a}` : p === a + 1 ? `${a},${p}` : `${a}-${p}`);
    if (cur !== undefined) { a = cur; p = cur; }
  }
  return parts.join(',');
}

function semesterCalendar(sem) {
  const base = mondayOf(parseDate(sem.week1Monday));
  const periods = [...(sem.periods || [])].sort((a, b) => a.index - b.index);
  const byIndex = new Map(periods.map((p) => [p.index, p]));
  return {
    sem,
    base,
    totalWeeks: sem.totalWeeks,
    periods,
    weekOf: (day) => Math.floor((day - base) / 7) + 1,
    dateOf: (week, d) => base + (week - 1) * 7 + (d - 1),
    weekStart(week) { return this.dateOf(week, 1); },
    weekEnd(week) { return this.dateOf(week, 7); },
    currentWeek(today) { return Math.min(Math.max(this.weekOf(today), 1), sem.totalWeeks); },
    startMin: (i) => (byIndex.has(i) ? parseTime(byIndex.get(i).start) : null),
    endMin: (i) => (byIndex.has(i) ? parseTime(byIndex.get(i).end) : null),
  };
}

function expandCourses(sem, courses, exceptions, from, to) {
  const cal = semesterCalendar(sem);
  const exByIdx = new Map();
  const exById = new Map();
  for (const e of exceptions || []) {
    if (e.deletedAt != null) continue;
    if (e.courseIndex != null) exByIdx.set(`${e.courseIndex}|${e.week}`, e);
    else if (e.courseId != null) exById.set(`${e.courseId}|${e.week}`, e);
  }
  const out = [];
  (courses || []).forEach((c, idx) => {
    if (c.deletedAt != null) return;
    for (const w of [...(c.weeks || [])].sort((a, b) => a - b)) {
      const ex = exByIdx.get(`${idx}|${w}`) ?? (c.id != null ? exById.get(`${c.id}|${w}`) : undefined);
      if (ex && ex.type === 'CANCEL') continue;
      const re = ex && ex.type === 'RESCHEDULE' ? ex : null;
      const week = re?.newWeek ?? w;
      const d = re?.newDayOfWeek ?? c.dayOfWeek;
      const sp = re?.newStartPeriod ?? c.startPeriod;
      const ep = re?.newEndPeriod ?? c.endPeriod;
      const day = cal.dateOf(week, d);
      if (day < from || day > to) continue;
      const s = cal.startMin(sp);
      const e = cal.endMin(ep);
      if (s == null || e == null) continue;
      out.push({
        course: c, week, origWeek: w, day, startPeriod: sp, endPeriod: ep,
        startMin: s, endMin: e, room: re?.newRoom ?? c.room, rescheduled: !!re,
      });
    }
  });
  return out.sort((a, b) => a.day - b.day || a.startMin - b.startMin || a.course.name.localeCompare(b.course.name));
}

function assignLanes(occs) {
  const byDay = new Map();
  for (const o of occs) {
    if (!byDay.has(o.day)) byDay.set(o.day, []);
    byDay.get(o.day).push(o);
  }
  for (const list of byDay.values()) {
    list.sort((a, b) => a.startMin - b.startMin || a.endMin - b.endMin);
    let cluster = [];
    let clusterEnd = -1;
    const flush = () => {
      const lanes = Math.max(1, ...cluster.map((c) => c.lane + 1));
      cluster.forEach((c) => { c.lanes = lanes; });
      cluster = [];
    };
    for (const o of list) {
      if (cluster.length && o.startMin >= clusterEnd) { flush(); clusterEnd = -1; }
      const used = new Set(cluster.filter((c) => c.endMin > o.startMin).map((c) => c.lane));
      let lane = 0;
      while (used.has(lane)) lane++;
      o.lane = lane;
      cluster.push(o);
      clusterEnd = Math.max(clusterEnd, o.endMin);
    }
    flush();
  }
}

function icsEscape(s) {
  return String(s || '')
    .replace(/\\/g, '\\\\')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
    .replace(/\r\n/g, '\n')
    .replace(/\n/g, '\\n');
}

function icsFold(line) {
  const enc = new TextEncoder();
  let out = '';
  let bytes = 0;
  for (const ch of line) {
    const n = enc.encode(ch).length;
    if (bytes + n > 74) {
      out += '\r\n ';
      bytes = 1;
    }
    out += ch;
    bytes += n;
  }
  return out;
}

function toIcsUtc(day, min) {
  const { y, m, d } = ymd(day);
  const hh = Math.floor(min / 60);
  const mm = min % 60;
  const dt = new Date(y, m - 1, d, hh, mm, 0);
  return `${pad(dt.getUTCFullYear(), 4)}${pad(dt.getUTCMonth() + 1)}${pad(dt.getUTCDate())}T${pad(dt.getUTCHours())}${pad(dt.getUTCMinutes())}${pad(dt.getUTCSeconds())}Z`;
}

function buildICS(data) {
  const sem = data.semester;
  const cal = semesterCalendar(sem);
  const lines = [];
  const push = (s) => lines.push(icsFold(s));
  const now = new Date();
  const stamp = `${pad(now.getUTCFullYear(), 4)}${pad(now.getUTCMonth() + 1)}${pad(now.getUTCDate())}T${pad(now.getUTCHours())}${pad(now.getUTCMinutes())}${pad(now.getUTCSeconds())}Z`;

  push('BEGIN:VCALENDAR');
  push('VERSION:2.0');
  push('PRODID:-//Goke//Goke Share 1.0//ZH');
  push('CALSCALE:GREGORIAN');
  const displayName = (data.name || '').trim();
  const calName = [displayName, sem.name || '课表'].filter(Boolean).join(' · ');
  push(`X-WR-CALNAME:${icsEscape(calName)}`);
  if (sem.week1Monday) push(`X-GOKE-WEEK1-MONDAY:${sem.week1Monday}`);

  const exceptions = data.exceptions || [];
  (data.courses || []).forEach((c, idx) => {
    if (c.deletedAt != null) return;
    const exByWeek = new Map();
    for (const ex of exceptions) {
      if (ex.deletedAt != null) continue;
      const matched = ex.courseIndex != null ? ex.courseIndex === idx : (c.id != null && ex.courseId === c.id);
      if (matched) exByWeek.set(ex.week, ex);
    }
    const sMin = cal.startMin(c.startPeriod);
    const eMin = cal.endMin(c.endPeriod);
    if (sMin == null || eMin == null) return;
    const desc = [`第${c.startPeriod} - ${c.endPeriod}节`, c.room, c.teacher].filter(Boolean).join('\n');
    const loc = [c.room, c.teacher].filter(Boolean).join(' ');

    const sorted = [...(c.weeks || [])].sort((a, b) => a - b);
    const runs = [];
    let cur = [];
    for (const w of sorted) {
      if (cur.length && w !== cur[cur.length - 1] + 1) {
        runs.push(cur);
        cur = [];
      }
      cur.push(w);
    }
    if (cur.length) runs.push(cur);

    const courseUid = c.id != null ? c.id : `c${idx}`;
    runs.forEach((run, runIdx) => {
      const firstDay = cal.dateOf(run[0], c.dayOfWeek);
      push('BEGIN:VEVENT');
      push(`UID:${courseUid}-${runIdx}@goke-share`);
      push(`DTSTAMP:${stamp}`);
      push(`DTSTART:${toIcsUtc(firstDay, sMin)}`);
      push(`DTEND:${toIcsUtc(firstDay, eMin)}`);
      push(`SUMMARY:${icsEscape(c.name)}`);
      if (loc) push(`LOCATION:${icsEscape(loc)}`);
      if (desc) push(`DESCRIPTION:${icsEscape(desc)}`);
      if (run.length > 1) push(`RRULE:FREQ=WEEKLY;COUNT=${run.length}`);
      const exWeeks = run.filter((w) => exByWeek.has(w));
      if (exWeeks.length) {
        push('EXDATE:' + exWeeks.map((w) => toIcsUtc(cal.dateOf(w, c.dayOfWeek), sMin)).join(','));
      }
      push('END:VEVENT');
    });

    for (const [w, ex] of exByWeek.entries()) {
      if (ex.type !== 'RESCHEDULE' || !sorted.includes(w)) continue;
      const nw = ex.newWeek ?? w;
      const nd = ex.newDayOfWeek ?? c.dayOfWeek;
      const st = cal.startMin(ex.newStartPeriod ?? c.startPeriod);
      const et = cal.endMin(ex.newEndPeriod ?? c.endPeriod);
      if (st == null || et == null) continue;
      const d = cal.dateOf(nw, nd);
      push('BEGIN:VEVENT');
      push(`UID:${ex.id || `${courseUid}-${w}`}@goke-share`);
      push(`DTSTAMP:${stamp}`);
      push(`DTSTART:${toIcsUtc(d, st)}`);
      push(`DTEND:${toIcsUtc(d, et)}`);
      push(`SUMMARY:${icsEscape(c.name)}`);
      if (ex.newRoom || c.room) push(`LOCATION:${icsEscape(ex.newRoom || c.room)}`);
      push('END:VEVENT');
    }
  });
  push('END:VCALENDAR');
  return lines.join('\r\n') + '\r\n';
}

function downloadBlob(filename, content, mime) {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = h('a', { href: url, download: filename });
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

let toastHost = null;
function showToast(msg) {
  if (!toastHost) {
    toastHost = h('div', { class: 'toasts', 'aria-live': 'polite' });
    document.body.append(toastHost);
  }
  const t = h('div', { class: 'toast' }, msg);
  toastHost.append(t);
  setTimeout(() => t.remove(), 2400);
}

async function copyText(text) {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    try { await navigator.clipboard.writeText(text); return true; } catch (e) { /* 降级 */ }
  }
  const ta = h('textarea', { class: 'copy-scratch', value: text, readonly: true });
  document.body.append(ta);
  ta.select();
  let ok = false;
  try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
  ta.remove();
  return ok;
}

function openCourseDetail(occ) {
  const c = occ.course;
  const rows = [
    ['clock', `${WEEKDAYS[dow(occ.day) - 1]} 第 ${occ.startPeriod}–${occ.endPeriod} 节　${fmtTime(occ.startMin)}–${fmtTime(occ.endMin)}`],
    ['calendar', `第 ${occ.week} 周（${shortDate(occ.day)}）${occ.rescheduled ? ' · 已调课' : ''}`],
    occ.room || c.room ? ['pin', occ.room || c.room] : null,
    c.teacher ? ['user', c.teacher] : null,
    c.weeks?.length ? ['repeat', `上课周次：${formatWeeks(c.weeks)}`] : null,
    c.note ? ['edit', c.note] : null,
  ].filter(Boolean);

  const dlg = h('dialog', { class: 'sheet', 'aria-label': c.name });
  const close = () => dlg.close();
  dlg.addEventListener('click', (e) => { if (e.target === dlg) close(); });
  dlg.addEventListener('close', () => dlg.remove());

  dlg.append(
    h('div', { class: `detail-head c${(c.colorIndex || 0) % 8}` },
      h('h2', {}, c.name),
      h('button', { type: 'button', class: 'icon-btn', 'aria-label': '关闭', onclick: close }, icon('x'))),
    h('ul', { class: 'detail-rows' }, rows.map(([ic, txt]) => h('li', {}, icon(ic), h('span', {}, txt)))),
  );
  document.body.append(dlg);
  dlg.showModal();
}

function formatExpiry(ms) {
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function renderError(root, title, msg) {
  root.replaceChildren(
    h('div', { class: 'error-card' },
      h('div', { class: 'error-icon' }, icon('alert', 28)),
      h('h1', {}, title),
      h('p', {}, msg)),
  );
}

function renderViewer(root, data) {
  const sem = data.semester;
  const cal = semesterCalendar(sem);
  const today = todayNum();
  const curWeek = cal.weekOf(today);
  let viewWeek = cal.currentWeek(today);
  let themeMode = getThemeMode();
  let isDark = applyTheme(themeMode);

  const page = h('div', { class: 'page' });
  const gridContainer = h('div');

  const themeBtn = h('button', {
    type: 'button',
    class: 'icon-btn',
    'aria-label': '切换深浅色主题',
    title: '切换深浅色主题',
    onclick: () => {
      themeMode = isDark ? 'light' : 'dark';
      try { localStorage.setItem(THEME_KEY, themeMode); } catch (e) { /* 忽略 */ }
      isDark = applyTheme(themeMode);
      themeBtn.replaceChildren(icon(isDark ? 'sun' : 'moon'));
    },
  }, icon(isDark ? 'sun' : 'moon'));

  const shareUrl = `${location.origin}/s/${data.code}`;
  const displayName = (data.name || '').trim();
  const headingText = displayName || sem.name || '课表';
  const header = h('section', { class: 'header-card' },
    h('div', { class: 'header-top' },
      h('div', { class: 'title-wrap' },
        h('div', { class: 'title-row' },
          h('h1', {}, headingText),
          h('span', { class: 'badge' }, '实验性')),
        h('div', { class: 'meta-line' },
          displayName && sem.name ? h('span', {}, sem.name) : null,
          h('span', {}, `有效期至 ${formatExpiry(data.expiresAt)}`))),
      themeBtn),
    h('div', { class: 'header-actions' },
      h('button', {
        type: 'button',
        class: 'btn primary small',
        onclick: () => downloadBlob(`goke-${data.code}.ics`, buildICS(data), 'text/calendar;charset=utf-8'),
      }, icon('download', 16), '下载 ICS'),
      h('button', {
        type: 'button',
        class: 'btn tonal small',
        onclick: () => downloadBlob(`goke-${data.code}.json`, JSON.stringify(data, null, 2), 'application/json;charset=utf-8'),
      }, icon('download', 16), '下载 JSON'),
      h('button', {
        type: 'button',
        class: 'btn small',
        onclick: async () => {
          const ok = await copyText(shareUrl);
          showToast(ok ? '已复制分享链接' : '复制失败，请手动复制地址栏链接');
        },
      }, icon('copy', 16), '复制链接')),
  );

  function renderWeek() {
    const week = Math.min(Math.max(viewWeek, 1), sem.totalWeeks);
    viewWeek = week;
    const occs = expandCourses(sem, data.courses, data.exceptions, cal.weekStart(week), cal.weekEnd(week));
    assignLanes(occs);

    const go = (delta) => {
      const next = Math.min(Math.max(viewWeek + delta, 1), sem.totalWeeks);
      if (next !== viewWeek) {
        viewWeek = next;
        renderWeek();
      }
    };

    const bar = h('div', { class: 'week-bar' },
      h('button', { type: 'button', class: 'icon-btn tonal', 'aria-label': '上一周', disabled: week <= 1, onclick: () => go(-1) }, icon('left')),
      h('select', {
        class: 'week-select',
        'aria-label': '选择周次',
        onchange: (e) => { viewWeek = Number(e.target.value); renderWeek(); },
      }, Array.from({ length: sem.totalWeeks }, (_, i) => h('option', { value: i + 1, selected: i + 1 === week }, `第 ${i + 1} 周${i + 1 === curWeek ? '（本周）' : ''}`))),
      h('button', { type: 'button', class: 'icon-btn tonal', 'aria-label': '下一周', disabled: week >= sem.totalWeeks, onclick: () => go(1) }, icon('right')),
      week !== cal.currentWeek(today)
        ? h('button', { type: 'button', class: 'btn tonal small', onclick: () => { viewWeek = cal.currentWeek(today); renderWeek(); } }, '回到本周')
        : null,
      h('span', { class: 'week-range' }, `${md(cal.weekStart(week))} – ${md(cal.weekEnd(week))}`),
    );

    const pos = new Map(cal.periods.map((p, i) => [p.index, i]));
    const grid = h('div', { class: 'tt-grid', style: { '--rows': cal.periods.length } });
    grid.append(h('div', { class: 'tt-corner' }));
    for (let d = 1; d <= 7; d++) {
      const day = cal.dateOf(week, d);
      grid.append(h('div', {
        class: `tt-head${day === today ? ' today' : ''}`,
        style: { 'grid-column': d + 1, 'grid-row': 1 },
      }, h('b', {}, WEEKDAYS[d - 1]), h('span', {}, md(day))));
    }
    cal.periods.forEach((p, i) => {
      grid.append(h('div', { class: 'tt-time', style: { 'grid-row': i + 2, 'grid-column': 1 } }, h('b', {}, p.index), h('span', {}, p.start)));
      for (let d = 1; d <= 7; d++) {
        const day = cal.dateOf(week, d);
        grid.append(h('div', {
          class: `tt-cell${day === today ? ' today' : ''}${i > 0 && (p.index === 5 || p.index === 10) ? ' gap' : ''}`,
          style: { 'grid-row': i + 2, 'grid-column': d + 1 },
        }));
      }
    });
    for (const o of occs) {
      const r1 = pos.get(o.startPeriod);
      const r2 = pos.get(o.endPeriod);
      if (r1 == null || r2 == null) continue;
      const span = o.endPeriod - o.startPeriod + 1;
      const c = o.course;
      grid.append(h('button', {
        type: 'button',
        class: `tt-block c${(c.colorIndex || 0) % 8}${o.rescheduled ? ' moved' : ''}${span === 1 ? ' short' : ''}`,
        style: { 'grid-row': `${r1 + 2} / ${r2 + 3}`, 'grid-column': dow(o.day) + 1, '--lane': o.lane, '--lanes': o.lanes },
        'aria-label': `${c.name}，${WEEKDAYS[dow(o.day) - 1]}第${o.startPeriod}到${o.endPeriod}节${o.room ? `，${o.room}` : ''}`,
        onclick: () => openCourseDetail(o),
      }, h('b', {}, c.name), o.room ? h('span', {}, o.room) : null, !o.room && c.teacher ? h('span', {}, c.teacher) : null));
    }

    const wrap = h('div', { class: 'tt-wrap' }, grid);
    let sx = 0; let sy = 0;
    wrap.addEventListener('touchstart', (e) => {
      const t = e.touches[0];
      if (t) { sx = t.clientX; sy = t.clientY; }
    }, { passive: true });
    wrap.addEventListener('touchend', (e) => {
      const t = e.changedTouches[0];
      if (!t) return;
      const dx = t.clientX - sx;
      const dy = t.clientY - sy;
      if (Math.abs(dx) > 70 && Math.abs(dy) < 50) go(dx < 0 ? 1 : -1);
    }, { passive: true });

    const note = occs.length ? null : h('p', { class: 'tt-empty' }, '这一周没有课');
    gridContainer.replaceChildren(bar, wrap, ...(note ? [note] : []));
  }

  document.addEventListener('keydown', (e) => {
    if (document.querySelector('dialog[open]')) return;
    if (e.key === 'ArrowLeft') { viewWeek = Math.max(viewWeek - 1, 1); renderWeek(); }
    if (e.key === 'ArrowRight') { viewWeek = Math.min(viewWeek + 1, sem.totalWeeks); renderWeek(); }
  });

  if (typeof matchMedia === 'function') {
    matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      if (getThemeMode() === 'system') {
        isDark = applyTheme('system');
        themeBtn.replaceChildren(icon(isDark ? 'sun' : 'moon'));
      }
    });
  }

  document.title = displayName ? `${displayName} · Goke 课表` : `${sem.name ? `${sem.name} · ` : ''}Goke 课表`;
  renderWeek();
  page.append(header, gridContainer);
  root.replaceChildren(page);
}

async function main() {
  applyTheme(getThemeMode());
  const root = document.getElementById('root');
  const code = new URLSearchParams(location.search).get('code')?.trim() || '';
  if (!/^[0-9]{6,12}$/.test(code)) {
    renderError(root, '分享链接无效', '缺少或包含格式不正确的课表分享码。');
    return;
  }
  try {
    const res = await fetch(`/api/v1/share/${encodeURIComponent(code)}`, { headers: { Accept: 'application/json' } });
    if (res.status === 503) {
      renderError(root, '分享功能暂未启用', '服务端已关闭实验性课表分享功能。');
      return;
    }
    if (res.status === 404) {
      renderError(root, '课表不存在或已过期', '该分享链接可能已失效或被分享者撤回。');
      return;
    }
    if (!res.ok) {
      renderError(root, '加载失败', `服务端返回错误（HTTP ${res.status}）。`);
      return;
    }
    const data = await res.json();
    renderViewer(root, data);
  } catch (e) {
    renderError(root, '网络错误', '无法连接到服务器，请稍后重试。');
  }
}

main();
