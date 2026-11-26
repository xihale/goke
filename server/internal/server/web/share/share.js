// Goke · 公开课表分享查看页（1:1 对齐 Android「素纸 Goke」课表，原生 ES 模块，零构建，严格 CSP 安全）。

const SVG_NS = 'http://www.w3.org/2000/svg';
const WEEKDAY_SHORT = ['一', '二', '三', '四', '五', '六', '日'];
const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
const THEME_KEY = 'goke-share-theme';

const ICONS = {
  sun: 'M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4M12 8a4 4 0 100 8 4 4 0 000-8z',
  moon: 'M20 14.5A8.5 8.5 0 019.5 4a7 7 0 1010.5 10.5z',
  left: 'M15 5l-7 7 7 7',
  right: 'M9 5l7 7-7 7',
  chevronDown: 'M6 9l6 6 6-6',
  share: 'M4 12v7a2 2 0 002 2h12a2 2 0 002-2v-7M16 6l-4-4-4 4M12 2v13',
  download: 'M12 4v11M7 11l5 5 5-5M5 20h14',
  copy: 'M9 9h10v10H9zM5 15H4a1 1 0 01-1-1V4a1 1 0 011-1h10a1 1 0 011 1v1',
  alert: 'M12 9v4M12 17h.01M10.3 3.9L1.8 18a2 2 0 001.7 3h17a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z',
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

function icon(name, size = 18, extraClass = '') {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('width', size);
  svg.setAttribute('height', size);
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '1.85');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');
  svg.classList.add('icon');
  if (extraClass) svg.classList.add(extraClass);
  const p = document.createElementNS(SVG_NS, 'path');
  p.setAttribute('d', ICONS[name] || ICONS.alert);
  svg.append(p);
  return svg;
}

const pad = (n, w = 2) => String(n).padStart(w, '0');

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
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', dark ? '#141A19' : '#F2F7F5');
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

function splitRooms(room) {
  return String(room || '').trim().split(/[,，/、;；\s]+/).map((s) => s.trim()).filter(Boolean);
}

function formatRooms(room) {
  const raw = String(room || '').trim();
  if (!raw) return '';
  const parts = splitRooms(raw);
  if (parts.length > 2) return `${parts[0]} 等 ${parts.length} 处`;
  return raw;
}

function fullRoomsText(room) {
  const raw = String(room || '').trim();
  if (!raw) return '';
  const parts = splitRooms(raw);
  return parts.length ? parts.join(' / ') : raw;
}

