// 用 Web 端的记录工厂生成 JSON 夹具，供 Kotlin :core 测试验证跨端兼容。
// 用法：node webtest/gen-fixture.mjs ../core/src/test/resources/web-records.json
import { writeFileSync } from 'node:fs';
import * as M from '../internal/server/web/app/js/model.js';

const T = 1_700_000_000_000;
const sem = M.newSemester({ name: '大一下', week1Monday: '2026-08-31', totalWeeks: 20 }, T);
const course = M.newCourse({ semesterId: sem.id, name: '高等数学', teacher: '王老师', room: '教3-204', dayOfWeek: 2, startPeriod: 1, endPeriod: 2, weeks: [3, 1, 2, 5], colorIndex: 3, remindMinutes: 15, note: '带计算器' }, T + 1);
const cancel = M.newException({ courseId: course.id, week: 2, type: 'CANCEL' }, T + 2);
const moved = M.newException({ courseId: course.id, week: 3, type: 'RESCHEDULE', newWeek: 4, newDayOfWeek: 5, newStartPeriod: 3, newEndPeriod: 4, newRoom: '教5-105' }, T + 3);
const event = M.newEvent({ title: '社团例会', start: '2026-10-23T20:00', end: '2026-10-23T21:30', location: '活动中心', colorIndex: 2, remindMinutes: 10, category: '社团', kind: 'EXAM',
  recurrence: M.newRecurrence('WEEKLY', { interval: 2, byDays: [5, 1], until: '2026-12-31', exDates: ['2026-11-06'] }) }, T + 4);
const allDay = M.newEvent({ title: '生日', start: '2026-10-25T00:00', end: '2026-10-25T23:59', allDay: true, kind: 'COUNTDOWN' }, T + 5);
const list = M.newList({ name: '学习', colorIndex: 2, sort: 1 }, T + 6);
const parent = M.newTask({ title: '交作业', listId: list.id, tags: ['数学', '紧急'], priority: 3, startDate: '2026-10-20', due: '2026-10-23T18:00', dueHasTime: true, estimateMinutes: 90, remindMinutes: 30, courseId: course.id,
  repeat: M.newRecurrence('DAILY', { interval: 2, count: 5 }) }, T + 7);
const child = M.newTask({ title: '子任务', parentId: parent.id, sortOrder: 2 }, T + 8);
const done = { ...M.newTask({ title: '已完成' }, T + 9), done: true, doneAt: T + 9000 };
const deleted = M.tombstone(M.newTask({ title: '已删除' }, T + 10), T + 20);

const recs = [sem, course, cancel, moved, event, allDay, list, parent, child, done, deleted];
writeFileSync(process.argv[2] || 'web-records.json', JSON.stringify(recs.map(M.toWire), null, 2) + '\n');
console.log(`wrote ${recs.length} records`);
