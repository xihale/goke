// 课表：按周的网格，点击课程查看详情，点击空白格添加课程。

import { ctx } from '../ctx.js';
import { h, icon } from '../dom.js';
import { openCourseDetail, openCourseForm, openSemesterForm, openTaskForm } from '../forms.js';
import {
  blockRows, currentSemester, expandCourses, extractExamSeat, isExamTask,
  semesterCalendar, splitExamDisplay, tokenizeVerticalTitle,
} from '../model.js';
import { WEEKDAYS, dow, fmtTime, parseDateTime, todayNum, ymd } from '../util.js';
import { emptyState } from './shared.js';

let viewWeek = null;

function state() {
  const sem = currentSemester(ctx.store.view());
  if (!sem) return null;
  const cal = semesterCalendar(sem);
  const today = todayNum();
  const week = Math.min(Math.max(viewWeek ?? cal.currentWeek(today), 1), sem.totalWeeks);
  return { sem, cal, today, week };
}

function go(delta) {
  const s = state();
  if (!s) return;
  viewWeek = Math.min(Math.max(s.week + delta, 1), s.sem.totalWeeks);
  ctx.rerender();
}

const md = (day) => { const { m, d } = ymd(day); return `${m}/${d}`; };

/** 同一天内相互重叠的课程分成并排的「车道」。 */
function assignLanes(occs) {
  const byDay = new Map();
  for (const o of occs) {
    const k = o.day;
    if (!byDay.has(k)) byDay.set(k, []);
    byDay.get(k).push(o);
  }
  for (const list of byDay.values()) {
    list.sort((a, b) => a.startMin - b.startMin || a.endMin - b.endMin);
    let cluster = [];
    let clusterEnd = -1;
    const flush = () => { const lanes = Math.max(1, ...cluster.map((c) => c.lane + 1)); cluster.forEach((c) => { c.lanes = lanes; }); cluster = []; };
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

export const timetableView = {
  id: 'timetable',
  head() {
    const s = state();
    if (!s) return { eyebrow: '', title: '课表' };
    return { eyebrow: `${md(s.cal.weekStart(s.week))} – ${md(s.cal.weekEnd(s.week))}`, title: `${s.sem.name} · 第 ${s.week} 周` };
  },
  add: () => openCourseForm(),
  onKey(e) {
    if (e.key === 'ArrowLeft') { go(-1); return true; }
    if (e.key === 'ArrowRight') { go(1); return true; }
    return false;
  },
  render() {
    const s = state();
    const { store } = ctx;
    if (!s) {
      return emptyState('grid', '还没有学期',
        h('button', { class: 'btn primary', type: 'button', onclick: () => openSemesterForm({ create: true }) }, '创建学期'));
    }
    const { sem, cal, today, week } = s;
    const view = store.view();
    const curWeek = cal.weekOf(today);
    const wStart = cal.weekStart(week);
    const wEnd = cal.weekEnd(week);
    const occs = expandCourses(sem, view.courses, view.exceptions, wStart, wEnd);
    const listColors = new Map(view.lists.map((l) => [l.id, l.colorIndex ?? 1]));
    const taskItems = [];
    for (const t of view.tasks) {
      if (t.deletedAt != null || !t.blockStart) continue;
      if (t.extra?.code || t.extra?.pickupCode) continue;
      const dt = parseDateTime(t.blockStart);
      if (!dt || Number.isNaN(dt.day) || dt.day < wStart || dt.day > wEnd) continue;
      const dur = t.blockMinutes || 60;
      if (dur > 180) continue;
      const endMin = dt.min + dur;
      const rows = blockRows(cal.periods, dt.min, endMin);
      if (!rows) continue;
      taskItems.push({
        kind: 'task',
        task: t,
        day: dt.day,
        startMin: dt.min,
        endMin,
        startPeriod: rows.startPeriod,
        endPeriod: rows.endPeriod,
        colorIndex: isExamTask(t) ? 2 : (listColors.get(t.listId) ?? 1),
      });
    }
    const gridItems = [
      ...occs.map((o) => ({ ...o, kind: 'course' })),
      ...taskItems,
    ];
    assignLanes(gridItems);

    const bar = h('div', { class: 'week-bar' },
      h('button', { type: 'button', class: 'icon-btn tonal', 'aria-label': '上一周', disabled: week <= 1, onclick: () => go(-1) }, icon('left')),
      h('select', {
        class: 'week-select', 'aria-label': '选择周次', onchange: (e) => { viewWeek = Number(e.target.value); ctx.rerender(); },
      }, Array.from({ length: sem.totalWeeks }, (_, i) => h('option', { value: i + 1, selected: i + 1 === week }, `第 ${i + 1} 周${i + 1 === curWeek ? '（本周）' : ''}`))),
      h('button', { type: 'button', class: 'icon-btn tonal', 'aria-label': '下一周', disabled: week >= sem.totalWeeks, onclick: () => go(1) }, icon('right')),
      week !== cal.currentWeek(today)
        ? h('button', { type: 'button', class: 'btn tonal small', onclick: () => { viewWeek = null; ctx.rerender(); } }, '回到本周') : null);

    const pos = new Map(cal.periods.map((p, i) => [p.index, i]));
    const grid = h('div', { class: 'tt-grid', style: { '--rows': cal.periods.length } });
    grid.append(h('div', { class: 'tt-corner' }));
    for (let d = 1; d <= 7; d++) {
      const day = cal.dateOf(week, d);
      grid.append(h('div', { class: `tt-head${day === today ? ' today' : ''}`, style: { 'grid-column': d + 1, 'grid-row': 1 } },
        h('b', {}, WEEKDAYS[d - 1]), h('span', {}, md(day))));
    }
    let touchMoved = false;
    cal.periods.forEach((p, i) => {
      grid.append(h('div', { class: 'tt-time', style: { 'grid-row': i + 2, 'grid-column': 1 } }, h('b', {}, p.index), h('span', {}, p.start)));
      for (let d = 1; d <= 7; d++) {
        const day = cal.dateOf(week, d);
        grid.append(h('div', {
          class: `tt-cell${day === today ? ' today' : ''} addable${i > 0 && (p.index === 5 || p.index === 10) ? ' gap' : ''}`,
          style: { 'grid-row': i + 2, 'grid-column': d + 1 },
          title: '添加课程',
          onclick: () => { if (!touchMoved) openCourseForm({ day: d, period: p.index }); },
        }));
      }
    });
    const titleSizeClass = (text) => {
      const len = [...String(text || '').trim()].length;
      if (len >= 9) return 'tt-title-xs';
      if (len >= 6) return 'tt-title-sm';
      return '';
    };
    const compactLead = (text) => String(text || '').replace(/^([\u4e00-\u9fffA-Za-z0-9])\s+/, '$1\u202F');
    const roomSizeClass = (text) => {
      const len = [...String(text || '').trim()].length;
      if (len === 0 || len > 11) return '';
      return len >= 7 ? 'tt-room tt-room-xs' : 'tt-room';
    };
    for (const o of gridItems) {
      const r1 = pos.get(o.startPeriod);
      const r2 = pos.get(o.endPeriod);
      if (r1 == null || r2 == null) continue;
      const span = o.endPeriod - o.startPeriod + 1;
      if (o.kind === 'task') {
        const t = o.task;
        const exam = isExamTask(t);
        const display = exam ? splitExamDisplay(t.title) : null;
        const loc = (t.extra?.location || '').trim();
        const timeStr = fmtTime(o.startMin);
        const title = display ? display.title : t.title;
        grid.append(h('button', {
          type: 'button',
          class: `tt-block task-blk${exam ? ' exam' : ''}${span === 1 ? ' short' : ''} c${o.colorIndex % 8}${t.done ? ' past' : ''}`,
          style: { 'grid-row': `${r1 + 2} / ${r2 + 3}`, 'grid-column': dow(o.day) + 1, '--lane': o.lane, '--lanes': o.lanes },
          'aria-label': `${display ? `${display.badgeFull}：${display.title}` : t.title}，${WEEKDAYS[dow(o.day) - 1]} ${timeStr}${loc ? `，${loc}` : ''}`,
          onclick: () => openTaskForm({ task: t }),
        },
        display ? h('span', { class: 'tt-exam-pill' }, display.badgeShort) : null,
        h('b', { class: titleSizeClass(title) }, compactLead(title)),
        loc && span >= 2 ? h('span', { class: roomSizeClass(loc) }, compactLead(loc)) : null,
        h('span', {}, span === 1 ? (compactLead(loc) || timeStr) : timeStr)));
        continue;
      }
      const c = o.course;
      const isExam = c.kind === 'EXAM';
      const display = isExam ? splitExamDisplay(c.name) : null;
      const seat = isExam ? extractExamSeat(c.note) : '';
      const seatText = seat ? `${seat}座` : '';
      const title = display ? display.title : c.name;
      grid.append(h('button', {
        type: 'button',
        class: `tt-block${isExam ? ' exam' : ''}${span === 1 ? ' short' : ''} c${c.colorIndex % 8}${o.rescheduled ? ' moved' : ''}`,
        style: { 'grid-row': `${r1 + 2} / ${r2 + 3}`, 'grid-column': dow(o.day) + 1, '--lane': o.lane, '--lanes': o.lanes },
        'aria-label': `${display ? `${display.badgeFull}：${display.title}` : c.name}，${WEEKDAYS[dow(o.day) - 1]}第${o.startPeriod}到${o.endPeriod}节${o.room ? `，${o.room}` : ''}`,
        onclick: () => openCourseDetail(o),
      },
      display ? h('span', { class: 'tt-exam-pill' }, display.badgeShort) : null,
      h('b', { class: titleSizeClass(title) }, compactLead(title)),
      o.room ? h('span', { class: roomSizeClass(o.room) }, compactLead(o.room)) : null,
      seatText && span >= 2 ? h('span', {}, seatText) : null,
      !o.room && !seatText && c.teacher ? h('span', {}, c.teacher) : null));
    }

    const wrap = h('div', { class: 'tt-wrap' }, grid);
    let sx = 0; let sy = 0;
    wrap.addEventListener('touchstart', (e) => {
      const t = e.touches[0];
      if (!t) return;
      sx = t.clientX; sy = t.clientY; touchMoved = false;
    }, { passive: true });
    wrap.addEventListener('touchmove', (e) => {
      const t = e.touches[0];
      if (!t) return;
      if (Math.abs(t.clientX - sx) > 10 || Math.abs(t.clientY - sy) > 10) touchMoved = true;
    }, { passive: true });
    wrap.addEventListener('touchend', (e) => {
      const t = e.changedTouches[0];
      if (!t) return;
      const dx = t.clientX - sx;
      const dy = t.clientY - sy;
      if (Math.abs(dx) > 70 && Math.abs(dy) < 50) go(dx < 0 ? 1 : -1);
    }, { passive: true });
    const note = gridItems.length ? null : h('p', { class: 'tt-empty' }, '这一周没有课');
    return h('div', { class: 'tt' }, bar, wrap, note);
  },
};
