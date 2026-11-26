// 数据模型与业务规则（纯函数，与 Android 端 :core 的行为保持一致）。
// 记录的 JSON 结构见 core/model/Model.kt：`_t` 为类型判别字段，日期 `YYYY-MM-DD`，日期时间 `YYYY-MM-DDTHH:mm`。

import {
  addMonths, daysInMonth, dayNum, dow, fmtDate, fmtDateTime, fmtTime, mondayOf, parseDate, parseDateTime, todayNum,
  parseTime, uuid, ymd,
} from './util.js';

export const TYPES = {
  semester: 'Semester',
  course: 'Course',
  courseException: 'CourseException',
  event: 'Event',
  taskList: 'TaskList',
  task: 'Task',
};

const toPeriods = (pairs) => pairs.map(([start, end], i) => ({ index: i + 1, start, end }));

export const GDUT_PERIODS = toPeriods([
  ['08:30', '09:15'], ['09:20', '10:05'], ['10:25', '11:10'], ['11:15', '12:00'],
  ['13:50', '14:35'], ['14:40', '15:25'], ['15:30', '16:15'], ['16:30', '17:15'],
  ['17:20', '18:05'], ['18:30', '19:15'], ['19:20', '20:05'], ['20:10', '20:55'],
]);

// ---------- 记录的整理 ----------

/** 所有未删除的记录，按类型分组。`records` 是 Map<id, data>。 */
export function liveView(records) {
  const v = { semesters: [], courses: [], exceptions: [], events: [], tasks: [], lists: [] };
  for (const r of records.values()) {
    if (r.deletedAt != null) continue;
    switch (r._t) {
      case 'semester': v.semesters.push(r); break;
      case 'course': v.courses.push(r); break;
      case 'courseException': v.exceptions.push(r); break;
      case 'event': v.events.push(r); break;
      case 'task': v.tasks.push(r); break;
      case 'taskList': v.lists.push(r); break;
      default: break;
    }
  }
  v.lists.sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0));
  return v;
}

export function currentSemester(view) {
  return view.semesters.find((s) => s.isCurrent) || view.semesters[0] || null;
}

export function toWire(r) {
  return { id: r.id, kind: TYPES[r._t] || r._t, data: r, updatedAt: r.updatedAt, deletedAt: r.deletedAt ?? null };
}

/** 返回修改后的新记录；updatedAt 取「现在」与「旧值 + 1」的较大者，保证后写的一定更新。 */
export function touch(rec, changes, now = Date.now()) {
  return { ...rec, ...changes, updatedAt: Math.max(now, (rec.updatedAt || 0) + 1) };
}

export function tombstone(rec, now = Date.now()) {
  const t = Math.max(now, (rec.updatedAt || 0) + 1);
  return { ...rec, updatedAt: t, deletedAt: t };
}

export function restore(rec, now = Date.now()) {
  return { ...rec, updatedAt: Math.max(now, (rec.updatedAt || 0) + 1), deletedAt: null };
}

// ---------- 学期 / 周次 / 节次 ----------

export function semesterCalendar(sem) {
  const base = mondayOf(parseDate(sem.week1Monday));
  const periods = [...sem.periods].sort((a, b) => a.index - b.index);
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
    isInSemester(day) { const w = this.weekOf(day); return w >= 1 && w <= sem.totalWeeks; },
    period: (i) => byIndex.get(i) || null,
    startMin: (i) => (byIndex.has(i) ? parseTime(byIndex.get(i).start) : null),
    endMin: (i) => (byIndex.has(i) ? parseTime(byIndex.get(i).end) : null),
  };
}

