// 日期与时间的纯函数工具（不依赖 DOM，Node 里也能测试）。
// 日期用「自 1970-01-01 起的天数」表示（整数），避免时区与夏令时问题；
// 时间用「当天的分钟数」表示。

export const pad = (n, w = 2) => String(n).padStart(w, '0');

export const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

export function dayNum(y, m, d) {
  return Math.round(Date.UTC(y, m - 1, d) / 864e5);
}

/** 解析 `YYYY-MM-DD`（后面可以跟时间），失败返回 NaN。 */
export function parseDate(s) {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s || '');
  return m ? dayNum(+m[1], +m[2], +m[3]) : NaN;
}

export function ymd(n) {
  const t = new Date(n * 864e5);
  return { y: t.getUTCFullYear(), m: t.getUTCMonth() + 1, d: t.getUTCDate() };
}

export function fmtDate(n) {
  const { y, m, d } = ymd(n);
  return `${pad(y, 4)}-${pad(m)}-${pad(d)}`;
}

/** 星期几：1=周一 … 7=周日。 */
export const dow = (n) => ((((n + 3) % 7) + 7) % 7) + 1;

export const mondayOf = (n) => n - (dow(n) - 1);

export function daysInMonth(y, m) {
  return new Date(Date.UTC(y, m, 0)).getUTCDate();
}

export function parseTime(s) {
  const m = /^(\d{1,2}):(\d{2})/.exec(s || '');
  return m ? +m[1] * 60 + +m[2] : NaN;
}

export const fmtTime = (min) => `${pad(Math.floor(min / 60))}:${pad(min % 60)}`;

/** `2026-09-01T08:30`（也接受空格分隔、带秒、仅日期）→ {day, min}；失败返回 null。 */
export function parseDateTime(s) {
  const m = /^(\d{4}-\d{2}-\d{2})(?:[T ](\d{1,2}:\d{2}))?/.exec(s || '');
  return m ? { day: parseDate(m[1]), min: m[2] ? parseTime(m[2]) : 0 } : null;
}

export const fmtDateTime = (day, min) => `${fmtDate(day)}T${fmtTime(min)}`;

export function todayNum(now = new Date()) {
  return dayNum(now.getFullYear(), now.getMonth() + 1, now.getDate());
}

export const minutesOfDay = (now = new Date()) => now.getHours() * 60 + now.getMinutes();

/** 月份加减：返回 {y, m}。 */
export function addMonths(y, m, k) {
  const t = y * 12 + (m - 1) + k;
  return { y: Math.floor(t / 12), m: (t % 12) + 1 };
}

export function monthTitle(y, m) {
  return `${y} 年 ${m} 月`;
}

export function shortDate(n) {
  const { m, d } = ymd(n);
  return `${m} 月 ${d} 日`;
}

export function weekdayName(n) {
  return WEEKDAYS[dow(n) - 1];
}

/** 生成 UUID；非安全上下文（http 内网部署）没有 crypto.randomUUID，用 getRandomValues 兜底。 */
export function uuid() {
  const c = globalThis.crypto;
  if (c && c.randomUUID) return c.randomUUID();
  const b = new Uint8Array(16);
  if (c && c.getRandomValues) c.getRandomValues(b);
  else for (let i = 0; i < 16; i++) b[i] = Math.floor(Math.random() * 256);
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = [...b].map((x) => pad(x.toString(16)));
  return `${h.slice(0, 4).join('')}-${h.slice(4, 6).join('')}-${h.slice(6, 8).join('')}-${h.slice(8, 10).join('')}-${h.slice(10).join('')}`;
}
