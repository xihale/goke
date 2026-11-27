// 演示数据：只用于本地预览与截图（node webtest/mock.mjs）。
import * as M from '../internal/server/web/app/js/model.js';
import * as U from '../internal/server/web/app/js/util.js';

export function seedDemo(state) {
  const u = state.users.get('demo');
  const now = Date.now();
  const today = U.todayNum();
  const curWeek = 13;
  const week1Mon = U.mondayOf(today) - (curWeek - 1) * 7;
  const sem = M.newSemester({ name: '2026-2027-1', week1Monday: U.fmtDate(week1Mon), totalWeeks: 20 }, now - 1e6);
  const recs = [sem];
  const C = (name, room, day, sp, ep, color, weeks = M.WeekSets.all(16), teacher = '', extra = {}) =>
    recs.push(M.newCourse({ semesterId: sem.id, name, room, dayOfWeek: day, startPeriod: sp, endPeriod: ep, colorIndex: color, weeks, teacher, ...extra }, now - 1e6));
  C('概率论与数理统计', '教3-204', 1, 1, 2, 0, undefined, '涂钰青');
  C('大学物理(2)', '教1-401', 4, 1, 2, 2, undefined, '史君');
  C('补考：大学物理（2）', '教1-102', 5, 1, 1, 2, [curWeek], '张敏', { kind: 'EXAM', note: '大学城校区 · 座位号 07' });
  C('工程英语', '实3-611', 2, 3, 4, 5, undefined, '董韵');
  C('考试：体育（3）', '', 3, 3, 4, 2, [curWeek], '李勃', { kind: 'EXAM' });
  C('期中考试：概率论', '教3-204', 4, 3, 4, 2, [curWeek], '王伟', { kind: 'EXAM', note: '大学城校区 · 座位号 18' });
  C('随堂测验：数据结构与算法', '实4-401', 5, 3, 4, 2, [curWeek], '陈思远', { kind: 'EXAM' });
  C('数字逻辑与系统设计', '教5-105', 1, 6, 7, 1, undefined, '丁磊');
  C('劳动教育', '', 2, 6, 6, 6);
  C('大学生心理健康教育', '教3-110', 5, 6, 7, 7);
  C('3D打印综合训练', '实4-201', 3, 10, 12, 3);
  C('西方哲学导论', '教4-310', 4, 10, 12, 4);
  const dt = (d, hm) => `${U.fmtDate(today + d)}T${hm}`;
  const l1 = M.newList({ name: '学习', colorIndex: 1, sort: 0 }, now);
  const l2 = M.newList({ name: '生活', colorIndex: 4, sort: 1 }, now);
  recs.push(l1, l2);
  const T = (f) => recs.push(M.newTask(f, now));
  recs.push({ ...M.newTask({ title: '社团例会', listId: l2.id, note: '学生活动中心', due: dt(0, '21:30'), dueHasTime: true, estimateMinutes: 90, tags: ['社团'] }, now), blockStart: dt(0, '20:00'), blockMinutes: 90 });
  recs.push({ ...M.newTask({ title: '考试：高数期中考试', listId: l1.id, note: '大学城校区', priority: 3, due: dt(0, '16:30'), dueHasTime: true, estimateMinutes: 120, tags: ['考试'], extra: { kind: 'EXAM', location: '教1-101' } }, now), blockStart: dt(0, '14:30'), blockMinutes: 120 });
  T({ title: '期中考试：英语口试', listId: l1.id, priority: 3, due: dt(1, '10:00'), dueHasTime: true, tags: ['考试'], extra: { kind: 'EXAM', location: '教2-302' } });
  T({ title: '校运动会', startDate: U.fmtDate(today + 5), due: dt(5, '23:59'), tags: ['活动'] });
  recs.push({ ...M.newTask({ title: '周会', due: dt(-3, '20:00'), dueHasTime: true, estimateMinutes: 60, repeat: M.newRecurrence('WEEKLY') }, now), blockStart: dt(-3, '19:00'), blockMinutes: 60 });
  T({ title: '交概率论第三章作业', listId: l1.id, priority: 3, due: dt(0, '00:00') });
  T({ title: '数据结构实验报告', listId: l1.id, priority: 2, due: dt(2, '23:00'), dueHasTime: true });
  T({ title: '预习下周英语课文', listId: l1.id, priority: 1, due: dt(1, '00:00') });
  T({ title: '取快递', listId: l2.id, due: dt(0, '00:00') });
  T({ title: '给手机充话费', listId: l2.id, due: dt(-2, '00:00') });
  T({ title: '整理宿舍', listId: l2.id });
  const done = M.newTask({ title: '买资料书', listId: l2.id, due: dt(-1, '00:00') }, now);
  recs.push({ ...done, done: true, doneAt: now });
  const parent = M.newTask({ title: '准备期中复习', listId: l1.id, priority: 2, due: dt(4, '00:00') }, now);
  recs.push(parent);
  recs.push(M.newTask({ title: '整理高数笔记', parentId: parent.id, listId: l1.id }, now));
  recs.push({ ...M.newTask({ title: '刷往年真题', parentId: parent.id, listId: l1.id }, now), done: true, doneAt: now });
  recs.push({ ...M.newTask({ title: '数据结构实验时间块', listId: l1.id, estimateMinutes: 60 }, now), blockStart: dt(0, '16:30'), blockMinutes: 60 });
  for (const r of recs) {
    u.seq += 1;
    u.records.set(r.id, { id: r.id, kind: M.TYPES[r._t], data: r, updatedAt: r.updatedAt, deletedAt: null, seq: u.seq });
  }
}