export const WeekSets = {
  all: (total) => range(1, total),
  odd: (total) => range(1, total).filter((w) => w % 2 === 1),
  even: (total) => range(1, total).filter((w) => w % 2 === 0),

  /** `[1,2,3,5,7,8]` → `1-3,5,7,8`（与 Android 端一致：两周连续写成 `7,8`）。 */
  format(weeks) {
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
  },

  /** 解析 `1-16`、`1,3,5-9`、`1-16单`、`2-16双`。无法解析的片段被忽略。 */
  parse(text, total = 30) {
    const out = new Set();
    for (const raw of String(text || '').trim().split(/[,，、\s]+/)) {
      const part = raw.trim().replace(/周$/, '');
      if (!part) continue;
      const odd = part.endsWith('单');
      const even = part.endsWith('双');
      const core = part.replace(/[单双]$/, '');
      const [x, y] = core.split(/[-–~－]/);
      const a = parseInt(x, 10);
      const b = y === undefined ? a : parseInt(y, 10);
      if (Number.isNaN(a) || Number.isNaN(b)) continue;
      const lo = Math.min(a, b);
      const hi = Math.min(Math.max(a, b), Math.max(total, a, b));
      for (let w = lo; w <= hi; w++) {
        if (odd && w % 2 === 0) continue;
        if (even && w % 2 === 1) continue;
        out.add(w);
      }
    }
    return [...out].sort((m, n) => m - n);
  },
};

function range(a, b) {
  const r = [];
  for (let i = a; i <= b; i++) r.push(i);
  return r;
}

// ---------- 课程展开 ----------

const EXAM_NAME_PREFIX_RE = /^(?:(?:期中|期末|随堂|结课|实验|上机)?(?:考试|测验|小测|考核)|(?:期中|期末|随堂|结课)测试|补考|重考|缓考|机考|机试)\s*[:：·\-—]\s*/;
const EXAM_NAME_SUFFIX_RE = /\s*[:：·\-—]?\s*(?:(?:期中|期末|随堂|结课|实验|上机)?(?:考试|测验|小测|考核)|(?:期中|期末|随堂|结课)测试|补考|重考|缓考|机考|机试)$/;

export function normalizeCourseNameForExam(raw) {
  let s = String(raw || '').trim().replace(/（/g, '(').replace(/）/g, ')').replace(/\s+/g, '');
  let prev;
  do {
    prev = s;
    s = s.replace(EXAM_NAME_PREFIX_RE, '').replace(EXAM_NAME_SUFFIX_RE, '').trim();
  } while (s !== prev && s.length > 0);
  return s.toLowerCase();
}

export function isSameCourseForExam(a, b) {
  const na = normalizeCourseNameForExam(a);
  const nb = normalizeCourseNameForExam(b);
  return Boolean(na) && na === nb;
}

function promoteExamOccurrences(out) {
  const exams = out.filter((o) => o.course.kind === 'EXAM');
  if (!exams.length) return out;
  const examRooms = exams.map((e) => e.room || '');
  const suppressed = new Set();
  for (let i = 0; i < out.length; i++) {
    const o = out[i];
    if (o.course.kind === 'EXAM') continue;
    const examIdx = exams.findIndex((e) => e.day === o.day
      && isSameCourseForExam(e.course.name, o.course.name)
      && ((o.startMin < e.endMin && e.startMin < o.endMin) || (o.startPeriod <= e.endPeriod && e.startPeriod <= o.endPeriod)));
    if (examIdx >= 0) {
      suppressed.add(i);
      if (!examRooms[examIdx] && o.room) examRooms[examIdx] = o.room;
    }
  }
  if (!suppressed.size) return out;
  let nextExam = 0;
  const res = [];
  for (let i = 0; i < out.length; i++) {
    if (suppressed.has(i)) continue;
    const o = out[i];
    if (o.course.kind === 'EXAM') {
      const room = examRooms[nextExam++];
      res.push(room !== (o.room || '') ? { ...o, room } : o);
    } else {
      res.push(o);
    }
  }
  return res;
}

