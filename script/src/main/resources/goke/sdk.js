// Goke 高级修改 SDK（QuickJS 沙箱内运行；无网络、无文件、无定时器）。
// 所有读写都在内存副本上进行，脚本结束后由宿主对比差异生成「变更集」，Dry Run 不会落库。
function __createSdk(data, opts) {
  var S = {
    semesters: data.semesters, courses: data.courses, exceptions: data.exceptions,
    events: data.events, tasks: data.tasks, lists: data.lists,
  };
  var logs = [];
  var LOG_MAX = 200;
  var TEXT_FIELDS = { name: 1, title: 1, teacher: 1, room: 1, location: 1, note: 1, category: 1 };

  function clone(x) { return x === undefined ? undefined : JSON.parse(JSON.stringify(x)); }
  function fail(msg) { throw new Error(msg); }
  function isInt(x) { return typeof x === 'number' && Math.floor(x) === x; }
  function isStr(x) { return typeof x === 'string'; }
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function uuid() {
    var h = '0123456789abcdef', s = '';
    for (var i = 0; i < 36; i++) {
      if (i === 8 || i === 13 || i === 18 || i === 23) s += '-';
      else if (i === 14) s += '4';
      else if (i === 19) s += h[8 + Math.floor(Math.random() * 4)];
      else s += h[Math.floor(Math.random() * 16)];
    }
    return s;
  }

  // ---- 日期工具（均为本地日期，不涉及时区） ----
  function parseDate(s) {
    var m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s);
    if (!m) fail('日期格式应为 YYYY-MM-DD：' + s);
    return Date.UTC(+m[1], +m[2] - 1, +m[3]);
  }
  function fmtDate(ms) {
    var d = new Date(ms);
    return d.getUTCFullYear() + '-' + pad(d.getUTCMonth() + 1) + '-' + pad(d.getUTCDate());
  }
  function normDateTime(s, field) {
    if (!isStr(s)) fail(field + ' 应为字符串，如 "2026-09-01T08:30"');
    var t = s.trim().replace(' ', 'T');
    if (t.indexOf('T') < 0) t += 'T00:00';
    var m = /^(\d{4}-\d{2}-\d{2})T(\d{1,2}):(\d{2})(:\d{2})?$/.exec(t);
    if (!m) fail(field + ' 格式应为 YYYY-MM-DDTHH:mm：' + s);
    return m[1] + 'T' + pad(+m[2]) + ':' + m[3] + (m[4] && m[4] !== ':00' ? m[4] : '');
  }
  function dtToMs(s) {
    var m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(s);
    return Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5]);
  }
  function msToDt(ms) {
    var d = new Date(ms);
    return fmtDate(ms) + 'T' + pad(d.getUTCHours()) + ':' + pad(d.getUTCMinutes());
  }
  var sem = S.semesters[0] || null;
  function semester() { if (!sem) fail('当前没有学期'); return sem; }
  function dateOf(week, day) {
    return fmtDate(parseDate(semester().week1Monday) + ((week - 1) * 7 + ((day || 1) - 1)) * 86400000);
  }
  function weekOf(date) {
    var diff = Math.floor((parseDate(date) - parseDate(semester().week1Monday)) / 86400000);
    return Math.floor(diff / 7) + 1;
  }

  // ---- 周次解析：数组或 "1-16"、"1-16单"、"2-16双"、"1,3,5-9" ----
  function parseWeeks(w, field) {
    if (Array.isArray(w)) {
      var out = [];
      for (var i = 0; i < w.length; i++) {
        if (!isInt(w[i]) || w[i] < 1) fail((field || 'weeks') + ' 里必须是 ≥1 的整数');
        if (out.indexOf(w[i]) < 0) out.push(w[i]);
      }
      return out.sort(function (a, b) { return a - b; });
    }
    if (isStr(w)) {
      var res = [];
      w.split(/[,，、\s]+/).forEach(function (part) {
        if (!part) return;
        var m = /^(\d+)(?:-(\d+))?([单双])?$/.exec(part);
        if (!m) fail('无法识别的周次：' + part);
        var a = +m[1], b = m[2] ? +m[2] : a;
        for (var k = a; k <= b; k++) {
          if (m[3] === '单' && k % 2 === 0) continue;
          if (m[3] === '双' && k % 2 === 1) continue;
          if (res.indexOf(k) < 0) res.push(k);
        }
      });
      return res.sort(function (a, b) { return a - b; });
    }
    return fail((field || 'weeks') + ' 应为数组或字符串，如 [1,2,3] 或 "1-16"');
  }

  // ---- 查询匹配 ----
  function matches(item, q) {
    if (q === undefined || q === null) return true;
    if (typeof q === 'function') return !!q(item);
    if (isStr(q)) return item.id === q;
    var keys = Object.keys(q);
    for (var i = 0; i < keys.length; i++) {
      var k = keys[i], want = q[k], have = item[k];
      if (want instanceof RegExp) {
        if (!want.test(have === undefined || have === null ? '' : String(have))) return false;
      } else if (typeof want === 'function') {
        if (!want(have, item)) return false;
      } else if (Array.isArray(want)) {
        if (Array.isArray(have)) { for (var j = 0; j < want.length; j++) if (have.indexOf(want[j]) < 0) return false; }
        else if (want.indexOf(have) < 0) return false;
      } else if (Array.isArray(have)) {
        if (have.indexOf(want) < 0) return false;
      } else if (isStr(want) && isStr(have) && TEXT_FIELDS[k]) {
        if (have.indexOf(want) < 0) return false;
      } else if (have !== want) return false;
    }
    return true;
  }

  function idOf(x) { return isStr(x) ? x : (x && x.id); }

  function collection(label, arr, spec) {
    var api = {
      query: function (q) { return arr().filter(function (it) { return matches(it, q); }).map(clone); },
      get: function (id) { var f = arr().filter(function (it) { return it.id === idOf(id); })[0]; return f ? clone(f) : null; },
      count: function (q) { return arr().filter(function (it) { return matches(it, q); }).length; },
      create: function (obj) {
        var rec = spec.build(obj || {});
        spec.validate(rec);
        arr().push(rec);
        return clone(rec);
      },
      update: function (idOrObj, patch) {
        var id = idOf(idOrObj);
        var list = arr(), idx = -1;
        for (var i = 0; i < list.length; i++) if (list[i].id === id) { idx = i; break; }
        if (idx < 0) fail(label + ' 不存在：' + id);
        var src = patch === undefined && idOrObj && typeof idOrObj === 'object' ? idOrObj : patch;
        if (!src || typeof src !== 'object') fail('update 需要要修改的字段对象');
        var next = clone(list[idx]);
        Object.keys(src).forEach(function (k) {
          if (k === 'id') return;
          next[k] = src[k] === undefined ? next[k] : clone(src[k]);
        });
        spec.normalize(next);
        spec.validate(next);
        list[idx] = next;
        return clone(next);
      },
      remove: function (idOrObj) {
        var id = idOf(idOrObj), list = arr();
        for (var i = 0; i < list.length; i++) {
          if (list[i].id === id) { var gone = list.splice(i, 1)[0]; if (spec.onRemove) spec.onRemove(gone); return true; }
        }
        return false;
      },
      removeWhere: function (q) {
        var ids = arr().filter(function (it) { return matches(it, q); }).map(function (it) { return it.id; });
        ids.forEach(function (id) { api.remove(id); });
        return ids.length;
      },
    };
    api.save = api.update;
    return api;
  }

  function color(i) { return isInt(i) ? ((i % 8) + 8) % 8 : 0; }
  function requireText(v, field) { if (!isStr(v) || !v.trim()) fail(field + ' 不能为空'); }

  // ---- 课程 ----
  var courseSpec = {
    build: function (o) {
      var r = {
        id: o.id || uuid(), semesterId: o.semesterId || semester().id, name: o.name,
        teacher: o.teacher || '', room: o.room || '',
        dayOfWeek: o.dayOfWeek !== undefined ? o.dayOfWeek : o.day,
        startPeriod: o.startPeriod, endPeriod: o.endPeriod !== undefined ? o.endPeriod : o.startPeriod,
        weeks: o.weeks === undefined ? fail('weeks 必填，如 "1-16" 或 [1,2,3]') : o.weeks,
        colorIndex: o.colorIndex !== undefined ? o.colorIndex : S.courses.length % 8,
        remindMinutes: o.remindMinutes === undefined ? null : o.remindMinutes,
        note: o.note || '', source: o.source || opts.source,
      };
      courseSpec.normalize(r);
      return r;
    },
    normalize: function (r) { r.weeks = parseWeeks(r.weeks, 'weeks'); r.colorIndex = color(r.colorIndex); },
    validate: function (r) {
      requireText(r.name, 'name');
      if (!isInt(r.dayOfWeek) || r.dayOfWeek < 1 || r.dayOfWeek > 7) fail('dayOfWeek 应为 1（周一）到 7（周日）：' + r.dayOfWeek);
      if (!isInt(r.startPeriod) || r.startPeriod < 1) fail('startPeriod 应为 ≥1 的整数');
      if (!isInt(r.endPeriod) || r.endPeriod < r.startPeriod) fail('endPeriod 不能小于 startPeriod');
      if (r.weeks.length === 0) fail('weeks 不能为空');
    },
    onRemove: function (c) { S.exceptions = S.exceptions.filter(function (e) { return e.courseId !== c.id; }); },
  };

  // ---- 日程 ----
  var eventSpec = {
    build: function (o) {
      var r = {
        id: o.id || uuid(), title: o.title, start: o.start, end: o.end,
        allDay: !!o.allDay, location: o.location || '', note: o.note || '',
        colorIndex: o.colorIndex !== undefined ? o.colorIndex : 4, category: o.category || '',
        remindMinutes: o.remindMinutes === undefined ? null : o.remindMinutes,
        recurrence: o.recurrence === undefined ? null : o.recurrence,
        kind: o.kind || 'NORMAL', source: o.source || opts.source,
      };
      if (r.start === undefined) fail('start 必填，如 "2026-09-01T14:00"');
      r.start = normDateTime(r.start, 'start');
      if (r.end === undefined || r.end === null) r.end = r.allDay ? r.start : msToDt(dtToMs(r.start) + 3600000);
      eventSpec.normalize(r);
      return r;
    },
    normalize: function (r) {
      r.start = normDateTime(r.start, 'start');
      r.end = normDateTime(r.end, 'end');
      r.colorIndex = color(r.colorIndex);
      var rc = r.recurrence;
      if (rc) {
        rc.interval = rc.interval || 1;
        rc.byDays = rc.byDays || [];
        rc.byMonths = rc.byMonths || [];
        rc.exDates = rc.exDates || [];
        if (rc.until === undefined) rc.until = null;
        if (rc.count === undefined) rc.count = null;
      }
    },
    validate: function (r) {
      requireText(r.title, 'title');
      if (dtToMs(r.end) < dtToMs(r.start)) fail('日程结束时间早于开始时间：' + r.title);
      if (['NORMAL', 'EXAM', 'COUNTDOWN'].indexOf(r.kind) < 0) fail('kind 只能是 NORMAL / EXAM / COUNTDOWN');
      if (r.recurrence && ['DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'].indexOf(r.recurrence.freq) < 0) fail('recurrence.freq 只能是 DAILY / WEEKLY / MONTHLY / YEARLY');
    },
  };

  // ---- 待办 ----
  var taskSpec = {
    build: function (o) {
      var r = {
        id: o.id || uuid(), title: o.title, note: o.note || '',
        listId: o.listId === undefined ? null : o.listId, tags: o.tags || [],
        priority: o.priority || 0, startDate: o.startDate === undefined ? null : o.startDate,
        due: o.due === undefined ? null : o.due, dueHasTime: !!o.dueHasTime,
        estimateMinutes: o.estimateMinutes === undefined ? null : o.estimateMinutes,
        done: !!o.done, doneAt: o.done ? opts.nowMillis : null,
        parentId: o.parentId === undefined ? null : o.parentId,
        repeat: o.repeat === undefined ? null : o.repeat,
        remindMinutes: o.remindMinutes === undefined ? null : o.remindMinutes,
        remindPersistent: !!o.remindPersistent,
        courseId: o.courseId === undefined ? null : o.courseId, dueBeforeNextClass: !!o.dueBeforeNextClass,
        blockStart: o.blockStart === undefined ? null : o.blockStart,
        blockMinutes: o.blockMinutes === undefined ? null : o.blockMinutes,
        sortOrder: o.sortOrder || 0, source: o.source || opts.source, extra: o.extra || {},
      };
      taskSpec.normalize(r);
      return r;
    },
    normalize: function (r) {
      if (r.due !== null && r.due !== undefined) {
        r.due = normDateTime(r.due, 'due');
        if (typeof r.dueHasTime !== 'boolean') r.dueHasTime = false;
      }
      if (r.blockStart !== null && r.blockStart !== undefined) r.blockStart = normDateTime(r.blockStart, 'blockStart');
      if (r.startDate !== null && r.startDate !== undefined) { parseDate(r.startDate); r.startDate = String(r.startDate).slice(0, 10); }
      if (r.repeat) { r.repeat.interval = r.repeat.interval || 1; r.repeat.byDays = r.repeat.byDays || []; r.repeat.byMonths = r.repeat.byMonths || []; r.repeat.exDates = r.repeat.exDates || []; if (r.repeat.until === undefined) r.repeat.until = null; if (r.repeat.count === undefined) r.repeat.count = null; }
    },
    validate: function (r) {
      requireText(r.title, 'title');
      if (!isInt(r.priority) || r.priority < 0 || r.priority > 3) fail('priority 应为 0（无）到 3（高）');
    },
    onRemove: function (t) { S.tasks = S.tasks.filter(function (x) { return x.parentId !== t.id; }); },
  };

  // ---- 清单 ----
  var listSpec = {
    build: function (o) {
      var r = { id: o.id || uuid(), name: o.name, colorIndex: o.colorIndex !== undefined ? o.colorIndex : 1, sort: o.sort || 0 };
      listSpec.normalize(r);
      return r;
    },
    normalize: function (r) { r.colorIndex = color(r.colorIndex); },
    validate: function (r) { requireText(r.name, 'name'); },
    onRemove: function (l) { S.tasks.forEach(function (t) { if (t.listId === l.id) t.listId = null; }); },
  };

  var courses = collection('课程', function () { return S.courses; }, courseSpec);
  var events = collection('日程', function () { return S.events; }, eventSpec);
  var tasks = collection('待办', function () { return S.tasks; }, taskSpec);
  var lists = collection('清单', function () { return S.lists; }, listSpec);
  var exceptions = collection('调课记录', function () { return S.exceptions; }, {
    build: function () { return fail('请使用 mc.courses.cancel / reschedule 创建调课记录'); },
    normalize: function () {}, validate: function () {},
  });
  exceptions.create = function () { fail('请使用 mc.courses.cancel / reschedule 创建调课记录'); };

  function findCourse(id) {
    var c = S.courses.filter(function (x) { return x.id === idOf(id); })[0];
    if (!c) fail('课程不存在：' + idOf(id));
    return c;
  }
  function exceptionFor(courseId, week) {
    return S.exceptions.filter(function (e) { return e.courseId === courseId && e.week === week; })[0];
  }
  function putException(e) {
    var old = exceptionFor(e.courseId, e.week);
    if (old) { e.id = old.id; S.exceptions[S.exceptions.indexOf(old)] = e; } else S.exceptions.push(e);
    return clone(e);
  }
  courses.cancel = function (course, week) {
    var c = findCourse(course);
    if (!isInt(week)) fail('week 应为整数');
    if (c.weeks.indexOf(week) < 0) fail('「' + c.name + '」第 ' + week + ' 周没有课，无法停课');
    return putException({ id: uuid(), courseId: c.id, week: week, type: 'CANCEL', newWeek: null, newDayOfWeek: null, newStartPeriod: null, newEndPeriod: null, newRoom: null });
  };
  courses.reschedule = function (course, week, to) {
    var c = findCourse(course);
    to = to || {};
    if (c.weeks.indexOf(week) < 0) fail('「' + c.name + '」第 ' + week + ' 周没有课，无法调课');
    var e = {
      id: uuid(), courseId: c.id, week: week, type: 'RESCHEDULE',
      newWeek: to.week !== undefined ? to.week : null,
      newDayOfWeek: to.dayOfWeek !== undefined ? to.dayOfWeek : (to.day !== undefined ? to.day : null),
      newStartPeriod: to.startPeriod !== undefined ? to.startPeriod : null,
      newEndPeriod: to.endPeriod !== undefined ? to.endPeriod : (to.startPeriod !== undefined && c.endPeriod >= c.startPeriod ? to.startPeriod + (c.endPeriod - c.startPeriod) : null),
      newRoom: to.room !== undefined ? to.room : null,
    };
    if (e.newDayOfWeek !== null && (!isInt(e.newDayOfWeek) || e.newDayOfWeek < 1 || e.newDayOfWeek > 7)) fail('dayOfWeek 应为 1 到 7');
    return putException(e);
  };
  courses.restore = function (course, week) {
    var e = exceptionFor(idOf(course), week);
    if (!e) return false;
    S.exceptions.splice(S.exceptions.indexOf(e), 1);
    return true;
  };
  tasks.complete = function (idOrObj, done) {
    var t = S.tasks.filter(function (x) { return x.id === idOf(idOrObj); })[0];
    if (!t) fail('待办不存在：' + idOf(idOrObj));
    var val = done === undefined ? true : !!done;
    t.done = val; t.doneAt = val ? opts.nowMillis : null;
    return clone(t);
  };

  function fmtArg(a) {
    if (isStr(a)) return a;
    if (a instanceof Error) return a.message;
    try { var j = JSON.stringify(a); return j === undefined ? String(a) : j; } catch (e) { return String(a); }
  }
  function logger(level) {
    return function () {
      if (logs.length >= LOG_MAX) return;
      var text = Array.prototype.slice.call(arguments).map(fmtArg).join(' ');
      if (text.length > 600) text = text.slice(0, 600) + '…';
      logs.push(level === 'log' ? text : '[' + level + '] ' + text);
      if (logs.length === LOG_MAX) logs.push('（日志过多，已省略后续输出）');
    };
  }
  var cons = { log: logger('log'), info: logger('info'), warn: logger('warn'), error: logger('error') };

  var mc = {
    today: opts.today,
    currentWeek: sem ? weekOf(opts.today) : null,
    semester: sem ? clone(sem) : null,
    periods: sem ? clone(sem.periods) : [],
    courses: courses, events: events, tasks: tasks, lists: lists, exceptions: exceptions,
    updateSemester: function (patch) {
      var s = semester();
      ['name', 'week1Monday', 'totalWeeks'].forEach(function (k) { if (patch && patch[k] !== undefined) s[k] = patch[k]; });
      if (patch && patch.periods) s.periods = clone(patch.periods);
      if (!isInt(s.totalWeeks) || s.totalWeeks < 1 || s.totalWeeks > 60) fail('totalWeeks 应为 1 到 60');
      parseDate(s.week1Monday);
      requireText(s.name, 'name');
      return clone(s);
    },
    dateOf: dateOf, weekOf: weekOf, weeks: parseWeeks,
    uuid: uuid, console: cons,
    finish: function (result) {
      var res = null;
      if (result !== undefined) { try { res = JSON.stringify(result); } catch (e) { res = String(result); } }
      return JSON.stringify({
        semesters: S.semesters, courses: S.courses, exceptions: S.exceptions,
        events: S.events, tasks: S.tasks, lists: S.lists,
        logs: logs, result: res === undefined ? null : res,
      });
    },
  };
  return mc;
}
