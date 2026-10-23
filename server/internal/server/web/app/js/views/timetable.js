// 课表：按周的网格，点击课程查看详情，点击空白格添加课程。

import { ctx } from '../ctx.js';
import { h, icon } from '../dom.js';
import { openCourseDetail, openCourseForm, openSemesterForm } from '../forms.js';
import { currentSemester, expandCourses, semesterCalendar } from '../model.js';
import { WEEKDAYS, dow, todayNum, ymd } from '../util.js';
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
      return emptyState('grid', '还没有学期。创建学期后就可以添加课程了',
        h('button', { class: 'btn primary', type: 'button', onclick: () => openSemesterForm({ create: true }) }, '创建学期'));
    }
    const { sem, cal, today, week } = s;
    const view = store.view();
    const curWeek = cal.weekOf(today);
    const occs = expandCourses(sem, view.courses, view.exceptions, cal.weekStart(week), cal.weekEnd(week));
    assignLanes(occs);

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
    const canHover = matchMedia('(hover: hover)').matches;
    cal.periods.forEach((p, i) => {
      grid.append(h('div', { class: 'tt-time', style: { 'grid-row': i + 2, 'grid-column': 1 } }, h('b', {}, p.index), h('span', {}, p.start)));
      for (let d = 1; d <= 7; d++) {
        const day = cal.dateOf(week, d);
        grid.append(h('div', {
          class: `tt-cell${day === today ? ' today' : ''}${canHover ? ' addable' : ''}${i > 0 && (p.index === 5 || p.index === 10) ? ' gap' : ''}`,
          style: { 'grid-row': i + 2, 'grid-column': d + 1 },
          title: canHover ? '添加课程' : null,
          onclick: canHover ? () => openCourseForm({ day: d, period: p.index }) : null,
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
        class: `tt-block c${c.colorIndex % 8}${o.rescheduled ? ' moved' : ''}${span === 1 ? ' short' : ''}`,
        style: { 'grid-row': `${r1 + 2} / ${r2 + 3}`, 'grid-column': dow(o.day) + 1, '--lane': o.lane, '--lanes': o.lanes },
        'aria-label': `${c.name}，${WEEKDAYS[dow(o.day) - 1]}第${o.startPeriod}到${o.endPeriod}节${o.room ? `，${o.room}` : ''}`,
        onclick: () => openCourseDetail(o),
      }, h('b', {}, c.name), o.room ? h('span', {}, o.room) : null, !o.room && c.teacher ? h('span', {}, c.teacher) : null));
    }

    const wrap = h('div', { class: 'tt-wrap' }, grid);
    let sx = 0; let sy = 0;
    wrap.addEventListener('touchstart', (e) => { sx = e.touches[0].clientX; sy = e.touches[0].clientY; }, { passive: true });
    wrap.addEventListener('touchend', (e) => {
      const dx = e.changedTouches[0].clientX - sx;
      const dy = e.changedTouches[0].clientY - sy;
      if (Math.abs(dx) > 70 && Math.abs(dy) < 50) go(dx < 0 ? 1 : -1);
    }, { passive: true });
    const note = occs.length ? null : h('p', { class: 'tt-empty' }, '这一周没有课。点网格里的空白处，或右上角「添加」来加课程。');
    return h('div', { class: 'tt' }, bar, wrap, note);
  },
};