/** 展开 [from, to]（天数，含）内所有课程出现，按开始时间排序。 */
export function expandCourses(sem, courses, exceptions, from, to) {
  const cal = semesterCalendar(sem);
  const exByKey = new Map();
  for (const e of exceptions) {
    if (e.deletedAt != null) continue;
    exByKey.set(`${e.courseId}|${e.week}`, e); // 后者覆盖前者
  }
  const out = [];
  for (const c of courses) {
    if (c.deletedAt != null || c.semesterId !== sem.id) continue;
    for (const w of [...c.weeks].sort((a, b) => a - b)) {
      const ex = exByKey.get(`${c.id}|${w}`);
      if (ex && ex.type === 'CANCEL') continue;
      const re = ex && ex.type === 'RESCHEDULE' ? ex : null;
      const week = re?.newWeek ?? w;
      const d = re?.newDayOfWeek ?? c.dayOfWeek;
      const sp = re?.newStartPeriod ?? c.startPeriod;
      const ep = re?.newEndPeriod ?? c.endPeriod;
      const day = cal.dateOf(week, d);
      if (day < from || day > to) continue;
      const useCustomClock = !re || (re.newStartPeriod == null && re.newEndPeriod == null);
      const cs = useCustomClock && c.startTime ? parseTime(c.startTime) : NaN;
      const ce = useCustomClock && c.endTime ? parseTime(c.endTime) : NaN;
      const s = !Number.isNaN(cs) ? cs : cal.startMin(sp);
      const e = !Number.isNaN(ce) ? ce : cal.endMin(ep);
      if (s == null || e == null) continue;
      out.push({
        kind: 'course', course: c, week, origWeek: w, day, startPeriod: sp, endPeriod: ep,
        startMin: s, endMin: e, room: re?.newRoom ?? c.room, rescheduled: !!re,
      });
    }
  }
  return promoteExamOccurrences(out).sort((a, b) => a.day - b.day || a.startMin - b.startMin || a.course.name.localeCompare(b.course.name));
}

/** 两门课（含各自周次）是否在时间上冲突，返回冲突的课程列表。 */
export function courseConflicts(candidate, courses) {
  const wk = new Set(candidate.weeks);
  const candExam = candidate.kind === 'EXAM';
  return courses.filter((o) => o.id !== candidate.id && o.deletedAt == null && o.semesterId === candidate.semesterId
    && o.dayOfWeek === candidate.dayOfWeek
    && o.startPeriod <= candidate.endPeriod && candidate.startPeriod <= o.endPeriod
    && o.weeks.some((w) => wk.has(w))
    && !(candExam !== (o.kind === 'EXAM') && isSameCourseForExam(candidate.name, o.name)));
}

// ---------- 重复规则 ----------

const MAX_ITER = 6000;

/** 以 anchor 为首次出现日期，规则在 [from, to] 内的所有日期（天数）。与 RecurrenceEngine.dates 同义。 */
export function recurrenceDates(rule, anchor, from, to) {
  const interval = Math.max(1, rule.interval || 1);
  const exd = new Set((rule.exDates || []).map(parseDate));
  const until = rule.until ? parseDate(rule.until) : null;
  const count = rule.count ?? null;
  const limit = until != null ? Math.min(until, to) : to;
  const res = [];
  let produced = 0;
  // 返回 false 表示应停止
  const accept = (d) => {
    if (d < anchor) return true;
    if (until != null && d > until) return false;
    if (count != null && produced >= count) return false;
    produced++;
    if (!exd.has(d) && d >= from && d <= to) res.push(d);
    return d <= limit;
  };
  const a = ymd(anchor);
  let iter = 0;
  if (rule.freq === 'DAILY') {
    for (let k = 0; iter++ < MAX_ITER; k++) {
      if (!accept(anchor + k * interval)) break;
    }
  } else if (rule.freq === 'WEEKLY') {
    const days = [...(rule.byDays && rule.byDays.length ? rule.byDays : [dow(anchor)])].sort((x, y) => x - y);
    const firstWeek = mondayOf(anchor);
    outer: for (let k = 0; iter++ < MAX_ITER; k++) {
      const ws = firstWeek + 7 * k * interval;
      for (const d of days) {
        const day = ws + d - 1;
        if (day < anchor) continue;
        if (!accept(day)) break outer;
      }
    }
  } else if (rule.freq === 'MONTHLY') {
    const days = [...(rule.byDays && rule.byDays.length ? rule.byDays : [a.d])].sort((x, y) => x - y);
    outer: for (let k = 0; iter++ < MAX_ITER; k++) {
      const { y, m } = addMonths(a.y, a.m, k * interval);
      if (dayNum(y, m, 1) > limit) break;
      const last = daysInMonth(y, m);
      for (const d of days) {
        if (d > last) continue;
        if (!accept(dayNum(y, m, d))) break outer;
      }
    }
  } else if (rule.freq === 'YEARLY') {
    const months = [...(rule.byMonths && rule.byMonths.length ? rule.byMonths : [a.m])].sort((x, y) => x - y);
    const days = [...(rule.byDays && rule.byDays.length ? rule.byDays : [a.d])].sort((x, y) => x - y);
    outer: for (let k = 0; iter++ < MAX_ITER; k++) {
      const y = a.y + k * interval;
      for (const m of months) {
        if (dayNum(y, m, 1) > limit) break;
        const last = daysInMonth(y, m);
        for (const d of days) {
          if (d > last) continue;
          if (!accept(dayNum(y, m, d))) break outer;
        }
      }
    }
  }
  return res;
}

