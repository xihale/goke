// 各视图共用的小组件：课程 / 日程 / 待办条目、空状态、日期文案。

import { toggleTask } from '../actions.js';
import { h, icon } from '../dom.js';
import { openCourseDetail, openEventForm, openTaskForm } from '../forms.js';
import { isOverdue, subtasks } from '../model.js';
import { ctx } from '../ctx.js';
import { dueOf } from '../model.js';
import { fmtTime, shortDate, weekdayName } from '../util.js';

export function emptyState(iconName, text, action) {
  return h('div', { class: 'empty' }, h('div', { class: 'empty-icon' }, icon(iconName, 36)), h('p', {}, text), action || null);
}

export function relativeDay(day, today) {
  const d = day - today;
  if (d === 0) return '今天';
  if (d === 1) return '明天';
  if (d === -1) return '昨天';
  if (d === 2) return '后天';
  return shortDate(day);
}

export function dueLabel(t, today, nowMin) {
  const d = dueOf(t);
  if (!d) return null;
  const text = `${relativeDay(d.day, today)}${t.dueHasTime ? ` ${fmtTime(d.min)}` : ''}`;
  return { text, overdue: isOverdue(t, today, nowMin) };
}

const KIND_LABEL = { EXAM: '考试', COUNTDOWN: '倒数日' };

/** 课程卡片（今日 / 日历 / 详情共用）。 */
export function courseCard(occ, { past = false, compact = false } = {}) {
  const c = occ.course;
  const meta = [occ.room, `第 ${occ.startPeriod}${occ.endPeriod !== occ.startPeriod ? `–${occ.endPeriod}` : ''} 节`, compact ? null : c.teacher]
    .filter(Boolean).join(' · ');
  return h('button', { type: 'button', class: `card item c${c.colorIndex % 8}${past ? ' past' : ''}`, onclick: () => openCourseDetail(occ) },
    h('span', { class: 'item-title' }, c.name),
    h('span', { class: 'item-meta' }, meta, occ.rescheduled ? ' · 已调课' : ''));
}

export function eventCard(occ, { past = false, day = null } = {}) {
  const e = occ.event;
  const parts = [];
  if (KIND_LABEL[e.kind]) parts.push(KIND_LABEL[e.kind]);
  if (e.location) parts.push(e.location);
  if (e.recurrence) parts.push('重复');
  if (day != null && occ.endDay > occ.day) parts.push(`${shortDate(occ.day)} – ${shortDate(occ.endDay)}`);
  return h('button', { type: 'button', class: `card item c${(e.colorIndex ?? 4) % 8}${past ? ' past' : ''}`, onclick: () => openEventForm({ event: e }) },
    h('span', { class: 'item-title' }, e.title),
    h('span', { class: 'item-meta' }, parts.join(' · ') || '日程'));
}

export function blockCard(item) {
  const t = item.task;
  const mins = item.endMin - item.startMin;
  return h('button', { type: 'button', class: 'card item block', onclick: () => openTaskForm({ task: t }) },
    h('span', { class: 'item-title' }, t.title),
    h('span', { class: 'item-meta' }, `待办时间块 · 预计 ${mins >= 60 ? `${Math.round((mins / 60) * 10) / 10} 小时` : `${mins} 分钟`}`));
}

/** 待办行：左侧复选框，右侧标题与元信息。 */
export function taskRow(t, { today, nowMin, listName } = {}) {
  const { store } = ctx;
  const due = dueLabel(t, today, nowMin);
  const subs = subtasks(store.view().tasks, t.id);
  const prio = { 1: '低', 2: '中', 3: '高' }[t.priority];
  const meta = [
    due ? h('span', { class: `meta-chip${due.overdue ? ' overdue' : ''}` }, icon('clock', 14), due.text) : null,
    listName ? h('span', { class: 'meta-chip' }, listName) : null,
    subs.length ? h('span', { class: 'meta-chip' }, icon('list', 14), `${subs.filter((s) => s.done).length}/${subs.length}`) : null,
    t.repeat ? h('span', { class: 'meta-chip' }, icon('repeat', 14)) : null,
  ];
  const check = h('button', {
    type: 'button',
    class: `check${t.done ? ' on' : ''}`,
    role: 'checkbox',
    'aria-checked': String(t.done),
    'aria-label': t.done ? `取消完成：${t.title}` : `完成：${t.title}`,
    onclick: (e) => { e.stopPropagation(); toggleTask(t, !t.done); },
  }, t.done ? icon('check', 18) : null);
  return h('div', { class: `card task${t.done ? ' done' : ''}`, role: 'listitem' },
    check,
    h('button', { type: 'button', class: 'task-main', onclick: () => openTaskForm({ task: t }) },
      h('span', { class: 'task-title' }, t.title),
      meta.some(Boolean) ? h('span', { class: 'task-meta' }, meta) : null),
    prio ? h('span', { class: `prio p${t.priority}` }, prio) : null);
}

export const weekdayLabel = (day) => weekdayName(day);
