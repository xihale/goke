// 日历：月视图（圆点标记当天有内容）+ 选中日期的日程列表。

import { ctx } from '../ctx.js';
import { h, icon } from '../dom.js';
import { openAddMenu } from '../forms.js';
import { agendaFor, currentSemester, monthMarks, semesterCalendar } from '../model.js';
import { WEEKDAYS, addMonths, dayNum, daysInMonth, dow, fmtTime, mondayOf, minutesOfDay, todayNum, ymd, weekdayName } from '../util.js';
import { blockCard, courseCard, emptyState, eventCard, taskRow } from './shared.js';

let cur = null;
let sel = null;

function init() {
  if (cur) return;
  const t = ymd(todayNum());
  cur = { y: t.y, m: t.m };
  sel = todayNum();
}

function shift(k) {
  init();
  cur = addMonths(cur.y, cur.m, k);
  // 选中日期跟着月份走（保持日号，超出则取月末）
  const s = ymd(sel);
  sel = dayNum(cur.y, cur.m, Math.min(s.d, daysInMonth(cur.y, cur.m)));
  ctx.rerender();
}

function goToday() {
  const t = ymd(todayNum());
  cur = { y: t.y, m: t.m };
  sel = todayNum();
  ctx.rerender();
}

function agendaPanel(day) {
  const { store } = ctx;
  const view = store.view();
  const now = minutesOfDay();
  const today = todayNum();
  const ag = agendaFor(view, day);
  const { m, d } = ymd(day);
  const panel = h('section', { class: 'cal-agenda' },
    h('div', { class: 'cal-agenda-head' },
      h('h3', {}, `${m} 月 ${d} 日 · ${weekdayName(day)}`, day === today ? h('span', { class: 'tag' }, '今天') : null),
      h('button', { type: 'button', class: 'btn tonal small', onclick: () => openAddMenu({ date: day }) }, icon('plus', 16), '添加')));
  const nothing = !ag.allDay.length && !ag.timed.length && !ag.tasks.length;
  if (nothing) {
    panel.append(emptyState('coffee', '这一天没有安排'));
    return panel;
  }
  if (ag.allDay.length) panel.append(h('div', { class: 'allday' }, ag.allDay.map((e) => eventCard(e, { day }))));
  const isPast = (x) => day < today || (day === today && x.endMin <= now);
  for (const x of ag.timed) {
    const card = x.kind === 'course' ? courseCard(x, { past: x.kind === 'course' && isPast(x) })
      : x.kind === 'event' ? eventCard(x, { day }) : blockCard(x);
    panel.append(h('div', { class: 'tl-row' }, h('div', { class: 'tl-time' }, h('b', {}, fmtTime(x.startMin)), h('span', {}, x.kind === 'event' && x.endDay > day ? '' : fmtTime(x.endMin))), card));
  }
  if (ag.tasks.length) {
    panel.append(h('div', { class: 'stack', role: 'list' }, ag.tasks.map((t) => taskRow(t, { today, nowMin: now, listName: view.lists.find((l) => l.id === t.listId)?.name }))));
  }
  return panel;
}

export const calendarView = {
  id: 'calendar',
  head() {
    init();
    return { eyebrow: '', title: `${cur.y} 年 ${cur.m} 月` };
  },
  add: () => { init(); openAddMenu({ date: sel }); },
  onKey(e) {
    if (e.key === 'ArrowLeft') { shift(-1); return true; }
    if (e.key === 'ArrowRight') { shift(1); return true; }
    return false;
  },
  render() {
    init();
    const { store } = ctx;
    const view = store.view();
    const today = todayNum();
    const first = dayNum(cur.y, cur.m, 1);
    const weeks = Math.ceil((dow(first) - 1 + daysInMonth(cur.y, cur.m)) / 7);
    const start = mondayOf(first);
    const marks = monthMarks(view, cur.y, cur.m);
    const sem = currentSemester(view);
    const cal = sem ? semesterCalendar(sem) : null;

    const bar = h('div', { class: 'week-bar' },
      h('button', { type: 'button', class: 'icon-btn tonal', 'aria-label': '上个月', onclick: () => shift(-1) }, icon('left')),
      h('button', { type: 'button', class: 'icon-btn tonal', 'aria-label': '下个月', onclick: () => shift(1) }, icon('right')),
      h('button', { type: 'button', class: 'btn tonal small', onclick: goToday }, '今天'));

    const grid = h('div', { class: 'cal-grid', role: 'grid' });
    grid.append(h('div', { class: 'cal-wk head' }, '周'));
    WEEKDAYS.forEach((n) => grid.append(h('div', { class: 'cal-dow' }, n)));
    for (let w = 0; w < weeks; w++) {
      const rowStart = start + w * 7;
      const wk = cal ? cal.weekOf(rowStart) : 0;
      grid.append(h('div', { class: 'cal-wk' }, cal && wk >= 1 && wk <= sem.totalWeeks ? wk : ''));
      for (let d = 0; d < 7; d++) {
        const day = rowStart + d;
        const { y, m, d: dd } = ymd(day);
        const mk = marks.get(day) || {};
        grid.append(h('button', {
          type: 'button',
          class: `cal-day${m !== cur.m || y !== cur.y ? ' other' : ''}${day === today ? ' today' : ''}${day === sel ? ' selected' : ''}`,
          'aria-label': `${m}月${dd}日`,
          'aria-pressed': String(day === sel),
          onclick: () => { sel = day; if (m !== cur.m || y !== cur.y) cur = { y, m }; ctx.rerender(); },
        }, h('span', { class: 'cal-num' }, dd),
        h('span', { class: 'dots' }, mk.course ? h('i', { class: 'dot dot-course' }) : null, mk.event ? h('i', { class: 'dot dot-event' }) : null, mk.task ? h('i', { class: 'dot dot-task' }) : null)));
      }
    }
    return h('div', { class: 'cal-layout' }, h('div', { class: 'cal-main' }, bar, grid), agendaPanel(sel));
  },
};
