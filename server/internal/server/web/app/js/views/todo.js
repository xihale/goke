// 待办：智能清单（今天 / 未来 7 天 / 全部 / 逾期 / 已完成）+ 自定义清单。

import { ctx } from '../ctx.js';
import { h, icon } from '../dom.js';
import { openListForm, openTaskForm } from '../forms.js';
import { filterTasks, newTask, topLevelTasks } from '../model.js';
import { fmtDateTime, minutesOfDay, todayNum } from '../util.js';
import { emptyState, taskRow } from './shared.js';

const SMART = [
  ['today', '今天', 'sun'],
  ['planned', '未来 7 天', 'calendar'],
  ['all', '全部', 'list'],
  ['overdue', '已逾期', 'flag'],
  ['done', '已完成', 'check'],
];

let filter = 'today';
let showDone = false;

ctx.onListCreated = (list) => { filter = `list:${list.id}`; ctx.rerender(); };
ctx.onListRemoved = () => { filter = 'all'; ctx.rerender(); };

function currentList(view) {
  return filter.startsWith('list:') ? view.lists.find((l) => l.id === filter.slice(5)) : null;
}

function tasksFor(view, day, now) {
  const list = currentList(view);
  if (list) {
    const mine = topLevelTasks(view.tasks).filter((t) => t.listId === list.id);
    const open = filterTasks(mine, 'all', day, now);
    const done = filterTasks(mine, 'done', day, now);
    return { open, done };
  }
  if (filter.startsWith('list:')) filter = 'all'; // 清单已被删除（例如在别的设备上）
  return { open: filterTasks(view.tasks, filter, day, now), done: [] };
}

export const todoView = {
  id: 'todo',
  head() {
    const view = ctx.store.view();
    const list = currentList(view);
    return { eyebrow: '', title: list ? list.name : (SMART.find((s) => s[0] === filter)?.[1] ?? '待办') };
  },
  add: () => {
    const view = ctx.store.view();
    openTaskForm({ listId: currentList(view)?.id, due: filter === 'today' ? todayNum() : undefined });
  },
  render() {
    const { store } = ctx;
    const view = store.view();
    const day = todayNum();
    const now = minutesOfDay();
    const list = currentList(view);
    const { open, done } = tasksFor(view, day, now);
    const countOf = (which) => filterTasks(view.tasks, which, day, now).length;

    const navBtn = (id, label, ic, count, colorIdx) => h('button', {
      type: 'button',
      class: `nav-chip${filter === id ? ' active' : ''}`,
      'aria-pressed': String(filter === id),
      onclick: () => { filter = id; showDone = false; ctx.rerender(); },
    }, colorIdx != null ? h('i', { class: `list-dot c${colorIdx % 8}` }) : icon(ic, 18), h('span', { class: 'nav-chip-label' }, label), count ? h('span', { class: 'nav-chip-count' }, count) : null);

    const side = h('nav', { class: 'todo-nav', 'aria-label': '待办清单' },
      SMART.map(([id, label, ic]) => navBtn(id, label, ic, id === 'done' ? 0 : countOf(id))),
      h('div', { class: 'todo-nav-sep' }, '清单'),
      view.lists.map((l) => navBtn(`list:${l.id}`, l.name, 'list', topLevelTasks(view.tasks).filter((t) => t.listId === l.id && !t.done).length, l.colorIndex)),
      h('button', { type: 'button', class: 'nav-chip add-list', onclick: () => openListForm() }, icon('plus', 18), h('span', { class: 'nav-chip-label' }, '新建清单')));

    const input = h('input', {
      class: 'input quick-input', type: 'text', placeholder: list ? `添加到「${list.name}」` : '添加待办', 'aria-label': '快速添加待办', maxLength: 120, dataset: { keep: 'quick-add' },
    });
    const quick = h('form', {
      class: 'quick-add',
      onsubmit: (e) => {
        e.preventDefault();
        const title = input.value.trim();
        if (!title) return;
        input.value = '';
        const f = { title, listId: list?.id };
        if (filter === 'today') f.due = fmtDateTime(day, 0);
        store.put(newTask(f));
      },
    }, input, h('button', { class: 'btn primary', type: 'submit' }, '添加'));

    const lname = (t) => (list ? null : view.lists.find((l) => l.id === t.listId)?.name);
    const rows = (arr) => h('div', { class: 'stack', role: 'list' }, arr.map((t) => taskRow(t, { today: day, nowMin: now, listName: lname(t) })));
    const main = h('section', { class: 'todo-main' },
      filter === 'done' ? null : quick,
      open.length ? rows(open) : emptyState(filter === 'done' ? 'check' : 'sparkle', filter === 'done' ? '还没有完成的待办' : filter === 'overdue' ? '没有逾期的待办，真棒' : '这里没有待办了'),
      done.length
        ? h('div', { class: 'done-sec' },
          h('button', { type: 'button', class: 'btn ghost small', 'aria-expanded': String(showDone), onclick: () => { showDone = !showDone; ctx.rerender(); } }, `${showDone ? '收起' : '查看'}已完成（${done.length}）`),
          showDone ? rows(done) : null)
        : null,
      list ? h('div', { class: 'list-tools' }, h('button', { type: 'button', class: 'btn ghost small', onclick: () => openListForm({ list }) }, icon('edit', 16), '编辑清单')) : null);

    return h('div', { class: 'todo-layout' }, side, main);
  },
};
