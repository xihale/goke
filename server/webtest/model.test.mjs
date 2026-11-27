import test from 'node:test';
import assert from 'node:assert/strict';
import * as U from '../internal/server/web/app/js/util.js';
import * as M from '../internal/server/web/app/js/model.js';

const D = (s) => U.parseDate(s);
const sem = M.newSemester({ name: '大一下', week1Monday: '2026-08-31', totalWeeks: 20 }, 1000);

test('日期工具', () => {
  assert.equal(U.fmtDate(D('2026-10-23')), '2026-10-23');
  assert.equal(U.dow(D('2026-10-23')), 5); // 周五
  assert.equal(U.dow(D('2026-08-31')), 1);
  assert.equal(U.dow(D('1969-12-28')), 7); // 负数天数
  assert.equal(U.fmtDate(U.mondayOf(D('2026-10-25'))), '2026-10-19');
  assert.deepEqual(U.parseDateTime('2026-09-01T08:30:15'), { day: D('2026-09-01'), min: 510 });
  assert.deepEqual(U.parseDateTime('2026-09-01'), { day: D('2026-09-01'), min: 0 });
  assert.equal(U.parseDateTime('xx'), null);
  assert.equal(U.fmtDateTime(D('2026-09-01'), 510), '2026-09-01T08:30');
  assert.deepEqual(U.addMonths(2026, 11, 3), { y: 2027, m: 2 });
  assert.deepEqual(U.addMonths(2026, 1, -1), { y: 2025, m: 12 });
  assert.match(U.uuid(), /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});

test('周次计算', () => {
  const cal = M.semesterCalendar(sem);
  assert.equal(cal.weekOf(D('2026-08-31')), 1);
  assert.equal(cal.weekOf(D('2026-09-06')), 1);
  assert.equal(cal.weekOf(D('2026-09-07')), 2);
  assert.equal(cal.weekOf(D('2026-08-30')), 0);
  assert.equal(cal.weekOf(D('2026-08-01')), -4);
  assert.equal(U.fmtDate(cal.dateOf(3, 5)), '2026-09-18');
  assert.equal(cal.currentWeek(D('2025-01-01')), 1);
  assert.equal(cal.currentWeek(D('2030-01-01')), 20);
  assert.equal(cal.startMin(1), 510);
  assert.equal(cal.endMin(2), 605);
});

test('周次集合格式化与解析', () => {
  const { WeekSets: W } = M;
  assert.equal(W.format([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]), '1-16');
  assert.equal(W.format([1, 3, 5, 6, 7, 9]), '1,3,5-7,9');
  assert.equal(W.format([7, 8]), '7,8');
  assert.equal(W.format([]), '');
  assert.deepEqual(W.parse('1-4,6'), [1, 2, 3, 4, 6]);
  assert.deepEqual(W.parse('1-8单'), [1, 3, 5, 7]);
  assert.deepEqual(W.parse('2-8双'), [2, 4, 6, 8]);
  assert.deepEqual(W.parse('3周，5周'), [3, 5]);
  assert.deepEqual(W.parse('abc'), []);
  assert.deepEqual(W.odd(5), [1, 3, 5]);
});

function course(over = {}) {
  return M.newCourse({ semesterId: sem.id, name: '数据结构', room: '教2-321', dayOfWeek: 2, startPeriod: 1, endPeriod: 2, weeks: M.WeekSets.all(16), ...over }, 1);
}

test('课程展开、停课与调课', () => {
  const c = course();
  const exCancel = M.newException({ courseId: c.id, week: 2, type: 'CANCEL' });
  const exMove = M.newException({ courseId: c.id, week: 3, type: 'RESCHEDULE', newDayOfWeek: 4, newStartPeriod: 3, newEndPeriod: 4, newRoom: '教1-101' });
  const cal = M.semesterCalendar(sem);
  const occ = M.expandCourses(sem, [c], [exCancel, exMove], cal.weekStart(1), cal.weekEnd(4));
  assert.deepEqual(occ.map((o) => o.week), [1, 3, 4]); // 第 2 周停课
  const moved = occ.find((o) => o.week === 3);
  assert.equal(moved.rescheduled, true);
  assert.equal(moved.room, '教1-101');
  assert.equal(U.dow(moved.day), 4);
  assert.equal(moved.startMin, 625); // 第 3 节 10:25
  assert.equal(occ[0].startMin, 510);
  // 已删除的异常不生效
  const dead = M.tombstone(exCancel);
  assert.equal(M.expandCourses(sem, [c], [dead], cal.weekStart(1), cal.weekEnd(4)).length, 4);
  // 其他学期的课程不展开
  assert.equal(M.expandCourses(sem, [course({ semesterId: 'other' })], [], cal.weekStart(1), cal.weekEnd(4)).length, 0);
});

test('课程自定义时间优先于节次默认时刻', () => {
  const exam = course({
    name: '考试：高等数学',
    kind: 'EXAM',
    startPeriod: 1,
    endPeriod: 3,
    weeks: [1, 2, 3],
    startTime: '09:00',
    endTime: '11:00',
  });
  const roomOnly = M.newException({ courseId: exam.id, week: 2, type: 'RESCHEDULE', newRoom: '教3-208' });
  const periodMove = M.newException({ courseId: exam.id, week: 3, type: 'RESCHEDULE', newStartPeriod: 5, newEndPeriod: 6 });
  const cal = M.semesterCalendar(sem);
  const occ = M.expandCourses(sem, [exam], [roomOnly, periodMove], cal.weekStart(1), cal.weekEnd(3));
  assert.equal(occ.length, 3);
  assert.equal(occ[0].startMin, 540);
  assert.equal(occ[0].endMin, 660);
  assert.equal(occ[1].startMin, 540);
  assert.equal(occ[1].endMin, 660);
  assert.equal(occ[1].room, '教3-208');
  assert.equal(occ[2].startMin, 830);
  assert.equal(occ[2].endMin, 925);
});

test('课程冲突', () => {
  const a = course();
  const b = course({ name: '英语', startPeriod: 2, endPeriod: 3, weeks: [1, 2] });
  const c = course({ name: '体育', startPeriod: 5, endPeriod: 6 });
  const d = course({ name: '单周', startPeriod: 1, endPeriod: 2, weeks: [17] });
  const hit = M.courseConflicts(b, [a, c, d]).map((x) => x.name);
  assert.deepEqual(hit, ['数据结构']);
});

test('随堂考或同课程考试提升为考试层且不产生冲突', () => {
  const regular = course({ id: 'c-math', name: '高等数学', room: '教1-201', dayOfWeek: 1, startPeriod: 1, endPeriod: 2, weeks: [1, 2] });
  const inClassExam = course({ id: 'c-math-exam', name: '随堂测验：高等数学', kind: 'EXAM', room: '', dayOfWeek: 1, startPeriod: 1, endPeriod: 2, weeks: [2] });
  const other = course({ id: 'c-eng', name: '大学英语', room: '教2-102', dayOfWeek: 1, startPeriod: 1, endPeriod: 2, weeks: [2] });
  const cal = M.semesterCalendar(sem);
  const occ = M.expandCourses(sem, [regular, inClassExam], [], cal.weekStart(2), cal.weekEnd(2));
  assert.equal(occ.length, 1);
  assert.equal(occ[0].course.id, inClassExam.id);
  assert.equal(occ[0].room, '教1-201');
  assert.deepEqual(M.courseConflicts(inClassExam, [regular]), []);
  assert.deepEqual(M.courseConflicts(inClassExam, [regular, other]).map((x) => x.name), ['大学英语']);
});

test('重复规则', () => {
  const a = D('2026-01-31');
  const monthly = M.newRecurrence('MONTHLY');
  const ds = M.recurrenceDates(monthly, a, D('2026-01-01'), D('2026-06-30')).map(U.fmtDate);
  assert.deepEqual(ds, ['2026-01-31', '2026-03-31', '2026-05-31']); // 没有 31 号的月份跳过
  const weekly = M.newRecurrence('WEEKLY', { byDays: [1, 3], count: 4 });
  const w = M.recurrenceDates(weekly, D('2026-09-02'), D('2026-09-01'), D('2026-12-31')).map(U.fmtDate);
  assert.deepEqual(w, ['2026-09-02', '2026-09-07', '2026-09-09', '2026-09-14']);
  const daily = M.newRecurrence('DAILY', { interval: 2, until: '2026-09-07', exDates: ['2026-09-03'] });
  const dd = M.recurrenceDates(daily, D('2026-09-01'), D('2026-09-01'), D('2026-09-30')).map(U.fmtDate);
  assert.deepEqual(dd, ['2026-09-01', '2026-09-05', '2026-09-07']);
  const yearly = M.newRecurrence('YEARLY');
  const yy = M.recurrenceDates(yearly, D('2024-02-29'), D('2024-01-01'), D('2033-01-01')).map(U.fmtDate);
  assert.deepEqual(yy, ['2024-02-29', '2028-02-29', '2032-02-29']);
  // 自定义：每月哪几天 / 每年哪几月哪几天（与 Android RecurrenceEngine 同义）
  const days2 = M.newRecurrence('MONTHLY', { byDays: [1, 15] });
  const m2 = M.recurrenceDates(days2, D('2026-01-10'), D('2026-01-01'), D('2026-02-28')).map(U.fmtDate);
  assert.deepEqual(m2, ['2026-01-15', '2026-02-01', '2026-02-15']);
  const ymd2 = M.newRecurrence('YEARLY', { byMonths: [3, 10], byDays: [5, 12] });
  const y2 = M.recurrenceDates(ymd2, D('2026-03-05'), D('2026-01-01'), D('2026-12-31')).map(U.fmtDate);
  assert.deepEqual(y2, ['2026-03-05', '2026-03-12', '2026-10-05', '2026-10-12']);
});

test('事件展开：重复与跨天', () => {
  const ev = M.newEvent({ title: '例会', start: '2026-09-02T20:00', end: '2026-09-02T21:00', recurrence: M.newRecurrence('WEEKLY') });
  const occ = M.expandEvent(ev, D('2026-09-01'), D('2026-09-20'));
  assert.deepEqual(occ.map((o) => U.fmtDate(o.day)), ['2026-09-02', '2026-09-09', '2026-09-16']);
  const multi = M.newEvent({ title: '运动会', start: '2026-10-01T00:00', end: '2026-10-03T00:00', allDay: true });
  assert.equal(M.expandEvent(multi, D('2026-10-02'), D('2026-10-02')).length, 1);
  assert.equal(M.expandEvent(multi, D('2026-10-04'), D('2026-10-04')).length, 0);
  const night = M.newEvent({ title: '夜宵', start: '2026-10-01T23:00', end: '2026-10-02T00:00' });
  assert.equal(M.expandEvent(night, D('2026-10-02'), D('2026-10-02')).length, 0); // 结束于 00:00 不占次日
  assert.equal(M.expandEvent(M.tombstone(night), D('2026-10-01'), D('2026-10-01')).length, 0);
});

test('待办筛选与完成', () => {
  const today = D('2026-10-23');
  const mk = (f) => M.newTask({ title: 't', ...f });
  const overdue = mk({ due: '2026-10-20T00:00' });
  const dueToday = mk({ due: '2026-10-23T00:00' });
  const dueTimePast = mk({ due: '2026-10-23T08:00', dueHasTime: true });
  const planned = mk({ due: '2026-10-28T00:00' });
  const far = mk({ due: '2026-12-28T00:00' });
  const none = mk({});
  const done = { ...mk({ due: '2026-10-23T00:00' }), done: true };
  const sub = mk({ parentId: dueToday.id, due: '2026-10-23T00:00' });
  const all = [overdue, dueToday, dueTimePast, planned, far, none, done, sub];
  const ids = (l) => l.map((t) => t.id);
  assert.deepEqual(ids(M.filterTasks(all, 'today', today, 600)).sort(), ids([overdue, dueToday, dueTimePast]).sort());
  assert.deepEqual(ids(M.filterTasks(all, 'today', today, 400)).sort(), ids([overdue, dueToday, dueTimePast]).sort());
  assert.ok(M.isOverdue(dueTimePast, today, 600) && !M.isOverdue(dueTimePast, today, 400));
  assert.deepEqual(ids(M.filterTasks(all, 'planned', today, 0)), ids([planned]));
  assert.deepEqual(ids(M.filterTasks(all, 'done', today, 0)), ids([done]));
  assert.equal(M.filterTasks(all, 'all', today, 0).length, 6); // 不含子任务与已完成
  // 排序：有截止的在前，早的在前
  assert.equal(M.filterTasks(all, 'all', today, 0)[0].id, overdue.id);
  assert.equal(M.filterTasks(all, 'all', today, 0).at(-1).id, none.id);
});

test('重复待办完成后生成下一次', () => {
  const t = M.newTask({ title: '周报', due: '2026-10-23T18:00', dueHasTime: true, repeat: M.newRecurrence('WEEKLY', { count: 3 }) }, 1);
  const { finished, next } = M.completeTask(t, 5000, D('2026-10-23'));
  assert.equal(finished.done, true);
  assert.equal(finished.doneAt, 5000);
  assert.equal(next.due, '2026-10-30T18:00');
  assert.equal(next.done, false);
  assert.notEqual(next.id, t.id);
  assert.equal(next.repeat.count, 2);
  const last = M.completeTask({ ...t, repeat: M.newRecurrence('WEEKLY', { count: 1 }) }, 5000, D('2026-10-23'));
  assert.equal(last.next.repeat, null);
  const plain = M.completeTask(M.newTask({ title: 'x' }), 5000, D('2026-10-23'));
  assert.equal(plain.next, null);
  assert.equal(M.reopenTask(finished, 9000).done, false);
});

test('同步字段：touch / tombstone / restore', () => {
  const r = course();
  const a = M.touch(r, { name: 'x' }, 0); // 时钟倒退也要比旧值大
  assert.equal(a.updatedAt, r.updatedAt + 1);
  const d = M.tombstone(a, 99999);
  assert.equal(d.deletedAt, 99999);
  assert.equal(d.updatedAt, 99999);
  const back = M.restore(d, 100000);
  assert.equal(back.deletedAt, null);
  assert.equal(M.toWire(back).kind, 'Course');
  assert.equal(M.toWire(back).data._t, 'course');
});

test('liveView 与当天汇总', () => {
  const c = course();
  const ev = M.newEvent({ title: '社团例会', start: '2026-09-01T20:00', end: '2026-09-01T21:00' });
  const blk = { ...M.newTask({ title: '实验报告' }), blockStart: '2026-09-01T16:30', blockMinutes: 60 };
  const due = M.newTask({ title: '交作业', due: '2026-09-01T00:00' });
  const dead = M.tombstone(M.newTask({ title: '删掉' }));
  const recs = new Map([sem, c, ev, blk, due, dead].map((r) => [r.id, r]));
  const view = M.liveView(recs);
  assert.equal(view.tasks.length, 2); // 时间块 + 交作业，已删除的不算
  assert.equal(M.currentSemester(view).id, sem.id);
  const ag = M.agendaFor(view, D('2026-09-01')); // 周二：数据结构 1-2 节
  assert.deepEqual(ag.timed.map((x) => x.kind), ['course', 'block', 'event']);
  assert.deepEqual(ag.tasks.map((t) => t.title), ['交作业']);
  const marks = M.monthMarks(view, 2026, 9);
  assert.ok(marks.get(D('2026-09-01')).course && marks.get(D('2026-09-01')).event && marks.get(D('2026-09-01')).task);
  assert.ok(!marks.has(D('2026-09-05')) || !marks.get(D('2026-09-05')).event);
});

test('课程未知字段往返保留', () => {
  const c = course({ name: '高数', startPeriod: 5, endPeriod: 6 });
  const cal = M.semesterCalendar(sem);
  const occ = M.expandCourses(sem, [c], [], cal.weekStart(1), cal.weekEnd(1));
  assert.equal(occ[0].startMin, 13 * 60 + 50); // 第 5 节 13:50
  assert.equal(occ[0].endMin, 15 * 60 + 25); // 第 6 节 15:25

  const withExtra = { ...c, customUnknownField: 'keep-me' };
  const touched = M.touch(withExtra, { room: '教4-101' }, 5000);
  assert.equal(touched.customUnknownField, 'keep-me');
});

test('考试展示拆分、座位号提取与纵中横分词', () => {
  const pe = M.splitExamDisplay('考试：体育（3）');
  assert.equal(pe.badgeFull, '考试');
  assert.equal(pe.badgeShort, '考试');
  assert.equal(pe.title, '体育(3)');
  assert.deepEqual(M.tokenizeVerticalTitle(pe.title), [
    { text: '体', tcy: false },
    { text: '育', tcy: false },
    { text: '(3)', tcy: true },
  ]);

  const mid = M.splitExamDisplay('期中考试：概率论');
  assert.equal(mid.badgeFull, '期中考试');
  assert.equal(mid.badgeShort, '期中');
  assert.equal(mid.title, '概率论');

  assert.equal(M.extractExamSeat('大学城校区 · 正常安排 · 座位号 18'), '18');
  assert.equal(M.isExamTask({ id: 't1', extra: { kind: 'EXAM' } }), true);
  assert.equal(M.isExamTask({ id: 't2', tags: ['考试'] }), true);
  assert.equal(M.isExamTask({ id: 't3', tags: ['学习'] }), false);
  assert.deepEqual(M.blockRows(sem.periods, 10 * 60 + 25, 12 * 60), { startPeriod: 3, endPeriod: 4 });
  assert.deepEqual(M.blockRows(sem.periods, 12 * 60 + 10, 12 * 60 + 50), { startPeriod: 4, endPeriod: 4 });
});
