// 今日：时间线 + 今日待办 + 进度摘要 + 明天预览。

import { ctx } from '../ctx.js';
import { h, icon, ring } from '../dom.js';
import { openAddMenu, openSemesterForm } from '../forms.js';
import { agendaFor, currentSemester, filterTasks, semesterCalendar } from '../model.js';
import { fmtTime, minutesOfDay, todayNum, weekdayName, ymd } from '../util.js';
import { blockCard, courseCard, emptyState, eventCard, taskRow } from './shared.js';

export const todayView = {
  id: 'today',
  head() {
    const day = todayNum();
    const sem = currentSemester(ctx.store.view());
    let eyebrow = weekdayName(day);
    if (sem) {
      const w = semesterCalendar(sem).weekOf(day);
      if (w >= 1 && w <= sem.totalWeeks) eyebrow += ` · 第 ${w} 周`;
    }
    const { m, d } = ymd(day);
    return { eyebrow, title: `${m}月${d}日` };
  },
  add: () => openAddMenu({ date: todayNum() }),
  render() {
    const { store } = ctx;
    const view = store.view();
    const day = todayNum();
    const now = minutesOfDay();
    const ag = agendaFor(view, day);
    const sem = currentSemester(view);

    const blockIds = new Set(ag.timed.filter((x) => x.kind === 'block').map((x) => x.task.id));
    const taskMap = new Map();
    for (const t of [...filterTasks(view.tasks, 'today', day, now), ...ag.tasks]) if (!blockIds.has(t.id)) taskMap.set(t.id, t);
    const tasks = [...taskMap.values()].sort((a, b) => Number(a.done) - Number(b.done));
    const listName = (t) => view.lists.find((l) => l.id === t.listId)?.name;

    const isPast = (x) => (x.kind === 'course' ? x.endMin <= now : x.kind === 'event' ? !x.allDay && x.endDay === day && x.endMin <= now : false);
    const courses = ag.timed.filter((x) => x.kind === 'course');
    const total = ag.timed.length + tasks.length;
    const done = ag.timed.filter(isPast).length + tasks.filter((t) => t.done).length;
    const nextUp = ag.timed.find((x) => x.startMin > now && x.kind !== 'block');

    const summary = h('section', { class: 'card summary' },
      ring(done, total, 76),
      h('div', { class: 'summary-text' },
        h('b', {}, total ? `今天有 ${courses.length} 节课，${tasks.length} 件待办` : '今天没有安排'),
        h('span', {}, total
          ? (nextUp ? `下一项 ${fmtTime(nextUp.startMin)} ${nextUp.kind === 'course' ? nextUp.course.name : nextUp.event?.title ?? nextUp.task?.title}`
            : done === total ? '今天的安排都完成了 🎉' : '今天的课程与日程已经结束')
          : '好好休息，或者添加一件想做的事'),
        h('span', { class: 'summary-count' }, total ? `${done}/${total}` : '')));

    const timeline = h('section', { class: 'timeline' });
    if (ag.allDay.length) timeline.append(h('div', { class: 'allday' }, ag.allDay.map((e) => eventCard(e, { day }))));
    for (const x of ag.timed) {
      const card = x.kind === 'course' ? courseCard(x, { past: isPast(x) })
        : x.kind === 'event' ? eventCard(x, { past: isPast(x), day })
          : blockCard(x);
      const endLabel = x.kind === 'event' && x.endDay > day ? '' : fmtTime(x.endMin);
      timeline.append(h('div', { class: 'tl-row' },
        h('div', { class: 'tl-time' }, h('b', {}, x.kind === 'event' && x.day < day ? '继续' : fmtTime(x.startMin)), h('span', {}, endLabel)),
        card));
    }
    if (!ag.timed.length && !ag.allDay.length) {
      timeline.append(emptyState('coffee', sem ? '今天没有课程和日程' : '还没有学期，先创建一个开始排课表吧',
        sem ? null : h('button', { class: 'btn primary', type: 'button', onclick: () => openSemesterForm({ create: true }) }, '创建学期')));
    }

    const taskSec = tasks.length
      ? h('section', { class: 'group tasks-sec' }, h('h3', { class: 'group-title' }, '今日待办'), h('div', { class: 'stack', role: 'list' }, tasks.map((t) => taskRow(t, { today: day, nowMin: now, listName: listName(t) }))))
      : null;

    // 明天预览
    const tom = agendaFor(view, day + 1);
    const tomTasks = tom.tasks.filter((t) => !t.done);
    const tomItems = [...tom.allDay.map((e) => e.event.title), ...tom.timed.filter((x) => x.kind !== 'block').map((x) => `${fmtTime(x.startMin)} ${x.kind === 'course' ? x.course.name : x.event.title}`)];
    const tomorrow = h('section', { class: 'group tomorrow-sec' },
      h('h3', { class: 'group-title' }, `明天 · ${weekdayName(day + 1)}`),
      h('div', { class: 'card tomorrow' },
        tomItems.length || tomTasks.length
          ? [tomItems.length ? h('ul', {}, tomItems.slice(0, 5).map((t) => h('li', {}, t)), tomItems.length > 5 ? h('li', { class: 'more' }, `还有 ${tomItems.length - 5} 项…`) : null) : null,
            tomTasks.length ? h('span', { class: 'meta-chip' }, `${tomTasks.length} 件待办到期`) : null]
          : [h('span', {}, '没有课程，留给自己吧'), icon('coffee', 20)]));

    return h('div', { class: 'today-layout' }, summary, h('div', { class: 'main-col' }, timeline, taskSec), tomorrow);
  },
};
