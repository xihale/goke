// 设置导出/导入备份、云端 MCP 配置与旧日程转待办的纯函数测试。
import test from 'node:test';
import assert from 'node:assert/strict';
import * as M from '../internal/server/web/app/js/model.js';
import { convertEventToTask } from '../internal/server/web/app/js/forms.js';
import { buildExportBundle, buildMcpConfig, parseBackup, restoreBackupRecords } from '../internal/server/web/app/js/views/settings.js';

test('导出 canonical ExportBundle 格式', () => {
  const sem = M.newSemester({ name: '大一下', week1Monday: '2026-08-31' }, 100);
  const task = M.newTask({ title: '交作业' }, 200);
  const recs = new Map([[sem.id, sem], [task.id, task], ['dead', M.tombstone(M.newTask({ title: '已删' }, 100), 300)]]);
  const view = M.liveView(recs);
  const bundle = buildExportBundle(view, 9999);
  assert.equal(bundle.app, 'mintcal');
  assert.equal(bundle.version, 1);
  assert.equal(bundle.exportedAt, 9999);
  assert.deepEqual(Object.keys(bundle.snapshot).sort(), ['courses', 'events', 'exceptions', 'lists', 'semesters', 'tasks']);
  assert.equal(bundle.snapshot.semesters.length, 1);
  assert.equal(bundle.snapshot.tasks.length, 1);
});

test('导入兼容 canonical ExportBundle（含无 _t 的 Android 备份）与旧版 mintcal-web', () => {
  const canonicalText = JSON.stringify({
    app: 'mintcal',
    version: 1,
    exportedAt: 1700000000000,
    snapshot: {
      semesters: [{ id: 's1', name: '秋季', week1Monday: '2026-08-31', totalWeeks: 20, periods: [], isCurrent: true }],
      courses: [{ id: 'c1', semesterId: 's1', name: '高数', dayOfWeek: 1, startPeriod: 1, endPeriod: 2, weeks: [1, 2] }],
      exceptions: [],
      events: [{ id: 'e1', title: '旧日程', start: '2026-09-01T10:00', end: '2026-09-01T11:00' }],
      tasks: [{ id: 't1', title: '写报告', tags: ['学习'], dueBeforeNextClass: true, remindPersistent: true }],
      lists: [{ id: 'l1', name: '学习' }],
    },
  });
  const p1 = parseBackup(canonicalText);
  assert.equal(p1.records.length, 5);
  assert.equal(p1.counts.courses, 1);
  assert.equal(p1.counts.events, 1);
  assert.equal(p1.counts.tasks, 1);
  assert.equal(p1.records.find((r) => r.id === 't1')._t, 'task');
  assert.equal(p1.records.find((r) => r.id === 'c1')._t, 'course');

  const legacyText = JSON.stringify({
    app: 'mintcal-web',
    exportedAt: '2026-10-01T00:00:00.000Z',
    records: [
      M.newTask({ title: '旧版待办' }, 10),
      M.tombstone(M.newTask({ title: '已删' }, 10), 20),
    ],
  });
  const p2 = parseBackup(legacyText);
  assert.equal(p2.records.length, 1);
  assert.equal(p2.records[0].title, '旧版待办');

  assert.throws(() => parseBackup('not json'));
  assert.throws(() => parseBackup(JSON.stringify({ app: 'other', records: [] })));
});

test('restoreBackupRecords 覆盖现有记录并递增 updatedAt 以便同步', () => {
  const keep = M.newTask({ title: '备份里也有' }, 100);
  const drop = M.newTask({ title: '备份里没有' }, 100);
  const map = new Map([[keep.id, M.tombstone(keep, 500)], [drop.id, drop]]);
  const written = [];
  const fakeStore = {
    allRecords: () => [...map.values()],
    get: (id) => map.get(id),
    put: (...recs) => {
      for (const r of recs) {
        map.set(r.id, r);
        written.push(r);
      }
    },
  };
  restoreBackupRecords(fakeStore, [{ ...keep, updatedAt: 100 }], 600);
  assert.equal(map.get(drop.id).deletedAt, 600);
  assert.equal(map.get(keep.id).deletedAt, null);
  assert.equal(map.get(keep.id).updatedAt, 600);
  assert.equal(written.length, 2);
});

test('buildMcpConfig 生成合法的 mcpServers JSON', () => {
  const cfg = JSON.parse(buildMcpConfig('https://cal.example.com/'));
  assert.equal(cfg.mcpServers.mintcal.type, 'http');
  assert.equal(cfg.mcpServers.mintcal.url, 'https://cal.example.com/mcp');
  assert.equal(cfg.mcpServers.mintcal.headers.Authorization, 'Bearer <token>');
});

test('convertEventToTask 将有时间日程与全天日程转为待办', () => {
  const timedEv = M.newEvent({
    title: '社团例会',
    start: '2026-09-01T20:00',
    end: '2026-09-01T21:30',
    location: '学生活动中心',
    note: '带笔记本',
    remindMinutes: 15,
    recurrence: M.newRecurrence('WEEKLY'),
  }, 100);
  const t1 = convertEventToTask(timedEv, 500);
  assert.equal(t1._t, 'task');
  assert.equal(t1.title, '社团例会');
  assert.equal(t1.note, '带笔记本\n学生活动中心');
  assert.equal(t1.blockStart, '2026-09-01T20:00');
  assert.equal(t1.blockMinutes, 90);
  assert.equal(t1.due, '2026-09-01T21:30');
  assert.equal(t1.dueHasTime, true);
  assert.equal(t1.remindMinutes, 15);
  assert.equal(t1.repeat.freq, 'WEEKLY');

  const allDayEv = M.newEvent({ title: '运动会', start: '2026-09-05T00:00', end: '2026-09-06T00:00', allDay: true }, 100);
  const t2 = convertEventToTask(allDayEv, 500);
  assert.equal(t2.blockStart, null);
  assert.equal(t2.blockMinutes, null);
  assert.equal(t2.due, '2026-09-05T23:59');
  assert.equal(t2.dueHasTime, false);
});