/** 日程在 [from, to]（天数，含）内的所有出现。 */
export function expandEvent(ev, from, to) {
  if (ev.deletedAt != null) return [];
  const s = parseDateTime(ev.start);
  const e = parseDateTime(ev.end);
  if (!s || !e) return [];
  const startAbs = s.day * 1440 + s.min;
  const endAbs = e.day * 1440 + e.min;
  const durMin = Math.max(0, endAbs - startAbs);
  const rule = ev.recurrence;
  let starts;
  if (!rule) starts = [s.day];
  else {
    const back = Math.floor(durMin / 1440) + 1;
    starts = recurrenceDates(rule, s.day, from - back, to);
  }
  const out = [];
  for (const day of starts) {
    const abs = day * 1440 + s.min;
    const endTotal = abs + durMin;
    const endDay = Math.floor(endTotal / 1440);
    const endMin = endTotal - endDay * 1440;
    if (endDay < from || day > to) continue;
    // 结束于 00:00 的非全天事件不占用次日
    if (endDay === from && endMin === 0 && day !== from && !ev.allDay) continue;
    out.push({ kind: 'event', event: ev, day, startMin: s.min, endDay, endMin, allDay: !!ev.allDay });
  }
  return out;
}

export function expandEvents(events, from, to) {
  return events
    .flatMap((e) => expandEvent(e, from, to))
    .sort((a, b) => a.day - b.day || (a.allDay ? -1 : 0) - (b.allDay ? -1 : 0) || a.startMin - b.startMin
      || a.event.title.localeCompare(b.event.title));
}

// ---------- 待办 ----------

export function dueOf(t) {
  return t.due ? parseDateTime(t.due) : null;
}

export function isOverdue(t, nowDay, nowMin) {
  if (t.done) return false;
  const d = dueOf(t);
  if (!d) return false;
  return t.dueHasTime ? d.day < nowDay || (d.day === nowDay && d.min < nowMin) : d.day < nowDay;
}

export function topLevelTasks(tasks) {
  return tasks.filter((t) => t.deletedAt == null && !t.parentId);
}

export function subtasks(tasks, parentId) {
  return tasks.filter((t) => t.deletedAt == null && t.parentId === parentId)
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
}

const dueKey = (t) => {
  const d = dueOf(t);
  return d ? d.day * 1440 + d.min : Infinity;
};

