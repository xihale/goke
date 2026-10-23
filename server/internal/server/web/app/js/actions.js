// 会修改数据的操作（带撤销提示）。视图与表单共用。

import { ctx } from './ctx.js';
import { toast } from './dom.js';
import { completeTask, currentSemester, reopenTask, restore, subtasks, tombstone, touch } from './model.js';
import { todayNum } from './util.js';

/** 删除一批记录，并给出可撤销的提示。 */
export function removeRecords(recs, label) {
  const { store } = ctx;
  const live = recs.filter((r) => r && r.deletedAt == null);
  if (!live.length) return;
  const now = Date.now();
  store.put(live.map((r) => tombstone(store.get(r.id) || r, now)));
  toast(`已删除${label ? `「${label}」` : ''}`, {
    action: '撤销',
    onAction: () => store.put(live.map((r) => restore(store.get(r.id) || r, Date.now()))),
  });
}

export function removeCourse(course) {
  const exs = ctx.store.view().exceptions.filter((e) => e.courseId === course.id);
  removeRecords([course, ...exs], course.name);
}

export function removeTask(task) {
  removeRecords([task, ...subtasks(ctx.store.view().tasks, task.id)], task.title);
}

export function removeList(list) {
  const { store } = ctx;
  const now = Date.now();
  const tasks = store.view().tasks.filter((t) => t.listId === list.id);
  store.put(tombstone(list, now), ...tasks.map((t) => touch(t, { listId: null }, now)));
  toast(`已删除清单「${list.name}」，其中的待办已移到「无清单」`, {
    action: '撤销',
    onAction: () => store.put(restore(store.get(list.id)), ...tasks.map((t) => touch(store.get(t.id) || t, { listId: list.id }))),
  });
}

export function toggleTask(task, done) {
  const { store } = ctx;
  if (done) {
    const { finished, next } = completeTask(task, Date.now(), todayNum());
    store.put(finished, ...(next ? [next] : []));
    if (next) toast('已完成，并生成了下一次重复');
  } else {
    store.put(reopenTask(task));
  }
}

export function setCurrentSemester(sem) {
  const { store } = ctx;
  const now = Date.now();
  const others = store.view().semesters.filter((s) => s.isCurrent && s.id !== sem.id).map((s) => touch(s, { isCurrent: false }, now));
  store.put(...others, touch(sem, { isCurrent: true }, now));
}

export const semester = () => currentSemester(ctx.store.view());
