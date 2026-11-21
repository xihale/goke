/* MintCal landing page: 零依赖原生 JS
 * 1. Hero 可交互薄荷手机预览器（1:1 还原真实截图：课表 / 今日 / 日历 / 待办 + 6 套主题 + 深浅切换）
 * 2. 分段胶囊 / 极客面板 / 复制按钮
 */
(function () {
  'use strict';

  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
  var reduceMotion = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : { matches: false };
  var esc = function (s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  };

  /* ---------- 图标（1:1 对齐真机底栏与顶栏） ---------- */
  var PATHS = {
    today: '<circle cx="12" cy="12" r="4.3" fill="currentColor" stroke="none"/><path d="M12 3.2v2.1M12 18.7v2.1M3.2 12h2.1M18.7 12h2.1M5.8 5.8l1.45 1.45M16.75 16.75l1.45 1.45M18.2 5.8l-1.45 1.45M7.25 16.75L5.8 18.2" stroke-width="2.2"/>',
    timetable: '<rect x="4" y="5" width="16" height="14" rx="2.2" stroke-width="2"/><path d="M9.33 5v14M14.67 5v14" stroke-width="2"/>',
    calendar: '<rect x="4" y="5" width="16" height="14.5" rx="2.4" stroke-width="2"/><path d="M8 3.2v3M16 3.2v3M4 9.3h16" stroke-width="2"/><circle cx="8.3" cy="12.8" r="1.05" fill="currentColor" stroke="none"/><circle cx="12" cy="12.8" r="1.05" fill="currentColor" stroke="none"/><circle cx="15.7" cy="12.8" r="1.05" fill="currentColor" stroke="none"/><circle cx="8.3" cy="16.1" r="1.05" fill="currentColor" stroke="none"/><circle cx="12" cy="16.1" r="1.05" fill="currentColor" stroke="none"/><circle cx="15.7" cy="16.1" r="1.05" fill="currentColor" stroke="none"/>',
    todo: '<circle cx="12" cy="12" r="8.6" fill="currentColor" stroke="none"/><path d="M8.3 12.2l2.45 2.45 5.15-5.15" stroke="var(--a-surface)" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>',
    copy: '<rect x="9" y="8" width="10" height="12" rx="2" stroke-width="1.8"/><path d="M6 16H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v1" stroke-width="1.8"/>',
    plus: '<path d="M12 5.5v13M5.5 12h13" stroke-width="2"/>',
    edit: '<path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z" fill="currentColor" stroke="none"/>',
    gear: '<path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z" fill="currentColor" stroke="none"/>',
    search: '<circle cx="11" cy="11" r="6.5" stroke-width="2"/><path d="M16 16l4.2 4.2" stroke-width="2"/>',
    checklist: '<path d="M4 7.5l2 2 3.5-3.5M4 16l2 2 3.5-3.5M13 8h7M13 16h7" stroke-width="2"/>',
    flag: '<path d="M5 21V4h9l.4 2H20v10h-7l-.4-2H7v7H5z" fill="currentColor" stroke="none"/>',
    check: '<path d="M5 12.5l4.2 4.2L19 7"/>',
    chev: '<path d="M6.5 9.5L12 15l5.5-5.5" stroke-width="2"/>',
    close: '<path d="M6 6l12 12M18 6L6 18"/>'
  };
  function icon(name, cls) {
    return '<svg class="ic ' + (cls || '') + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + PATHS[name] + '</svg>';
  }

  /* ---------- 真实截图对齐数据 ---------- */
  var TODAY = new Date(2026, 10, 21); // 2026-11-21 周六（第 12 周）
  var DOW = ['日', '一', '二', '三', '四', '五', '六'];
  var PERIODS = [
    ['1', '08:30', '09:15', 1],
    ['2', '09:20', '10:05', 2],
    ['3', '10:25', '11:10', 3],
    ['4', '11:15', '12:00', 4],
    ['5', '13:50', '14:35', 6],
    ['6', '14:40', '15:25', 7],
    ['7', '15:30', '16:15', 8],
    ['8', '16:30', '17:15', 9],
    ['9', '17:20', '18:05', 11],
    ['10', '18:30', '19:15', 12]
  ];
  var LAB_ROOMS = ['实4-401', '实4-402', '实4-403', '实4-405', '实4-406', '实4-407', '实4-408', '实4-410'];

  /* 课表课程块：1:1 对齐 shot-timetable.webp */
  var COURSES = [
    { id: 'prob-mon', name: '概率论与数理统计', d: 1, rStart: 1, rSpan: 2, c: 0, roomDisp: '教\n3-204', rooms: ['教3-204'], teacher: '陈老师', weeks: '1–16 周', time: '周一 1–2 节 · 08:30–10:05' },
    { id: 'phys-thu', name: '大学物理(2)', d: 4, rStart: 1, rSpan: 2, c: 2, roomDisp: '教\n1-401', rooms: ['教1-401'], teacher: '林老师', weeks: '1–16 周', time: '周四 1–2 节 · 08:30–10:05' },
    { id: 'prob-fri', name: '概率论与数理统计', d: 5, rStart: 1, rSpan: 2, c: 0, roomDisp: '教\n3-204', rooms: ['教3-204'], teacher: '陈老师', weeks: '1–16 周', time: '周五 1–2 节 · 08:30–10:05' },
    { id: 'eng-tue', name: '工程英语', d: 2, rStart: 3, rSpan: 2, c: 2, roomDisp: '实\n3-611', rooms: ['实3-611'], teacher: '郑老师', weeks: '1–16 周', time: '周二 3–4 节 · 10:25–12:00' },
    { id: 'pe-wed', name: '体育(3)', d: 3, rStart: 3, rSpan: 2, c: 3, roomDisp: '', rooms: ['大学城体育场'], teacher: '黄老师', weeks: '1–16 周', time: '周三 3–4 节 · 10:25–12:00' },
    { id: 'dl-thu', name: '数字逻辑与系统设计', d: 4, rStart: 3, rSpan: 2, c: 4, roomDisp: '教\n5-105', rooms: ['教5-105'], teacher: '周老师', weeks: '1–16 周', time: '周四 3–4 节 · 10:25–12:00' },
    { id: 'lab-mon', name: '大学物理实验', d: 1, rStart: 6, rSpan: 3, c: 3, roomDisp: '实\n4-401\n等 8 处', rooms: LAB_ROOMS, teacher: '吴老师', weeks: '5–16 周', time: '周一 5–7 节 · 13:50–16:15' },
    { id: 'dllab-wed', name: '数字逻辑及系统设计实验', d: 3, rStart: 6, rSpan: 4, c: 4, roomDisp: '工\n1-315', rooms: ['工1-315'], teacher: '周老师', weeks: '5–14 周', time: '周三 5–8 节 · 13:50–17:15' },
    { id: 'pol-tue', name: '形势与政策', d: 2, rStart: 9, rSpan: 3, c: 0, roomDisp: '教\n3-110', rooms: ['教3-110'], teacher: '刘老师', weeks: '9–12 周', time: '周二 8–9 节 · 16:30–18:05' },
    { id: 'art-mon', name: '大学美育(2)', d: 1, rStart: 12, rSpan: 1, c: 1, roomDisp: '教\n3-101', rooms: ['教3-101'], teacher: '苏老师', weeks: '1–16 周', time: '周一 10–11 节 · 18:30–20:05' }
  ];
  function roomText(c) { return c.rooms.length > 2 ? c.rooms[0] + ' 等 ' + c.rooms.length + ' 处' : c.rooms.join('、'); }

  /* 待办与快递：1:1 对齐 shot-today.webp 与 shot-todo.webp */
  var TODOS = [
    { id: 'parcel', parcel: true, title: '4-2-1086', sub: '菜鸟驿站', when: 'parcel' },
    { id: 't1', title: '交物理实验报告', sub: '今天 18:00', time: '18:00', when: 'today' },
    { id: 't2', title: '复习概率论第四章', sub: '今天 20:30', time: '20:30', when: 'today' },
    { id: 't3', title: '考试：数据结构', sub: '11月27日 16:00 · #考试', when: 'later' },
    { id: 't4', title: '考试：概率论与数理统计', sub: '12月11日 16:00 · #考试', when: 'later' },
    { id: 't5', title: '考试：工程英语', sub: '12月25日 11:30 · #考试', when: 'later' },
    { id: 't6', title: '考试：大学物理(2)', sub: '1月13日 16:00 · #考试', when: 'later' },
    { id: 't7', title: '考试：数字逻辑与系统设计', sub: '1月14日 11:30 · #考试', when: 'later' },
    { id: 't8', title: '【菜鸟驿站】您的顺丰包裹已到东门…', sub: '取件码 4-2-1086', when: 'later' },
    { id: 't9', title: '买 2B 铅笔与橡皮', sub: '收集箱', when: 'inbox' }
  ];

  var EXAMPLES = [
    '明天 18:00 交实验报告 !高',
    '周五 14:00 数字逻辑实验预习'
  ];

  /* 日历每日日程：1:1 对齐 shot-calendar.webp */
  var CAL_BY_DOW = {
    1: [
      { t: '08:30', title: '概率论与数理统计', place: '教3-204', c: 0 },
      { t: '13:50', title: '大学物理实验', place: '实4-401 等 8 处', c: 3 },
      { t: '18:30', title: '大学美育(2)', place: '教3-101', c: 1 }
    ],
    2: [
      { t: '10:25', title: '工程英语', place: '实3-611', c: 2 },
      { t: '16:30', title: '形势与政策', place: '教3-110', c: 0 }
    ],
    3: [
      { t: '10:25', title: '体育(3)', place: '大学城体育场', c: 3 },
      { t: '13:50', title: '数字逻辑及系统设计实验', place: '工1-315', c: 4 }
    ],
    4: [
      { t: '08:30', title: '大学物理(2)', place: '教1-401', c: 2 },
      { t: '10:25', title: '数字逻辑与系统设计', place: '教5-105', c: 4 }
    ],
    5: [
      { t: '08:30', title: '概率论与数理统计', place: '教3-204', c: 0 }
    ]
  };
  function getDayEvents(d) {
    var w = new Date(2026, 10, d).getDay();
    var base = (CAL_BY_DOW[w] || []).slice();
    if (d === 27) base.push({ t: '16:00', title: '考试：数据结构', place: '教1-201 · #考试', c: 4 });
    return base;
  }

  /* ---------- 自然语言解析 ---------- */
  function fmtDate(d) {
    var diff = Math.round((d - TODAY) / 86400000);
    if (diff === 0) return '今天';
    if (diff === 1) return '明天';
    return (d.getMonth() + 1) + '月' + d.getDate() + '日 周' + DOW[d.getDay()];
  }
  function resolveDate(raw) {
    var d = new Date(TODAY);
    if (raw === '今天') return d;
    if (raw === '明天') { d.setDate(d.getDate() + 1); return d; }
    if (raw === '后天') { d.setDate(d.getDate() + 2); return d; }
    var ch = raw.charAt(raw.length - 1);
    var target = ch === '日' || ch === '天' ? 0 : '一二三四五六'.indexOf(ch) + 1;
    var delta = (target - TODAY.getDay() + 7) % 7;
    d.setDate(d.getDate() + (delta === 0 ? 7 : delta));
    return d;
  }
  function parseNL(text) {
    var rules = [
      ['pri', /[!！](高|中|低)/],
      ['date', /下?(?:周|星期)[一二三四五六日天]|今天|明天|后天/],
      ['time', /\d{1,2}[:：]\d{2}/]
    ];
    var toks = [];
    rules.forEach(function (r) {
      var m = r[1].exec(text);
      if (m) toks.push({ type: r[0], start: m.index, end: m.index + m[0].length, raw: m[0] });
    });
    toks.sort(function (a, b) { return a.start - b.start; });
    var html = '', pos = 0, title = '';
    toks.forEach(function (t) {
      html += esc(text.slice(pos, t.start)) + '<mark class="ap-hl ap-hl-' + t.type + '">' + esc(t.raw) + '</mark>';
      title += text.slice(pos, t.start);
      pos = t.end;
    });
    html += esc(text.slice(pos));
    title = (title + text.slice(pos)).replace(/\s+/g, ' ').trim();
    var res = { html: html, title: title, pri: null, time: null, date: null, sub: '', when: 'today' };
    toks.forEach(function (t) {
      if (t.type === 'pri') res.pri = { '高': 'high', '中': 'mid', '低': 'low' }[t.raw.slice(1)];
      if (t.type === 'time') res.time = t.raw.replace('：', ':').replace(/^(\d):/, '0$1:');
      if (t.type === 'date') res.date = resolveDate(t.raw);
    });
    if (!res.date && res.time) res.date = new Date(TODAY);
    if (res.date) {
      res.sub = fmtDate(res.date) + (res.time ? ' ' + res.time : '');
      res.when = res.date.getTime() === TODAY.getTime() ? 'today' : 'later';
    } else {
      res.sub = '今天';
    }
    return res;
  }

  /* ---------- 行与卡片组件 ---------- */
  function checkBtn(t, done) {
    var label = t.parcel ? '标记已取件 ' + t.title : '完成 ' + t.title;
    return '<button type="button" class="ap-check" role="checkbox" aria-checked="' + (done ? 'true' : 'false') + '" aria-label="' + esc(label) + '" data-act="check" data-id="' + t.id + '">' + icon('check') + '</button>';
  }
  function renderRow(t, done, useTimeOnly) {
    var cls = 'ap-row' + (done ? ' is-done' : '') + (t.isNew ? ' is-new' : '');
    if (t.parcel) {
      return '<div class="' + cls + '" data-row="' + t.id + '">' + checkBtn(t, done) +
        '<div class="ap-row-main"><button type="button" class="ap-code" data-act="copy" data-code="' + t.title + '" aria-label="复制取件码 ' + t.title + '">' + t.title + '</button>' +
        '<span class="ap-row-sub">' + esc(t.sub) + '</span></div>' +
        '<button type="button" class="ap-icon-btn" data-act="copy" data-code="' + t.title + '" aria-label="复制取件码">' + icon('copy') + '</button></div>';
    }
    var subText = useTimeOnly ? (t.time || t.sub) : t.sub;
    return '<div class="' + cls + '" data-row="' + t.id + '">' + checkBtn(t, done) +
      '<div class="ap-row-main"><span class="ap-row-title">' + esc(t.title) + '</span>' +
      (subText ? '<span class="ap-row-sub">' + esc(subText) + '</span>' : '') + '</div></div>';
  }
  function calcTodaySummary(st) {
    var items = st.todos.filter(function (t) { return t.when === 'today'; });
    var done = items.filter(function (t) { return st.done.has(t.id); }).length;
    var rem = items.length - done;
    return {
      done: done,
      total: items.length,
      rem: rem,
      pct: items.length ? Math.round(done / items.length * 100) : 0,
      headline: '0 节课 · ' + rem + ' 项待办'
    };
  }

  /* ---------- 四个主视图 ---------- */
  var VIEWS = {
    timetable: {
      top: function () {
        return '<div class="ap-top-text"><div class="ap-title">第 12 周</div><div class="ap-sub">11/16 – 11/22</div></div>' +
          '<div class="ap-acts">' +
          '<button type="button" class="ap-act" data-act="quick-new" aria-label="新建日程">' + icon('plus') + '</button>' +
          '<button type="button" class="ap-act" data-act="edit-tt" aria-label="编辑课表">' + icon('edit') + '</button></div>';
      },
      body: function () {
        var weekHead = '<div></div>';
        var days = ['一', '二', '三', '四', '五', '六', '日'];
        for (var i = 0; i < 7; i++) {
          var isSat = i === 5;
          weekHead += '<div class="ap-tt-day' + (isSat ? ' is-today' : '') + '"><b>' + days[i] + '</b><span>11/' + (16 + i) + '</span></div>';
        }
        var slots = PERIODS.map(function (p) {
          return '<div class="ap-tt-slot" style="grid-row:' + p[3] + '"><b>' + p[0] + '</b><span>' + p[1] + '</span><span>' + p[2] + '</span></div>';
        }).join('');
        var breaks =
          '<div class="ap-tt-break" style="grid-row:5" aria-hidden="true"></div>' +
          '<div class="ap-tt-break" style="grid-row:10" aria-hidden="true"></div>';
        var blocks = COURSES.map(function (c) {
          var roomHtml = c.roomDisp ? '<span class="ap-course-room">' + esc(c.roomDisp).replace(/\n/g, '<br>') + '</span>' : '';
          return '<button type="button" class="ap-course" data-act="course" data-id="' + c.id + '" aria-label="' + esc(c.name + '，' + c.time + '，' + roomText(c)) + '" style="grid-column:' + (c.d + 1) + ';grid-row:' + c.rStart + ' / span ' + c.rSpan + ';--bg:var(--c' + c.c + 'b);--fg:var(--c' + c.c + 'f)">' +
            '<span class="ap-course-name">' + esc(c.name) + '</span>' + roomHtml + '</button>';
        }).join('');
        return '<div class="ap-tt-wrap"><div class="ap-tt-week">' + weekHead + '</div><div class="ap-tt-grid">' + slots + breaks + blocks + '</div></div>';
      }
    },

    today: {
      top: function () {
        return '<div class="ap-top-text"><div class="ap-title">11月21日 周六</div><div class="ap-sub ap-sub--sans">第 12 周</div></div>' +
          '<div class="ap-acts"><button type="button" class="ap-act" data-act="gear" aria-label="打开设置">' + icon('gear') + '</button></div>';
      },
      body: function (st) {
        var s = calcTodaySummary(st);
        var parcels = st.todos.filter(function (t) { return t.when === 'parcel'; });
        var tasks = st.todos.filter(function (t) { return t.when === 'today'; });
        return '<div class="ap-card ap-sum"><div class="ap-sum-text"><b data-sum-head>' + esc(s.headline) + '</b><span>下一节 11月23日 08:30 概率论与数理统计 教3-204</span></div>' +
          '<div class="ap-ring" data-ring style="--p:' + s.pct + '%"><span data-ring-num>' + s.done + '/' + s.total + '</span></div></div>' +
          '<div class="ap-sec-head"><span>待取快递</span><span class="ap-num">' + parcels.length + '</span></div>' +
          parcels.map(function (t) { return renderRow(t, st.done.has(t.id), true); }).join('') +
          '<div class="ap-sec-head"><span>今日时间线 · ' + tasks.length + '</span></div>' +
          '<div data-today-tasks>' + tasks.map(function (t) { return renderRow(t, st.done.has(t.id), true); }).join('') + '</div>';
      }
    },

    calendar: {
      topCenter: true,
      top: function () {
        return '<button type="button" class="ap-title-btn" data-act="month-tip"><span>2026年11月</span>' + icon('chev') + '</button>' +
          '<div class="ap-cal-switch" role="group" aria-label="视图切换">' +
          '<button type="button" class="is-on" aria-pressed="true">月</button>' +
          '<button type="button" data-act="tab" data-tab="timetable" aria-pressed="false">周</button></div>';
      },
      body: function (st) {
        var cells = '';
        for (var i = 0; i < 42; i++) {
          var d = new Date(2026, 9, 26 + i), inM = d.getMonth() === 10, n = d.getDate(), dow = d.getDay();
          var isWorkday = dow >= 1 && dow <= 5;
          if (!inM) {
            cells += '<span class="ap-cal-cell is-dim"><b>' + n + '</b><span class="ap-cal-marks">' + (isWorkday ? '<i class="ap-bar"></i>' : '') + '</span></span>';
            continue;
          }
          var marks = '';
          if (n === 21) {
            marks = '<i class="ap-ringdot"></i><i class="ap-ringdot"></i>';
          } else if (n === 27) {
            marks = '<i class="ap-bar"></i><i class="ap-ringdot"></i>';
          } else if (isWorkday) {
            marks = '<i class="ap-bar"></i>';
          }
          var cls = 'ap-cal-cell' + (n === 21 ? ' is-today' : '') + (n === st.sel ? ' is-sel' : '');
          cells += '<button type="button" class="' + cls + '" data-act="day" data-d="' + n + '" aria-pressed="' + (n === st.sel ? 'true' : 'false') + '" aria-label="11月' + n + '日">' +
            '<b>' + n + '</b><span class="ap-cal-marks">' + marks + '</span></button>';
        }
        var wk = '一二三四五六日'.split('').map(function (w) { return '<span>' + w + '</span>'; }).join('');
        return '<div class="ap-cal-card"><div class="ap-cal-wk">' + wk + '</div><div class="ap-cal-grid">' + cells + '</div></div>' +
          '<div data-day-list>' + renderDayDetail(st.sel, st) + '</div>';
      }
    },

    todo: {
      top: function () {
        return '<div class="ap-top-text"><div class="ap-title">待办</div></div>' +
          '<div class="ap-acts">' +
          '<button type="button" class="ap-act" data-act="focus-nl" aria-label="搜索或录入待办">' + icon('search') + '</button>' +
          '<button type="button" class="ap-act" data-act="multi-tip" aria-label="批量多选">' + icon('checklist') + '</button></div>';
      },
      body: function (st) {
        return '<div class="ap-todo-toolbar">' +
          '<button type="button" class="ap-pri-pill" data-act="pri-tip">' + icon('flag') + '<span>优先级</span>' + icon('chev') + '</button>' +
          '</div>' +
          '<div class="ap-nl" data-nl>' +
          '<div class="ap-nl-field"><div class="ap-nl-mirror" data-mirror aria-hidden="true"></div>' +
          '<input class="ap-nl-input" data-nl-input type="text" autocomplete="off" spellcheck="false" maxlength="38" placeholder="输入自然语言，如：明天 18:00 交实验报告 !高" aria-label="自然语言添加待办"></div>' +
          '<div class="ap-nl-preview" data-preview></div>' +
          '<div class="ap-chips" aria-label="点击填入示例">' +
          EXAMPLES.map(function (e, i) { return '<button type="button" class="ap-chip" data-act="example" data-i="' + i + '">' + esc(e) + '</button>'; }).join('') +
          '</div></div>' +
          '<div data-todo-list>' + renderTodoGroups(st) + '</div>';
      }
    }
  };

  function renderDayDetail(d, st) {
    var ev = getDayEvents(d);
    var isToday = d === 21;
    var todayTasks = st.todos.filter(function (t) { return t.when === 'today'; });
    var doneCnt = isToday ? todayTasks.filter(function (t) { return st.done.has(t.id); }).length : 0;
    var remCnt = isToday ? (todayTasks.length - doneCnt) : 0;
    var head =
      '<div class="ap-day-head">' +
      '<div class="ap-day-ttl"><b>11月' + d + '日 周' + DOW[new Date(2026, 10, d).getDay()] + '</b>' +
      '<span>' + ev.length + ' 项日程，' + remCnt + ' 项待办未完成，' + doneCnt + ' 项已完成</span></div>' +
      '<div class="ap-day-acts">' +
      '<button type="button" class="ap-back-today" data-act="today-jump">回今天</button>' +
      '<button type="button" class="ap-act" data-act="quick-new" aria-label="新建日程">' + icon('plus') + '</button></div></div>';
    if (isToday) {
      return head + todayTasks.map(function (t) { return renderRow(t, st.done.has(t.id), true); }).join('');
    }
    if (!ev.length) {
      return head + '<div class="ap-sec-head"><span>当天无课程安排，可点右上角 + 新建日程</span></div>';
    }
    return head + ev.map(function (e) {
      return '<div class="ap-cal-event" style="--bg:var(--c' + e.c + 'b);--fg:var(--c' + e.c + 'f)">' +
        '<span class="ap-cal-time">' + e.t + '</span>' +
        '<div class="ap-cal-info"><b>' + esc(e.title) + '</b><span>' + esc(e.place) + '</span></div></div>';
    }).join('');
  }

  function renderTodoGroups(st) {
    var groups = [
      ['today', '今天'],
      ['later', '之后'],
      ['inbox', '收集箱']
    ];
    return groups.map(function (g) {
      var key = g[0], label = g[1];
      var items = st.todos.filter(function (t) { return t.when === key; });
      var open = !st.collapsed[key];
      return '<div class="ap-grp">' +
        '<button type="button" class="ap-grp-head" data-act="toggle-grp" data-grp="' + key + '" aria-expanded="' + (open ? 'true' : 'false') + '">' +
        '<span class="ap-grp-l">' + icon('chev') + '<span>' + label + '</span></span>' +
        '<span class="ap-grp-r"><span class="ap-grp-cnt">' + items.length + '</span>' + icon('plus') + '</span></button>' +
        '<div class="ap-grp-list"' + (open ? '' : ' hidden') + '>' +
        items.map(function (t) { return renderRow(t, st.done.has(t.id), false); }).join('') +
        '</div></div>';
    }).join('');
  }

  /* ---------- 挂载 Hero 手机应用 ---------- */
  var NAV = [['today', '今日'], ['timetable', '课表'], ['calendar', '日历'], ['todo', '待办']];

  function mountApp(root, opts) {
    var st = {
      tab: opts.tab || 'timetable',
      sel: 23, // 默认选中 11月23日 周一（与 shot-calendar.webp 一致展示 3 门粉彩课程卡）
      done: new Set(),
      collapsed: {},
      todos: TODOS.map(function (t) { return Object.assign({}, t); }),
      uid: 0,
      typer: null,
      toastTimer: null
    };

    var navHtml = '<nav class="ap-nav" role="tablist" aria-label="底部导航" style="--i:' + NAV.map(function (n) { return n[0]; }).indexOf(st.tab) + '"><span class="ap-nav-thumb" aria-hidden="true"></span>' +
      NAV.map(function (n) {
        var on = n[0] === st.tab;
        return '<button type="button" role="tab" class="ap-nav-item' + (on ? ' is-on' : '') + '" aria-selected="' + on + '" tabindex="' + (on ? 0 : -1) + '" data-act="tab" data-tab="' + n[0] + '">' + icon(n[0]) + '<span>' + n[1] + '</span></button>';
      }).join('') + '</nav>';

    root.classList.add('ap', 'ap--nav');
    root.innerHTML =
      '<div class="ap-status" aria-hidden="true"><span>10:18</span><span class="ap-status-r"><span>5G</span><svg viewBox="0 0 18 12" width="1.05em" height=".72em"><path d="M1 11V8M5.5 11V6M10 11V3.5M14.5 11V1" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg><svg viewBox="0 0 26 12" width="1.5em" height=".72em"><rect x="1" y="1.5" width="21" height="9" rx="2.5" fill="none" stroke="currentColor" stroke-width="1.5"/><rect x="3" y="3.5" width="11" height="5" rx="1.2" fill="currentColor"/><rect x="23.2" y="4" width="1.8" height="4" rx=".9" fill="currentColor"/></svg><span>40%</span></span></div>' +
      '<header class="ap-top" data-top></header>' +
      '<div class="ap-body" data-body role="tabpanel" tabindex="-1"></div>' +
      navHtml +
      '<button type="button" class="ap-fab" data-act="fab" aria-label="新建" hidden>' + icon('plus') + '</button>' +
      '<div class="ap-scrim" data-act="sheet-close"></div><section class="ap-sheet" data-sheet role="dialog" aria-label="课程详情" aria-hidden="true"></section>' +
      '<div class="ap-toast" data-toast role="status" aria-live="polite"></div>';

    var topBar = $('[data-top]', root), body = $('[data-body]', root), sheet = $('[data-sheet]', root), toast = $('[data-toast]', root), fab = $('.ap-fab', root);

    function say(msg) {
      toast.textContent = msg;
      toast.classList.add('is-on');
      clearTimeout(st.toastTimer);
      st.toastTimer = setTimeout(function () { toast.classList.remove('is-on'); }, 1900);
    }

    function render(animate) {
      var v = VIEWS[st.tab];
      topBar.className = 'ap-top' + (v.topCenter ? ' ap-top--center' : '');
      topBar.innerHTML = v.top(st);
      body.innerHTML = v.body(st);
      body.scrollTop = 0;
      body.classList.remove('is-in');
      if (animate && !reduceMotion.matches) { void body.offsetWidth; body.classList.add('is-in'); }
      /* 今日 / 日历 / 待办 均展示右下角薄荷圆底 + FAB（与真机截图 1:1 一致） */
      if (fab) fab.hidden = st.tab === 'timetable';
      if (st.tab === 'todo') bindNL();
    }

    function setTab(tab, focus) {
      if (tab === st.tab) return;
      closeSheet();
      st.tab = tab;
      var idx = NAV.map(function (n) { return n[0]; }).indexOf(tab);
      var nav = $('.ap-nav', root);
      nav.style.setProperty('--i', idx);
      $$('.ap-nav-item', nav).forEach(function (b, i) {
        var on = i === idx;
        b.classList.toggle('is-on', on);
        b.setAttribute('aria-selected', on);
        b.tabIndex = on ? 0 : -1;
        if (on && focus) b.focus();
      });
      render(true);
    }

    function openSheet(id) {
      var c = COURSES.filter(function (x) { return x.id === id; })[0];
      if (!c) return;
      var multi = c.rooms.length > 2;
      sheet.innerHTML =
        '<span class="ap-grab" aria-hidden="true"></span>' +
        '<div class="ap-sheet-head"><span class="ap-swatch" style="--bg:var(--c' + c.c + 'b);--fg:var(--c' + c.c + 'f)"></span>' +
        '<div class="ap-sheet-ttl"><b>' + esc(c.name) + '</b><span>' + c.time + '</span></div>' +
        '<button type="button" class="ap-icon-btn" data-act="sheet-close" aria-label="关闭详情">' + icon('close') + '</button></div>' +
        '<div class="ap-vrow"><span>周次</span><b>' + c.weeks + '</b></div>' +
        '<div class="ap-vrow"><span>教师</span><b>' + c.teacher + '</b></div>' +
        (multi
          ? '<button type="button" class="ap-vrow ap-vrow-btn is-open" data-act="rooms" aria-expanded="true"><span>实验机房（点击折叠）</span><b>' + esc(roomText(c)) + icon('chev', 'ap-chev') + '</b></button>' +
            '<div class="ap-rooms" data-rooms>' + c.rooms.map(function (r) { return '<span>' + r + '</span>'; }).join('') + '</div>'
          : '<div class="ap-vrow"><span>上课地点</span><b>' + esc(c.rooms[0]) + '</b></div>');
      root.classList.add('has-sheet');
      sheet.setAttribute('aria-hidden', 'false');
      var closeBtn = $('[data-act="sheet-close"]', sheet);
      if (closeBtn) closeBtn.focus({ preventScroll: true });
      st.lastCourse = id;
    }

    function closeSheet() {
      if (!root.classList.contains('has-sheet')) return;
      root.classList.remove('has-sheet');
      sheet.setAttribute('aria-hidden', 'true');
      var back = st.lastCourse && $('.ap-course[data-id="' + st.lastCourse + '"]', root);
      if (back) back.focus({ preventScroll: true });
    }

    function refreshTodaySummary() {
      var head = $('[data-sum-head]', root);
      if (!head) return;
      var s = calcTodaySummary(st);
      head.textContent = s.headline;
      $('[data-ring]', root).style.setProperty('--p', s.pct + '%');
      $('[data-ring-num]', root).textContent = s.done + '/' + s.total;
    }

    /* 待办自然语言录入 */
    function bindNL() {
      var input = $('[data-nl-input]', root);
      var mirror = $('[data-mirror]', root);
      var preview = $('[data-preview]', root);
      if (!input) return;
      var cur = null;
      function update() {
        var v = input.value;
        if (!v.trim()) { mirror.innerHTML = ''; preview.innerHTML = ''; preview.classList.remove('is-on'); cur = null; return; }
        cur = parseNL(v);
        mirror.innerHTML = cur.html;
        if (!cur.title) { preview.innerHTML = ''; preview.classList.remove('is-on'); cur = null; return; }
        var tags = '<span class="ap-hl ap-hl-date">' + esc(cur.sub) + '</span>';
        if (cur.pri) tags += '<span class="ap-hl ap-hl-pri">' + { high: '高优先级', mid: '中优先级', low: '低优先级' }[cur.pri] + '</span>';
        preview.innerHTML = '<div class="ap-pv-main"><span class="ap-pv-title">' + esc(cur.title) + '</span><span class="ap-pv-tags">' + tags + '</span></div><button type="button" class="ap-btn-sm" data-act="nl-add">添加</button>';
        preview.classList.add('is-on');
      }
      input.addEventListener('input', update);
      input.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && cur) { e.preventDefault(); add(); }
      });
      st.nlUpdate = update;
      st.nlInput = input;
      st.nlAdd = add;
      function add() {
        if (!cur) return;
        var t = { id: 'u' + (++st.uid), title: cur.title, sub: cur.sub, time: cur.time || '全天', when: cur.when, isNew: true };
        st.todos.unshift(t);
        st.collapsed[cur.when] = false;
        input.value = '';
        update();
        $('[data-todo-list]', root).innerHTML = renderTodoGroups(st);
        t.isNew = false;
        say('已添加待办：' + t.title);
      }
    }

    function typeIn(text) {
      var input = st.nlInput;
      if (!input) return;
      clearInterval(st.typer);
      if (reduceMotion.matches) { input.value = text; st.nlUpdate(); return; }
      var i = 0;
      input.value = '';
      st.typer = setInterval(function () {
        i++;
        input.value = text.slice(0, i);
        st.nlUpdate();
        if (i >= text.length) clearInterval(st.typer);
      }, 28);
    }

    function selectDay(d) {
      st.sel = d;
      $$('.ap-cal-cell[data-act="day"]', root).forEach(function (b) {
        var on = +b.dataset.d === st.sel;
        b.classList.toggle('is-sel', on);
        b.setAttribute('aria-pressed', on);
      });
      var dl = $('[data-day-list]', root);
      if (!dl) return;
      dl.innerHTML = renderDayDetail(st.sel, st);
      dl.classList.remove('is-in');
      if (!reduceMotion.matches) { void dl.offsetWidth; dl.classList.add('is-in'); }
    }

    /* 事件委托 */
    root.addEventListener('click', function (e) {
      var el = e.target.closest('[data-act]');
      if (!el || !root.contains(el)) return;
      var act = el.dataset.act;
      switch (act) {
        case 'tab': setTab(el.dataset.tab); break;
        case 'course': openSheet(el.dataset.id); break;
        case 'sheet-close': closeSheet(); break;
        case 'rooms':
          var open = el.getAttribute('aria-expanded') !== 'true';
          el.setAttribute('aria-expanded', open);
          el.classList.toggle('is-open', open);
          $('[data-rooms]', root).hidden = !open;
          break;
        case 'copy':
          copyText(el.dataset.code).then(function (ok) {
            say(ok ? '已复制取件码 ' + el.dataset.code : '已选中取件码 ' + el.dataset.code);
          });
          break;
        case 'check':
          var id = el.dataset.id;
          var done = !st.done.has(id);
          if (done) st.done.add(id); else st.done.delete(id);
          $$('.ap-row[data-row="' + id + '"]', root).forEach(function (r) {
            r.classList.toggle('is-done', done);
            var cb = $('.ap-check', r);
            if (cb) cb.setAttribute('aria-checked', done);
          });
          refreshTodaySummary();
          if (st.tab === 'calendar' && st.sel === 21) {
            var dl = $('[data-day-list]', root);
            if (dl) dl.innerHTML = renderDayDetail(21, st);
          }
          break;
        case 'day':
          selectDay(+el.dataset.d);
          break;
        case 'today-jump':
          selectDay(21);
          say('已回到今天（11月21日）');
          break;
        case 'toggle-grp':
          var grp = el.dataset.grp;
          st.collapsed[grp] = !st.collapsed[grp];
          $('[data-todo-list]', root).innerHTML = renderTodoGroups(st);
          break;
        case 'example':
          typeIn(EXAMPLES[+el.dataset.i]);
          break;
        case 'nl-add':
          st.nlAdd && st.nlAdd();
          break;
        case 'fab':
        case 'quick-new':
          if (st.tab !== 'todo') {
            setTab('todo');
            setTimeout(function () { if (st.nlInput) { st.nlInput.focus(); typeIn(EXAMPLES[0]); } }, 60);
          } else if (st.nlInput) {
            st.nlInput.focus();
            typeIn(EXAMPLES[0]);
          }
          break;
        case 'focus-nl':
          if (st.nlInput) st.nlInput.focus();
          break;
        case 'edit-tt':
          say('长按课程块可拖拽换时段，点实验课可展开 8 个机房');
          break;
        case 'gear':
          say('支持教务自动同步、四校区作息与 6 套植物主题');
          break;
        case 'month-tip':
          say('2026年11月 · 第 10–14 教学周');
          break;
        case 'pri-tip':
          say('已按优先级与截止时间排序');
          break;
        case 'multi-tip':
          say('长按任意待办可进入多选批量完成或改期');
          break;
      }
    });

    root.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') closeSheet();
      var nav = e.target.closest && e.target.closest('.ap-nav');
      if (nav && (e.key === 'ArrowRight' || e.key === 'ArrowLeft')) {
        var cur = NAV.map(function (n) { return n[0]; }).indexOf(st.tab);
        var nx = (cur + (e.key === 'ArrowRight' ? 1 : NAV.length - 1)) % NAV.length;
        setTab(NAV[nx][0], true);
        e.preventDefault();
      }
    });

    render(false);
    return { setTab: setTab, root: root };
  }

  function copyText(text) {
    function fallback() {
      try {
        var ta = document.createElement('textarea');
        ta.value = text;
        ta.setAttribute('readonly', '');
        ta.style.cssText = 'position:fixed;top:0;left:0;opacity:0';
        document.body.appendChild(ta);
        ta.select();
        var ok = document.execCommand('copy');
        ta.remove();
        return ok;
      } catch (err) { return false; }
    }
    if (navigator.clipboard && navigator.clipboard.writeText && window.isSecureContext) {
      return navigator.clipboard.writeText(text).then(function () { return true; }, function () { return fallback(); });
    }
    return Promise.resolve(fallback());
  }

  /* ---------- 分段胶囊（页面级，带键盘支持） ---------- */
  function initTabs(list, onSelect) {
    var tabs = $$('[role="tab"]', list);
    function select(i, focus) {
      list.style.setProperty('--i', i);
      tabs.forEach(function (t, k) {
        var on = k === i;
        t.setAttribute('aria-selected', on);
        t.tabIndex = on ? 0 : -1;
        t.classList.toggle('is-on', on);
        if (on && focus) t.focus();
      });
      onSelect(i, tabs[i]);
    }
    tabs.forEach(function (t, i) {
      t.addEventListener('click', function () { select(i); });
      t.addEventListener('keydown', function (e) {
        var k = null;
        if (e.key === 'ArrowRight') k = (i + 1) % tabs.length;
        else if (e.key === 'ArrowLeft') k = (i + tabs.length - 1) % tabs.length;
        else if (e.key === 'Home') k = 0;
        else if (e.key === 'End') k = tabs.length - 1;
        if (k !== null) { e.preventDefault(); select(k, true); }
      });
    });
    var start = tabs.findIndex ? tabs.findIndex(function (t) { return t.getAttribute('aria-selected') === 'true'; }) : 0;
    list.style.setProperty('--i', Math.max(0, start));
  }

  /* ---------- 启动 ---------- */
  function boot() {
    var heroRoot = $('#heroApp');
    var heroStage = $('#heroStage');
    if (heroRoot) {
      mountApp(heroRoot, { tab: 'timetable' });
      var bar = $('#themeBar');
      var nameOut = $('#themeName');
      var modeBtn = $('#modeToggle');
      var names = { mint: '薄荷', peach: '蜜桃', lavender: '薰衣草', sky: '天空', rose: '玫瑰', lemon: '柠檬' };
      $$('[data-preset]', bar).forEach(function (b) {
        b.addEventListener('click', function () {
          heroRoot.dataset.themePreset = b.dataset.preset;
          if (heroStage) heroStage.dataset.themePreset = b.dataset.preset;
          $$('[data-preset]', bar).forEach(function (x) { x.setAttribute('aria-pressed', x === b); });
          nameOut.textContent = names[b.dataset.preset];
        });
      });
      modeBtn.addEventListener('click', function () {
        var dark = heroRoot.dataset.colorMode !== 'dark';
        heroRoot.dataset.colorMode = dark ? 'dark' : 'light';
        if (heroStage) heroStage.dataset.colorMode = dark ? 'dark' : 'light';
        modeBtn.setAttribute('aria-pressed', dark);
        modeBtn.setAttribute('aria-label', dark ? '切换到浅色模式' : '切换到深色模式');
        $('.mode-label', modeBtn).textContent = dark ? '深色' : '浅色';
      });
    }

    /* Band 3 四个视图 Tab */
    var vTabs = $('#viewTabs');
    if (vTabs) {
      var panels = $$('.view-panel');
      initTabs(vTabs, function (i) {
        panels.forEach(function (p, k) { p.hidden = k !== i; });
      });
    }

    /* Band 5 电脑与 AI Tab + 左侧列表双向联动 */
    var oTabs = $('#openTabs');
    if (oTabs) {
      var oPanels = $$('.open-panel');
      var oItems = $$('#openList .open-item');
      var oTabBtns = $$('[role="tab"]', oTabs);
      initTabs(oTabs, function (i) {
        oPanels.forEach(function (p, k) { p.hidden = k !== i; });
        oItems.forEach(function (item, k) { item.classList.toggle('is-on', k === i); });
      });
      oItems.forEach(function (item) {
        item.addEventListener('click', function () {
          var idx = +item.dataset.openIdx;
          if (oTabBtns[idx]) oTabBtns[idx].click();
        });
      });
    }

    /* 顶栏滚动浮层与导航高亮 */
    var topbar = $('#topbar');
    if (topbar) {
      var syncBar = function () { topbar.classList.toggle('is-scrolled', window.scrollY > 12); };
      syncBar();
      window.addEventListener('scroll', syncBar, { passive: true });
    }
    var navLinks = $$('.topnav a[href^="#"]');
    if (navLinks.length && 'IntersectionObserver' in window) {
      var secMap = navLinks.map(function (a) { return { a: a, el: $(a.getAttribute('href')) }; }).filter(function (x) { return x.el; });
      var io = new IntersectionObserver(function (entries) {
        entries.forEach(function (en) {
          if (en.isIntersecting) {
            secMap.forEach(function (x) { x.a.classList.toggle('is-active', x.el === en.target); });
          }
        });
      }, { rootMargin: '-32% 0px -56% 0px' });
      secMap.forEach(function (x) { io.observe(x.el); });
    }

    /* 代码块一键复制 */
    $$('[data-copy-target]').forEach(function (btn) {
      var label = $('span', btn);
      var timer;
      btn.addEventListener('click', function () {
        var code = document.getElementById(btn.dataset.copyTarget);
        copyText(code.textContent.replace(/\s+$/, '')).then(function (ok) {
          label.textContent = ok ? '已复制' : '复制失败，请手动选择';
          btn.classList.toggle('is-ok', ok);
          clearTimeout(timer);
          timer = setTimeout(function () { label.textContent = '复制'; btn.classList.remove('is-ok'); }, 2000);
        });
      });
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