function groupBoundaries(periodCount) {
  if (periodCount > 8) return new Set([4, 8]);
  if (periodCount > 4) return new Set([4]);
  return new Set();
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

function calcNowIndicator(periods, now = new Date()) {
  if (!periods.length) return null;
  const nowMin = now.getHours() * 60 + now.getMinutes();
  const first = parseTime(periods[0].start);
  const last = parseTime(periods[periods.length - 1].end);
  if (Number.isNaN(first) || Number.isNaN(last) || nowMin < first || nowMin > last) return null;
  for (const p of periods) {
    const s = parseTime(p.start);
    const e = parseTime(p.end);
    if (Number.isNaN(s) || Number.isNaN(e)) continue;
    if (nowMin <= s) return { periodIndex: p.index, pct: 0 };
    if (nowMin <= e) return { periodIndex: p.index, pct: ((nowMin - s) / Math.max(1, e - s)) * 100 };
  }
  return null;
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
  const rooms = fullRoomsText(occ.room || c.room);
  const rows = [
    rooms ? ['教室', rooms] : null,
    c.teacher ? ['教师', c.teacher] : null,
    c.weeks?.length ? ['周次', formatWeeks(c.weeks)] : null,
    ['日期', `第 ${occ.week} 周 · ${shortDate(occ.day)}`],
  ].filter(Boolean);

  const dlg = h('dialog', { class: 'sheet', 'aria-label': c.name });
  const close = () => dlg.close();
  dlg.addEventListener('click', (e) => { if (e.target === dlg) close(); });
  dlg.addEventListener('close', () => dlg.remove());

  dlg.append(
    h('div', { class: 'sheet-handle', 'aria-hidden': 'true' }),
    h('div', { class: 'sheet-body' },
      h('div', { class: 'sheet-title-row' },
        h('span', { class: `sheet-accent c${(c.colorIndex || 0) % 8}`, 'aria-hidden': 'true' }),
        h('h2', {}, c.name)),
      h('p', { class: 'sheet-subtitle' },
        `${WEEKDAYS[dow(occ.day) - 1]} ${fmtTime(occ.startMin)}–${fmtTime(occ.endMin)} · 第 ${occ.startPeriod}–${occ.endPeriod} 节`),
      occ.rescheduled
        ? h('p', { class: 'sheet-resched-hint' }, `本节已调课（第 ${occ.week} 周 · ${shortDate(occ.day)}）`)
        : null,
      rows.map(([label, val]) => h('div', { class: 'value-row' },
        h('span', { class: 'value-label' }, label),
        h('span', { class: 'value-val' }, val))),
      c.note ? h('div', { class: 'sheet-note' }, c.note) : null),
  );
  document.body.append(dlg);
  dlg.showModal();
}

function formatExpiry(ms) {
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function openExportSheet(data, shareUrl) {
  const sem = data.semester || {};
  const displayName = (data.name || '').trim();
  const rows = [
    displayName ? ['分享者', displayName] : null,
    ['有效期至', formatExpiry(data.expiresAt)],
    ['分享码', data.code],
  ].filter(Boolean);

  const dlg = h('dialog', { class: 'sheet', 'aria-label': '导出与分享' });
  const close = () => dlg.close();
  dlg.addEventListener('click', (e) => { if (e.target === dlg) close(); });
  dlg.addEventListener('close', () => dlg.remove());

  dlg.append(
    h('div', { class: 'sheet-handle', 'aria-hidden': 'true' }),
    h('div', { class: 'sheet-body' },
      h('div', { class: 'sheet-title-row' },
        h('span', { class: 'sheet-accent', 'aria-hidden': 'true' }),
        h('h2', {}, '课表分享')),
      h('p', { class: 'sheet-subtitle' }, sem.name || 'Goke 课表'),
      rows.map(([label, val]) => h('div', { class: 'value-row' },
        h('span', { class: 'value-label' }, label),
        h('span', { class: 'value-val' }, val))),
      h('div', { class: 'sheet-actions' },
        h('button', {
          type: 'button',
          class: 'pill-btn primary',
          onclick: () => {
            downloadBlob(`goke-${data.code}.ics`, buildICS(data), 'text/calendar;charset=utf-8');
            close();
            showToast('已开始下载 ICS 日历');
          },
        }, icon('download', 16), '下载 ICS'),
        h('button', {
          type: 'button',
          class: 'pill-btn',
          onclick: () => {
            downloadBlob(`goke-${data.code}.json`, JSON.stringify(data, null, 2), 'application/json;charset=utf-8');
            close();
            showToast('已开始下载 JSON 数据');
          },
        }, '下载 JSON'),
        h('button', {
          type: 'button',
          class: 'pill-btn',
          onclick: async () => {
            const ok = await copyText(shareUrl);
            close();
            showToast(ok ? '已复制分享链接' : '复制失败，请手动复制地址栏链接');
          },
        }, icon('copy', 15), '复制链接'))),
  );
  document.body.append(dlg);
  dlg.showModal();
}

function renderError(root, title, msg, retryable = false) {
  root.replaceChildren(
    h('div', { class: 'error-card' },
      h('div', { class: 'error-icon' }, icon('alert', 28)),
      h('h1', {}, title),
      h('p', {}, msg),
      retryable ? h('button', { type: 'button', class: 'btn primary', onclick: () => main() }, '重新加载') : null),
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

  const themeBtn = h('button', {
    type: 'button',
    class: 'ghost-icon-btn',
    'aria-label': '切换深浅色主题',
    title: '切换深浅色主题',
    onclick: () => {
      themeMode = isDark ? 'light' : 'dark';
      try { localStorage.setItem(THEME_KEY, themeMode); } catch (e) { /* 忽略 */ }
      isDark = applyTheme(themeMode);
      themeBtn.replaceChildren(icon(isDark ? 'sun' : 'moon', 21));
    },
  }, icon(isDark ? 'sun' : 'moon', 21));

  const shareUrl = `${location.origin}/s/${data.code}`;
  const displayName = (data.name || '').trim();
  const boundaries = groupBoundaries(cal.periods.length);

  const prevBtn = h('button', {
    type: 'button',
    class: 'desk-step',
    'aria-label': '上一周',
    onclick: () => animateToWeek(viewWeek - 1),
  }, icon('left', 18));

  const nextBtn = h('button', {
    type: 'button',
    class: 'desk-step',
    'aria-label': '下一周',
    onclick: () => animateToWeek(viewWeek + 1),
  }, icon('right', 18));

  const nonEmptyWeeks = new Set(
    expandCourses(sem, data.courses, data.exceptions, cal.weekStart(1), cal.weekEnd(sem.totalWeeks))
      .map((o) => o.week),
  );

  function openWeekPickerSheet() {
    const dlg = h('dialog', { class: 'sheet', 'aria-label': '选择周次' });
    const close = () => dlg.close();
    dlg.addEventListener('click', (e) => { if (e.target === dlg) close(); });
    dlg.addEventListener('close', () => dlg.remove());

    const defaultWeek = cal.currentWeek(today);
    const cells = Array.from({ length: sem.totalWeeks }, (_, i) => {
      const w = i + 1;
      const isSelected = w === viewWeek;
      const isCurrent = w === curWeek;
      const isEmpty = !nonEmptyWeeks.has(w);
      return h('button', {
        type: 'button',
        class: `week-cell${isSelected ? ' is-selected' : ''}${isCurrent ? ' is-current' : ''}${isEmpty ? ' is-empty' : ''}`,
        'aria-current': isSelected ? 'true' : null,
        'aria-label': `第 ${w} 周${isCurrent ? '（本周）' : ''}，${md(cal.weekStart(w))} 起`,
        onclick: () => {
          close();
          animateToWeek(w);
        },
      },
      h('span', { class: 'week-cell-num' }, w),
      h('span', { class: 'week-cell-sub' }, isCurrent ? '本周' : md(cal.weekStart(w))));
    });

    dlg.append(
      h('div', { class: 'sheet-handle', 'aria-hidden': 'true' }),
      h('div', { class: 'sheet-body' },
        h('div', { class: 'week-sheet-head' },
          h('div', { class: 'sheet-title-row' },
            h('span', { class: 'sheet-accent', 'aria-hidden': 'true' }),
            h('h2', {}, '选择周次')),
          viewWeek !== defaultWeek
            ? h('button', {
              type: 'button',
              class: 'btn-today',
              onclick: () => {
                close();
                animateToWeek(defaultWeek);
              },
            }, '回本周')
            : null),
        h('p', { class: 'sheet-subtitle' }, `${sem.name ? `${sem.name} · ` : ''}共 ${sem.totalWeeks} 周`),
        h('div', { class: 'week-grid' }, cells)),
    );
    document.body.append(dlg);
    dlg.showModal();
  }

  const weekH1 = h('h1', {});
  const weekSub = h('p', { class: 'week-sub' });
  const titleAnim = h('div', { class: 'week-title-anim' },
    h('div', { class: 'week-heading-row' },
      weekH1,
      icon('chevronDown', 14, 'week-chevron')),
    weekSub);

  const todayBtn = h('button', {
    type: 'button',
    class: 'btn-today',
    onclick: () => animateToWeek(cal.currentWeek(today)),
  }, '回本周');

  const topbar = h('header', { class: 'topbar' },
    h('div', { class: 'top-left' },
      prevBtn,
      h('button', {
        type: 'button',
        class: 'week-picker-btn',
        'aria-label': '选择周次',
        onclick: () => openWeekPickerSheet(),
      }, titleAnim),
      nextBtn,
      todayBtn),
    h('div', { class: 'top-actions' },
      h('button', {
        type: 'button',
        class: 'ghost-icon-btn',
        'aria-label': '导出与分享',
        title: '导出与分享',
        onclick: () => openExportSheet(data, shareUrl),
      }, icon('share', 21)),
      themeBtn),
  );

  const track = h('div', { class: 'tt-track' });
  const pager = h('div', { class: 'tt-pager' }, track);

  function updateTopbar(dir = 0, targetWeek = viewWeek) {
    const w = Math.min(Math.max(targetWeek, 1), sem.totalWeeks);
    weekH1.textContent = `第 ${w} 周`;
    weekSub.textContent = displayName
      ? `${md(cal.weekStart(w))} – ${md(cal.weekEnd(w))} · ${displayName}`
      : `${md(cal.weekStart(w))} – ${md(cal.weekEnd(w))}`;
    prevBtn.disabled = w <= 1;
    nextBtn.disabled = w >= sem.totalWeeks;
    todayBtn.hidden = w === cal.currentWeek(today);
    titleAnim.classList.remove('dir-next', 'dir-prev');
    if (dir !== 0) {
      void titleAnim.offsetWidth;
      titleAnim.classList.add(dir > 0 ? 'dir-next' : 'dir-prev');
    }
  }

  function buildWeekSlide(week) {
    if (week < 1 || week > sem.totalWeeks) {
      return h('div', { class: 'tt-slide', 'aria-hidden': 'true' });
    }
    const occs = expandCourses(sem, data.courses, data.exceptions, cal.weekStart(week), cal.weekEnd(week));
    assignLanes(occs);

    const rowTracks = ['auto'];
    const rowOf = new Map();
    const gapAfterIndex = new Set();

    cal.periods.forEach((p, idx) => {
      rowTracks.push('var(--row-h)');
      rowOf.set(p.index, rowTracks.length);
      if (idx < cal.periods.length - 1 && boundaries.has(p.index)) {
        gapAfterIndex.add(p.index);
        rowTracks.push('14px');
      }
    });

    const grid = h('div', { class: 'tt-grid', style: { 'grid-template-rows': rowTracks.join(' ') } });
    grid.append(h('div', { class: 'tt-corner' }));
    for (let d = 1; d <= 7; d++) {
      const day = cal.dateOf(week, d);
      grid.append(h('div', {
        class: `tt-head${day === today ? ' today' : ''}`,
        style: { 'grid-column': d + 1, 'grid-row': 1 },
      }, h('div', { class: 'tt-head-pill' }, h('b', {}, WEEKDAY_SHORT[d - 1]), h('span', {}, md(day)))));
    }

    cal.periods.forEach((p) => {
      const r = rowOf.get(p.index);
      grid.append(h('div', { class: 'tt-time', style: { 'grid-row': r, 'grid-column': 1 } },
        h('b', {}, p.index),
        h('span', {}, p.start),
        h('span', {}, p.end)));
      if (gapAfterIndex.has(p.index)) {
        grid.append(h('div', { class: 'tt-gap', style: { 'grid-row': r + 1 }, 'aria-hidden': 'true' }));
      }
    });

    const nowDt = new Date();
    const nowMin = nowDt.getHours() * 60 + nowDt.getMinutes();

    for (const o of occs) {
      const r1 = rowOf.get(o.startPeriod);
      const r2 = rowOf.get(o.endPeriod);
      if (r1 == null || r2 == null) continue;
      const span = o.endPeriod - o.startPeriod + 1;
      const c = o.course;
      const roomText = formatRooms(o.room);
      const showTeacher = span >= 2 && c.teacher;

      let phaseClass = '';
      let progStyle = null;
      if (o.day < today || (o.day === today && nowMin >= o.endMin)) {
        phaseClass = ' finished';
      } else if (o.day === today && nowMin > o.startMin && nowMin < o.endMin) {
        phaseClass = ' ongoing';
        const pct = ((nowMin - o.startMin) / Math.max(1, o.endMin - o.startMin)) * 100;
        progStyle = `${pct.toFixed(1)}%`;
      }

      const blockStyle = {
        'grid-row': `${r1} / ${r2 + 1}`,
        'grid-column': dow(o.day) + 1,
        '--lane': o.lane,
        '--lanes': o.lanes,
      };
      if (progStyle) blockStyle['--prog'] = progStyle;

      grid.append(h('button', {
        type: 'button',
        class: `tt-block c${(c.colorIndex || 0) % 8}${phaseClass}${span === 1 ? ' short' : ''}`,
        style: blockStyle,
        'aria-label': `${c.name}，${WEEKDAYS[dow(o.day) - 1]}第${o.startPeriod}到${o.endPeriod}节${o.room ? `，${o.room}` : ''}`,
        onclick: () => openCourseDetail(o),
      },
      h('b', {}, c.name),
      roomText ? h('span', { class: 'tt-loc' }, roomText) : null,
      showTeacher ? h('span', { class: 'tt-teacher' }, c.teacher) : null,
      o.rescheduled ? h('span', { class: 'tt-resched' }, '已调课') : null));
    }

    if (week === curWeek && today >= cal.weekStart(week) && today <= cal.weekEnd(week)) {
      const nowInfo = calcNowIndicator(cal.periods, nowDt);
      const nowRow = nowInfo ? rowOf.get(nowInfo.periodIndex) : null;
      if (nowRow != null) {
        grid.append(h('div', {
          class: 'tt-now',
          style: { 'grid-column': dow(today) + 1, 'grid-row': nowRow, '--now-offset': `${nowInfo.pct.toFixed(1)}%` },
          'aria-hidden': 'true',
        }, h('div', { class: 'tt-now-line' })));
      }
    }

    const note = occs.length ? null : h('p', { class: 'tt-empty' }, '这一周没有课');
    return h('div', { class: 'tt-slide' }, grid, note);
  }

  let settling = false;
  let pendingWeek = null;
  let settleTimer = 0;
  let suppressClickUntil = 0;

  function renderSlides() {
    track.classList.remove('settling');
    track.style.setProperty('--drag-x', '0px');
    track.replaceChildren(
      buildWeekSlide(viewWeek - 1),
      buildWeekSlide(viewWeek),
      buildWeekSlide(viewWeek + 1),
    );
  }

  function finishSettle() {
    clearTimeout(settleTimer);
    settleTimer = 0;
    if (!settling) return;
    settling = false;
    const changed = pendingWeek != null && pendingWeek !== viewWeek;
    if (pendingWeek != null) {
      viewWeek = pendingWeek;
      pendingWeek = null;
    }
    if (changed) {
      renderSlides();
      updateTopbar(0);
    } else {
      track.classList.remove('settling');
      track.style.setProperty('--drag-x', '0px');
    }
  }

  track.addEventListener('transitionend', (e) => {
    if (e.target === track && e.propertyName === 'transform') finishSettle();
  });

  function animateToWeek(targetWeek, fromDragX = null) {
    const next = Math.min(Math.max(targetWeek, 1), sem.totalWeeks);
    if (settling) finishSettle();

    const reduceMotion = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (next === viewWeek) {
      if (fromDragX != null && Math.abs(fromDragX) > 0.5 && !reduceMotion) {
        pendingWeek = viewWeek;
        settling = true;
        track.classList.add('settling');
        void track.offsetWidth;
        track.style.setProperty('--drag-x', '0px');
        settleTimer = setTimeout(finishSettle, 320);
      } else {
        track.classList.remove('settling');
        track.style.setProperty('--drag-x', '0px');
      }
      return;
    }

    const dir = next > viewWeek ? 1 : -1;
    if (reduceMotion) {
      viewWeek = next;
      renderSlides();
      updateTopbar(0);
      return;
    }

    if (Math.abs(next - viewWeek) > 1 && track.children.length === 3) {
      const slotIdx = dir > 0 ? 2 : 0;
      track.replaceChild(buildWeekSlide(next), track.children[slotIdx]);
    }

    pendingWeek = next;
    settling = true;
    updateTopbar(dir, next);

    if (fromDragX == null) {
      track.classList.remove('settling');
      track.style.setProperty('--drag-x', '0px');
    }
    track.classList.add('settling');
    void track.offsetWidth;
    const width = pager.clientWidth || window.innerWidth || 360;
    track.style.setProperty('--drag-x', `${dir > 0 ? -width : width}px`);
    settleTimer = setTimeout(finishSettle, 320);
  }

  let dragActive = false;
  let dragAxis = null;
  let startX = 0;
  let startY = 0;
  let dragX = 0;
  let samples = [];

  pager.addEventListener('touchstart', (e) => {
    if (e.touches.length !== 1) return;
    if (settling) finishSettle();
    const t = e.touches[0];
    startX = t.clientX;
    startY = t.clientY;
    dragX = 0;
    dragAxis = null;
    dragActive = true;
    samples = [{ x: startX, t: performance.now() }];
  }, { passive: true });

  pager.addEventListener('touchmove', (e) => {
    if (!dragActive || e.touches.length !== 1) return;
    const t = e.touches[0];
    const dx = t.clientX - startX;
    const dy = t.clientY - startY;
    if (dragAxis === null) {
      if (Math.hypot(dx, dy) < 8) return;
      if (Math.abs(dy) > Math.abs(dx) * 1.1) {
        dragAxis = 'y';
        dragActive = false;
        return;
      }
      dragAxis = 'x';
      track.classList.remove('settling');
    }
    if (dragAxis === 'x') {
      e.preventDefault();
      if ((dx > 0 && viewWeek <= 1) || (dx < 0 && viewWeek >= sem.totalWeeks)) {
        dragX = Math.sign(dx) * Math.min(84, Math.abs(dx) * 0.28);
      } else {
        dragX = dx;
      }
      track.style.setProperty('--drag-x', `${dragX.toFixed(1)}px`);
      const now = performance.now();
      samples.push({ x: t.clientX, t: now });
      while (samples.length > 2 && now - samples[0].t > 100) samples.shift();
    }
  }, { passive: false });

  function onTouchFinish() {
    if (!dragActive) return;
    dragActive = false;
    if (dragAxis !== 'x') return;
    suppressClickUntil = performance.now() + 260;
    const first = samples[0];
    const last = samples[samples.length - 1];
    const dt = last && first ? Math.max(1, last.t - first.t) : 1;
    const vx = last && first ? (last.x - first.x) / dt : 0;
    const w = pager.clientWidth || 360;
    if ((dragX > w * 0.22 || vx > 0.35) && viewWeek > 1) {
      animateToWeek(viewWeek - 1, dragX);
    } else if ((dragX < -w * 0.22 || vx < -0.35) && viewWeek < sem.totalWeeks) {
      animateToWeek(viewWeek + 1, dragX);
    } else {
      animateToWeek(viewWeek, dragX);
    }
  }

  pager.addEventListener('touchend', onTouchFinish, { passive: true });
  pager.addEventListener('touchcancel', onTouchFinish, { passive: true });
  pager.addEventListener('click', (e) => {
    if (performance.now() < suppressClickUntil) {
      e.preventDefault();
      e.stopPropagation();
    }
  }, { capture: true });

  document.addEventListener('keydown', (e) => {
    if (document.querySelector('dialog[open]')) return;
    if (e.key === 'ArrowLeft') animateToWeek(viewWeek - 1);
    if (e.key === 'ArrowRight') animateToWeek(viewWeek + 1);
  });

  if (typeof matchMedia === 'function') {
    matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      if (getThemeMode() === 'system') {
        isDark = applyTheme('system');
        themeBtn.replaceChildren(icon(isDark ? 'sun' : 'moon', 21));
      }
    });
  }

  document.title = displayName ? `${displayName} · Goke 课表` : `${sem.name ? `${sem.name} · ` : ''}Goke 课表`;
  renderSlides();
  updateTopbar(0);
  const footerText = `Goke 课表分享 · ${sem.name ? `${sem.name} · ` : ''}有效期至 ${formatExpiry(data.expiresAt)}`;
  page.append(topbar, pager, h('footer', { class: 'page-footer' }, footerText));
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
      renderError(root, '加载失败', `服务端返回错误（HTTP ${res.status}）。`, true);
      return;
    }
    const data = await res.json();
    renderViewer(root, data);
  } catch (e) {
    renderError(root, '网络错误', '无法连接到服务器，请稍后重试。', true);
  }
}

main();