/** which: today | planned | all | done | overdue */
export function filterTasks(tasks, which, nowDay, nowMin) {
  const top = topLevelTasks(tasks);
  const dueDay = (t) => dueOf(t)?.day ?? null;
  let list;
  switch (which) {
    case 'today':
      list = top.filter((t) => !t.done && (isOverdue(t, nowDay, nowMin) || dueDay(t) === nowDay
        || (t.startDate && parseDate(t.startDate) === nowDay)
        || (t.blockStart && parseDateTime(t.blockStart)?.day === nowDay)));
      break;
    case 'planned':
      list = top.filter((t) => !t.done && dueDay(t) != null && dueDay(t) > nowDay && dueDay(t) <= nowDay + 7);
      break;
    case 'done': list = top.filter((t) => t.done); break;
    case 'overdue': list = top.filter((t) => isOverdue(t, nowDay, nowMin)); break;
    default: list = top.filter((t) => !t.done);
  }
  return list.sort((a, b) => Number(a.done) - Number(b.done) || dueKey(a) - dueKey(b)
    || (b.priority || 0) - (a.priority || 0) || (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
}

/** 完成任务。重复任务：本次标记完成，同时生成下一次实例。返回 {finished, next}。 */
export function completeTask(task, now = Date.now(), today = 0) {
  const finished = touch(task, { done: true, doneAt: now }, now);
  const rule = task.repeat;
  if (!rule) return { finished, next: null };
  const due = dueOf(task);
  const anchor = due ? due.day : task.startDate ? parseDate(task.startDate) : today;
  const after = Math.max(anchor, today);
  const nextDate = recurrenceDates({ ...rule, count: null }, anchor, after + 1, after + 366 * 8)[0];
  if (nextDate === undefined) return { finished, next: null };
  const shift = nextDate - anchor;
  const left = rule.count != null ? rule.count - 1 : null;
  const next = {
    ...task,
    id: uuid(),
    done: false,
    doneAt: null,
    due: due ? fmtDateTime(due.day + shift, due.min) : null,
    startDate: task.startDate ? fmtDate(parseDate(task.startDate) + shift) : null,
    blockStart: null,
    updatedAt: Math.max(now, finished.updatedAt),
    deletedAt: null,
    repeat: left == null || left > 0 ? { ...rule, count: left } : null,
  };
  return { finished, next };
}

export function reopenTask(task, now = Date.now()) {
  return touch(task, { done: false, doneAt: null }, now);
}

// ---------- 某一天的日程汇总 ----------

/**
 * 某一天的条目（课程 / 日程 / 待办 / 时间块），用于「今日」与「日历」。
 * 返回 {allDay: [], timed: [], tasks: []}。
 */
export function agendaFor(view, day) {
  const sem = currentSemester(view);
  const courses = sem ? expandCourses(sem, view.courses, view.exceptions, day, day) : [];
  const events = expandEvents(view.events, day, day);
  const allDay = events.filter((e) => e.allDay).map((e) => ({ ...e, sortMin: -1 }));
  const timed = [
    ...courses.map((c) => ({ ...c, sortMin: c.startMin })),
    ...events.filter((e) => !e.allDay).map((e) => ({ ...e, sortMin: e.day === day ? e.startMin : 0 })),
  ];
  const tasks = [];
  for (const t of topLevelTasks(view.tasks)) {
    if (t.blockStart) {
      const b = parseDateTime(t.blockStart);
      if (b && b.day === day) {
        timed.push({ kind: 'block', task: t, day, startMin: b.min, endMin: b.min + (t.blockMinutes ?? t.estimateMinutes ?? 60), sortMin: b.min });
        continue;
      }
    }
    const d = dueOf(t);
    if (d && d.day === day) tasks.push(t);
  }
  timed.sort((a, b) => a.sortMin - b.sortMin);
  return { allDay, timed, tasks };
}

/** 某个月（y, m）中每天是否有内容，用于日历上的圆点。返回 Map<day, {course, event, task}>。 */
export function monthMarks(view, y, m) {
  const first = dayNum(y, m, 1);
  const last = dayNum(y, m, daysInMonth(y, m));
  const marks = new Map();
  const mark = (day, k) => {
    if (day < first || day > last) return;
    const o = marks.get(day) || {};
    o[k] = true;
    marks.set(day, o);
  };
  const sem = currentSemester(view);
  if (sem) for (const c of expandCourses(sem, view.courses, view.exceptions, first, last)) mark(c.day, 'course');
  for (const e of expandEvents(view.events, first, last)) {
    const end = Math.min(e.endDay, last);
    for (let d = Math.max(e.day, first); d <= end; d++) mark(d, 'event');
  }
  for (const t of topLevelTasks(view.tasks)) {
    const d = dueOf(t);
    if (d && !t.done) mark(d.day, 'task');
  }
  return marks;
}

// ---------- 工厂：构造符合 Android 端序列化格式的新记录 ----------

const base = (type, now) => ({ _t: type, id: uuid(), updatedAt: now, deletedAt: null, userId: null });

export function newSemester(f = {}, now = Date.now()) {
  return {
    ...base('semester', now),
    name: f.name || '新学期',
    week1Monday: f.week1Monday || fmtDate(mondayOf(todayNum())),
    totalWeeks: f.totalWeeks ?? 20,
    periods: f.periods || GDUT_PERIODS.map((p) => ({ ...p })),
    isCurrent: f.isCurrent ?? true,
  };
}

export function newCourse(f, now = Date.now()) {
  return {
    ...base('course', now),
    semesterId: f.semesterId,
    name: f.name,
    teacher: f.teacher || '',
    room: f.room || '',
    dayOfWeek: f.dayOfWeek,
    startPeriod: f.startPeriod,
    endPeriod: f.endPeriod,
    weeks: [...f.weeks].sort((a, b) => a - b),
    colorIndex: f.colorIndex ?? 0,
    remindMinutes: f.remindMinutes ?? null,
    note: f.note || '',
    source: 'MANUAL',
    kind: f.kind || 'NORMAL',
    startTime: f.startTime ?? null,
    endTime: f.endTime ?? null,
  };
}

export function newException(f, now = Date.now()) {
  return {
    ...base('courseException', now),
    courseId: f.courseId,
    week: f.week,
    type: f.type,
    newWeek: f.newWeek ?? null,
    newDayOfWeek: f.newDayOfWeek ?? null,
    newStartPeriod: f.newStartPeriod ?? null,
    newEndPeriod: f.newEndPeriod ?? null,
    newRoom: f.newRoom ?? null,
  };
}

export function newRecurrence(freq, f = {}) {
  return { freq, interval: Math.max(1, f.interval || 1), byDays: f.byDays || [], byMonths: f.byMonths || [], until: f.until || null, count: f.count ?? null, exDates: f.exDates || [] };
}

export function newEvent(f, now = Date.now()) {
  return {
    ...base('event', now),
    title: f.title,
    start: f.start,
    end: f.end,
    allDay: !!f.allDay,
    location: f.location || '',
    note: f.note || '',
    colorIndex: f.colorIndex ?? 4,
    category: f.category || '',
    remindMinutes: f.remindMinutes ?? null,
    recurrence: f.recurrence || null,
    kind: f.kind || 'NORMAL',
    source: 'MANUAL',
  };
}

export function newList(f, now = Date.now()) {
  return { ...base('taskList', now), name: f.name, colorIndex: f.colorIndex ?? 1, sort: f.sort ?? 0 };
}

export function newTask(f, now = Date.now()) {
  return {
    ...base('task', now),
    title: f.title,
    note: f.note || '',
    listId: f.listId || null,
    tags: f.tags || [],
    priority: f.priority || 0,
    startDate: f.startDate || null,
    due: f.due || null,
    dueHasTime: !!f.dueHasTime,
    estimateMinutes: f.estimateMinutes ?? null,
    done: false,
    doneAt: null,
    parentId: f.parentId || null,
    repeat: f.repeat || null,
    remindMinutes: f.remindMinutes ?? null,
    courseId: f.courseId || null,
    dueBeforeNextClass: false,
    blockStart: null,
    blockMinutes: null,
    sortOrder: f.sortOrder ?? 0,
    source: 'MANUAL',
    extra: {},
  };
}

export { fmtTime };
