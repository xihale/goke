// 各类编辑表单（课程、日程、待办、清单、学期、改密码）。

import { ctx } from './ctx.js';
import { confirmDialog, h, icon, openDialog, toast } from './dom.js';
import {
  colorSwatches, field, formSheet, group, numberInput, periodOptions, remindSelect, remindValue, segmented, select,
  textarea, textInput,
} from './formkit.js';
import {
  GDUT_CAMPUS_LABELS, GDUT_PERIODS, WeekSets, courseConflicts, currentSemester, effectiveCoursePeriods, newCourse,
  newEvent, newException, newList, newRecurrence, newSemester, newTask, restore, semesterCalendar, subtasks, tombstone,
  touch,
} from './model.js';
import { removeCourse, removeList, removeRecords, removeTask, setCurrentSemester } from './actions.js';
import {
  WEEKDAYS, fmtDate, fmtDateTime, fmtTime, minutesOfDay, mondayOf, parseDate, parseDateTime, parseTime, shortDate, todayNum, weekdayName,
} from './util.js';

const dateInput = (v, attrs = {}) => h('input', { class: 'input', type: 'date', value: v || '', ...attrs });
const timeInput = (v, attrs = {}) => h('input', { class: 'input', type: 'time', value: v || '', ...attrs });
const WEEKDAY_OPTIONS = WEEKDAYS.map((n, i) => [i + 1, n]);

// ---------- 学期 ----------

/** 返回当前学期；没有学期时引导用户先创建，并返回 null。 */
export function requireSemester() {
  const sem = currentSemester(ctx.store.view());
  if (sem) return sem;
  toast('请先创建学期，课程与周次都依赖它');
  openSemesterForm({ create: true });
  return null;
}

export function openSemesterForm({ semester, create = false } = {}) {
  const { store } = ctx;
  const cur = semester || currentSemester(store.view());
  const isNew = create || !semester;
  const name = textInput(isNew ? '' : cur.name, { placeholder: '学期名称', maxLength: 40 });
  const monday = dateInput(isNew ? fmtDate(mondayOf(todayNum())) : cur.week1Monday);
  const weeks = numberInput(isNew ? 20 : cur.totalWeeks, { min: 1, max: 60 });
  const copy = h('input', { type: 'checkbox', checked: !!cur });
  let periods = (isNew && !cur ? GDUT_PERIODS : (cur ? cur.periods : GDUT_PERIODS)).map((p) => ({ ...p }));
  const list = h('div', { class: 'period-list' });
  const renderPeriods = () => {
    list.replaceChildren(...periods.map((p, i) => h('div', { class: 'period-row' },
      h('span', { class: 'period-no' }, `第 ${i + 1} 节`),
      timeInput(p.start, { 'aria-label': `第 ${i + 1} 节开始`, onchange: (e) => { p.start = e.target.value; } }),
      h('span', {}, '–'),
      timeInput(p.end, { 'aria-label': `第 ${i + 1} 节结束`, onchange: (e) => { p.end = e.target.value; } }),
      h('button', { type: 'button', class: 'icon-btn', 'aria-label': '删除这一节', onclick: () => { periods.splice(i, 1); renderPeriods(); } }, icon('x', 18)))));
  };
  renderPeriods();
  const fields = [
    field('学期名称', name),
    field('第 1 周的周一', monday, '自动对齐到周一'),
    field('总周数', weeks),
    group('节次时间', h('div', {}, list, h('div', { class: 'row-actions left' },
      h('button', { type: 'button', class: 'btn tonal small', onclick: () => { periods.push({ index: periods.length + 1, start: '', end: '' }); renderPeriods(); } }, '添加一节'),
      h('button', { type: 'button', class: 'btn ghost small', onclick: () => { periods = GDUT_PERIODS.map((p) => ({ ...p })); renderPeriods(); } }, '恢复广工默认')))),
  ];
  if (isNew && cur) fields.push(h('label', { class: 'check-row' }, copy, h('span', {}, `复制「${cur.name}」的课程（不含调课与停课）`)));
  formSheet({
    title: isNew ? '新建学期' : '编辑学期',
    fields,
    wide: true,
    onSubmit: async () => {
      const nm = name.value.trim();
      if (!nm) throw new Error('请填写学期名称');
      if (!monday.value) throw new Error('请选择第 1 周的周一');
      const total = Number(weeks.value);
      if (!Number.isInteger(total) || total < 1 || total > 60) throw new Error('总周数需要在 1–60 之间');
      if (!periods.length) throw new Error('至少需要一节课的时间');
      const ps = periods.map((p, i) => {
        const s = parseTime(p.start);
        const e = parseTime(p.end);
        if (Number.isNaN(s) || Number.isNaN(e)) throw new Error(`第 ${i + 1} 节的时间还没填写`);
        if (e <= s) throw new Error(`第 ${i + 1} 节的结束时间要晚于开始时间`);
        return { index: i + 1, start: fmtTime(s), end: fmtTime(e) };
      });
      const fields2 = { name: nm, week1Monday: monday.value, totalWeeks: total, periods: ps };
      if (!isNew) {
        store.put(touch(cur, fields2));
        return false;
      }
      const sem = newSemester({ ...fields2, isCurrent: true });
      const now = Date.now();
      const others = store.view().semesters.filter((s) => s.isCurrent).map((s) => touch(s, { isCurrent: false }, now));
      const copies = cur && copy.checked
        ? store.view().courses.filter((c) => c.semesterId === cur.id).map((c) => newCourse({ ...c, semesterId: sem.id }, now))
        : [];
      store.put(...others, sem, ...copies);
      return false;
    },
    extraActions: !isNew && store.view().semesters.length > 1 && !cur.isCurrent
      ? [h('button', { type: 'button', class: 'btn tonal', onclick: () => { setCurrentSemester(cur); toast('已切换到该学期'); } }, '设为当前学期')]
      : [],
  });
}

// ---------- 课程 ----------

export function openCourseForm({ course, day, period } = {}) {
  const sem = requireSemester();
  if (!sem) return;
  const { store } = ctx;
  const cal = semesterCalendar(sem);
  const c = course;
  const campusOptions = [
    ['', '跟随学期默认'],
    ...Object.entries(GDUT_CAMPUS_LABELS),
  ];
  const campusS = select(campusOptions, c?.campusOverride || '');
  const activePeriods = () => effectiveCoursePeriods({ campusOverride: campusS.value || null }, sem);
  const popts = periodOptions(activePeriods());
  const name = textInput(c?.name || '', { placeholder: '课程名称', maxLength: 60 });
  const teacher = textInput(c?.teacher || '', { maxLength: 40 });
  const room = textInput(c?.room || '', { maxLength: 40 });
  const dayS = select(WEEKDAY_OPTIONS, c?.dayOfWeek ?? day ?? 1);
  const sp = select(popts, c?.startPeriod ?? period ?? 1);
  const ep = select(popts, c?.endPeriod ?? Math.min(period ? period + 1 : 2, popts.length));
  campusS.addEventListener('change', () => {
    const nextOpts = periodOptions(activePeriods());
    const curS = sp.value;
    const curE = ep.value;
    sp.replaceChildren(...nextOpts.map(([v, l]) => h('option', { value: v, selected: String(v) === curS }, l)));
    ep.replaceChildren(...nextOpts.map(([v, l]) => h('option', { value: v, selected: String(v) === curE }, l)));
  });
  const note = textarea(c?.note || '', { rows: 2 });
  const remind = remindSelect(c?.remindMinutes, '默认（按设置）');
  const color = colorSwatches(c?.colorIndex ?? store.view().courses.length % 8);
  const chosen = new Set(c ? c.weeks : WeekSets.all(Math.min(16, sem.totalWeeks)));
  const weekText = textInput(WeekSets.format([...chosen]), { placeholder: '1-16、1,3,5-9、2-16双', 'aria-label': '周次' });
  const grid = h('div', { class: 'week-grid' });
  const conflict = h('p', { class: 'warn', hidden: true });
  const syncWeeks = (fromText) => {
    if (!fromText) weekText.value = WeekSets.format([...chosen]);
    [...grid.children].forEach((b, i) => b.setAttribute('aria-pressed', String(chosen.has(i + 1))));
    checkConflict();
  };
  for (let w = 1; w <= sem.totalWeeks; w++) {
    grid.append(h('button', { type: 'button', class: 'week-chip', 'aria-pressed': String(chosen.has(w)), onclick: () => { if (chosen.has(w)) chosen.delete(w); else chosen.add(w); syncWeeks(false); } }, w));
  }
  weekText.addEventListener('change', () => {
    chosen.clear();
    WeekSets.parse(weekText.value, sem.totalWeeks).filter((w) => w >= 1 && w <= sem.totalWeeks).forEach((w) => chosen.add(w));
    syncWeeks(false);
  });
  const quick = (label, weeks) => h('button', { type: 'button', class: 'btn tonal small', onclick: () => { chosen.clear(); weeks.forEach((w) => chosen.add(w)); syncWeeks(false); } }, label);
  function checkConflict() {
    const cand = { id: c?.id, semesterId: sem.id, dayOfWeek: Number(dayS.value), startPeriod: Number(sp.value), endPeriod: Number(ep.value), weeks: [...chosen] };
    const hit = courseConflicts(cand, store.view().courses);
    conflict.hidden = !hit.length;
    conflict.textContent = hit.length ? `与「${hit.map((x) => x.name).join('」「')}」的上课时间重叠（仍可保存）` : '';
  }
  [dayS, sp, ep].forEach((el) => el.addEventListener('change', checkConflict));
  checkConflict();
  formSheet({
    title: c ? '编辑课程' : '添加课程',
    wide: true,
    fields: [
      field('课程名称', name),
      h('div', { class: 'grid-2' }, field('教师', teacher), field('教室', room)),
      h('div', { class: 'grid-3' }, field('星期', dayS), field('开始节', sp), field('结束节', ep)),
      conflict,
      group('周次', h('div', {}, h('div', { class: 'row-actions left wrap' },
        quick('全部', WeekSets.all(sem.totalWeeks)), quick('单周', WeekSets.odd(sem.totalWeeks)), quick('双周', WeekSets.even(sem.totalWeeks)),
        quick('清空', [])), grid, weekText)),
      group('颜色', color.el),
      field('提醒', remind),
      field('上课校区（作息）', campusS),
      field('备注', note),
    ],
    extraActions: c ? [h('button', { type: 'button', class: 'btn danger-ghost', onclick: async () => { if (await confirmDialog(`删除课程「${c.name}」？`, '删除', { danger: true })) { dlgClose(); removeCourse(c); } } }, '删除')] : [],
    onSubmit: async () => {
      const nm = name.value.trim();
      if (!nm) throw new Error('请填写课程名称');
      const s = Number(sp.value);
      const e = Number(ep.value);
      if (e < s) throw new Error('结束节不能早于开始节');
      if (!chosen.size) throw new Error('请至少选择一个周次');
      const f = {
        name: nm, teacher: teacher.value.trim(), room: room.value.trim(), dayOfWeek: Number(dayS.value), startPeriod: s, endPeriod: e,
        weeks: [...chosen].sort((a, b) => a - b), colorIndex: color.get(), remindMinutes: remindValue(remind), note: note.value,
        campusOverride: campusS.value || null,
      };
      store.put(c ? touch(c, f) : newCourse({ ...f, semesterId: sem.id }));
      return false;
    },
  });
  // 删除按钮需要关闭当前对话框
  function dlgClose() { document.querySelector('dialog.sheet[open]')?.close(); }
}

/** 点击课表里的课程：显示详情与「停课 / 调课 / 编辑 / 删除」。occ 是 expandCourses 的一项。 */
export function openCourseDetail(occ) {
  const { store } = ctx;
  const sem = currentSemester(store.view());
  const cal = semesterCalendar(sem);
  const c = occ.course;
  const ex = store.view().exceptions.find((e) => e.courseId === c.id && e.week === occ.origWeek);
  const cancelled = ex && ex.type === 'CANCEL';
  const campusTag = c.campusOverride && GDUT_CAMPUS_LABELS[c.campusOverride] ? ` · ${GDUT_CAMPUS_LABELS[c.campusOverride]}作息` : '';
  const rows = [
    ['clock', `${weekdayName(occ.day)} 第 ${occ.startPeriod}–${occ.endPeriod} 节　${fmtTime(occ.startMin)}–${fmtTime(occ.endMin)}${campusTag}`],
    ['calendar', `第 ${occ.week} 周（${shortDate(occ.day)}）${occ.rescheduled ? ' · 已调课' : ''}`],
    c.room || occ.room ? ['pin', occ.room || c.room] : null,
    c.teacher ? ['user', c.teacher] : null,
    ['repeat', `上课周次：${WeekSets.format(c.weeks)}`],
    c.note ? ['edit', c.note] : null,
  ].filter(Boolean);
  const body = h('div', { class: 'detail' },
    h('div', { class: `detail-head c${c.colorIndex % 8}` }, h('h2', {}, c.name), h('button', { type: 'button', class: 'icon-btn', 'aria-label': '关闭', onclick: () => d.close() }, icon('x'))),
    h('ul', { class: 'detail-rows' }, rows.map(([ic, text]) => h('li', {}, icon(ic, 18), h('span', {}, text)))),
    h('div', { class: 'detail-actions' },
      h('button', { class: 'btn tonal', type: 'button', onclick: () => { d.close(); openCourseForm({ course: c }); } }, icon('edit', 18), '编辑课程'),
      h('button', { class: 'btn tonal', type: 'button', onclick: () => { d.close(); openRescheduleForm(c, occ.origWeek, ex); } }, icon('repeat', 18), '调课'),
      h('button', { class: 'btn tonal', type: 'button', onclick: () => {
        d.close();
        if (ex) store.put(tombstone(ex)); // 恢复：删除异常
        else store.put(newException({ courseId: c.id, week: occ.origWeek, type: 'CANCEL' }));
        toast(ex ? '已恢复上课' : `第 ${occ.origWeek} 周已停课`);
      } }, icon('sun', 18), ex ? '恢复上课' : '本周停课'),
      h('button', { class: 'btn danger-ghost', type: 'button', onclick: async () => { if (await confirmDialog(`删除课程「${c.name}」的所有周次？`, '删除', { danger: true })) { d.close(); removeCourse(c); } } }, icon('trash', 18), '删除')));
  void cal; void cancelled;
  const d = openDialog(body, { label: c.name });
}

function openRescheduleForm(c, week, existing) {
  const sem = currentSemester(ctx.store.view());
  const cal = semesterCalendar(sem);
  const popts = periodOptions(cal.periods);
  const weekOpts = Array.from({ length: sem.totalWeeks }, (_, i) => [i + 1, `第 ${i + 1} 周`]);
  const ex = existing && existing.type === 'RESCHEDULE' ? existing : null;
  const nw = select(weekOpts, ex?.newWeek ?? week);
  const nd = select(WEEKDAY_OPTIONS, ex?.newDayOfWeek ?? c.dayOfWeek);
  const sp = select(popts, ex?.newStartPeriod ?? c.startPeriod);
  const ep = select(popts, ex?.newEndPeriod ?? c.endPeriod);
  const room = textInput(ex?.newRoom ?? '', { placeholder: c.room ? `沿用「${c.room}」` : '教室' });
  formSheet({
    title: `调课：第 ${week} 周「${c.name}」`,
    fields: [h('div', { class: 'grid-2' }, field('调到', nw), field('星期', nd)), h('div', { class: 'grid-2' }, field('开始节', sp), field('结束节', ep)), field('新教室', room)],
    extraActions: existing ? [h('button', { type: 'button', class: 'btn danger-ghost', onclick: () => { ctx.store.put(tombstone(existing)); document.querySelector('dialog.sheet[open]')?.close(); toast('已取消调课'); } }, '取消调课')] : [],
    onSubmit: async () => {
      const s = Number(sp.value);
      const e = Number(ep.value);
      if (e < s) throw new Error('结束节不能早于开始节');
      const f = { type: 'RESCHEDULE', newWeek: Number(nw.value), newDayOfWeek: Number(nd.value), newStartPeriod: s, newEndPeriod: e, newRoom: room.value.trim() || null };
      ctx.store.put(existing ? touch(existing, f) : newException({ courseId: c.id, week, ...f }));
      return false;
    },
  });
}

// ---------- 日程 ----------

const FREQ_OPTIONS = [['', '不重复'], ['DAILY', '每天'], ['WEEKLY', '每周'], ['MONTHLY', '每月'], ['YEARLY', '每年']];

export function convertEventToTask(ev, now = Date.now()) {
  const s0 = parseDateTime(ev.start);
  const e0 = parseDateTime(ev.end);
  const timed = !ev.allDay && s0 && e0;
  const durMin = timed
    ? Math.min(24 * 60, Math.max(15, (e0.day * 1440 + e0.min) - (s0.day * 1440 + s0.min)))
    : null;
  const mergedNote = [ev.note?.trim(), ev.location?.trim()].filter(Boolean).join('\n');
  return {
    ...newTask({
      title: ev.title,
      note: mergedNote,
      due: timed ? ev.end : (s0 ? fmtDateTime(s0.day, 23 * 60 + 59) : null),
      dueHasTime: !!timed,
      remindMinutes: ev.remindMinutes ?? null,
      repeat: ev.recurrence ? { ...ev.recurrence } : null,
    }, now),
    blockStart: timed ? ev.start : null,
    blockMinutes: timed ? durMin : null,
    source: ev.source || 'MANUAL',
  };
}

export function openEventForm({ event, date } = {}) {
  const { store } = ctx;
  const ev = event;
  const s0 = ev ? parseDateTime(ev.start) : { day: date ?? todayNum(), min: Math.min(23 * 60, (Math.floor(minutesOfDay() / 60) + 1) * 60) };
  const e0 = ev ? parseDateTime(ev.end) : { day: s0.day, min: Math.min(s0.min + 60, 23 * 60 + 59) };
  const title = textInput(ev?.title || '', { placeholder: '标题', maxLength: 80 });
  const allDay = h('input', { type: 'checkbox', checked: !!ev?.allDay });
  const sd = dateInput(fmtDate(s0.day));
  const st = timeInput(fmtTime(s0.min));
  const ed = dateInput(fmtDate(e0.day));
  const et = timeInput(fmtTime(e0.min));
  const loc = textInput(ev?.location || '', { maxLength: 80 });
  const note = textarea(ev?.note || '', { rows: 2 });
  const kind = segmented([['NORMAL', '日程'], ['EXAM', '考试'], ['COUNTDOWN', '倒数日']], ev?.kind || 'NORMAL');
  const color = colorSwatches(ev?.colorIndex ?? 4);
  const remind = remindSelect(ev?.remindMinutes);
  const freq = select(FREQ_OPTIONS, ev?.recurrence?.freq || '');
  const until = dateInput(ev?.recurrence?.until || '');
  const repeatRow = h('div', { class: 'grid-2', hidden: !ev?.recurrence }, field('重复到', until, '不填则一直重复'));
  freq.addEventListener('change', () => { repeatRow.hidden = !freq.value; });
  const timeRow = (label, d, t) => h('div', { class: 'grid-2 dt-row' }, field(label, d), h('div', { class: 'time-cell', hidden: false }, field('时间', t)));
  const startRow = timeRow('开始', sd, st);
  const endRow = timeRow('结束', ed, et);
  const syncAllDay = () => { [startRow, endRow].forEach((r) => { r.querySelector('.time-cell').hidden = allDay.checked; }); };
  allDay.addEventListener('change', syncAllDay);
  syncAllDay();
  // 修改开始时间时，结束时间跟着平移，保持时长
  let dur = (e0.day * 1440 + e0.min) - (s0.day * 1440 + s0.min);
  const remember = () => {
    const a = parseDate(sd.value); const b = parseDate(ed.value);
    if (!Number.isNaN(a) && !Number.isNaN(b)) dur = (b * 1440 + (parseTime(et.value) || 0)) - (a * 1440 + (parseTime(st.value) || 0));
  };
  [ed, et].forEach((el) => el.addEventListener('change', remember));
  [sd, st].forEach((el) => el.addEventListener('change', () => {
    const a = parseDate(sd.value);
    if (Number.isNaN(a)) return;
    const total = a * 1440 + (parseTime(st.value) || 0) + Math.max(0, dur);
    const day = Math.floor(total / 1440);
    ed.value = fmtDate(day);
    et.value = fmtTime(total - day * 1440);
  }));
  formSheet({
    title: ev ? '编辑日程' : '添加日程',
    wide: true,
    fields: [
      field('标题', title),
      kind.el,
      h('label', { class: 'check-row' }, allDay, h('span', {}, '全天')),
      startRow, endRow,
      field('地点', loc),
      group('颜色', color.el),
      h('div', { class: 'grid-2' }, field('提醒', remind), field('重复', freq)),
      repeatRow,
      field('备注', note),
      ev?.recurrence ? h('p', { class: 'hint' }, '这是重复日程：修改与删除会作用于整个系列。') : null,
    ],
    extraActions: ev ? [
      h('button', {
        type: 'button',
        class: 'btn tonal',
        onclick: () => {
          const now = Date.now();
          const task = convertEventToTask(ev, now);
          const dead = tombstone(ev, now);
          document.querySelector('dialog.sheet[open]')?.close();
          store.put(task, dead);
          toast(`已将「${ev.title}」转为待办`, {
            action: '撤销',
            onAction: () => {
              const t = Date.now();
              store.put(tombstone(store.get(task.id) || task, t), restore(store.get(ev.id) || ev, t));
            },
          });
        },
      }, '转为待办'),
      h('button', { type: 'button', class: 'btn danger-ghost', onclick: async () => { if (await confirmDialog(`删除日程「${ev.title}」${ev.recurrence ? '（整个重复系列）' : ''}？`, '删除', { danger: true })) { document.querySelector('dialog.sheet[open]')?.close(); removeRecords([ev], ev.title); } } }, '删除'),
    ] : [],
    onSubmit: async () => {
      const t = title.value.trim();
      if (!t) throw new Error('请填写标题');
      const a = parseDate(sd.value);
      let b = parseDate(ed.value);
      if (Number.isNaN(a)) throw new Error('请选择开始日期');
      if (Number.isNaN(b)) b = a;
      let start; let end;
      if (allDay.checked) {
        start = fmtDateTime(a, 0);
        end = fmtDateTime(Math.max(a, b), 0);
      } else {
        const sm = parseTime(st.value);
        const em = parseTime(et.value);
        if (Number.isNaN(sm) || Number.isNaN(em)) throw new Error('请填写开始与结束时间');
        start = fmtDateTime(a, sm);
        end = fmtDateTime(b, em);
        if (b * 1440 + em < a * 1440 + sm) end = start;
      }
      const old = ev?.recurrence;
      const recurrence = freq.value
        ? { ...(old || newRecurrence(freq.value)), freq: freq.value, until: until.value || null }
        : null;
      const f = { title: t, start, end, allDay: allDay.checked, location: loc.value.trim(), note: note.value, colorIndex: color.get(), kind: kind.get(), remindMinutes: remindValue(remind), recurrence };
      store.put(ev ? touch(ev, f) : newEvent(f));
      return false;
    },
  });
}

// ---------- 待办 ----------

const PRIORITIES = [[0, '无'], [1, '低'], [2, '中'], [3, '高']];
const REPEATS = [['', '不重复'], ['DAILY', '每天'], ['WEEKLY', '每周'], ['MONTHLY', '每月']];

export function openTaskForm({ task, listId, due } = {}) {
  const { store } = ctx;
  const view = store.view();
  const t = task;
  const dueP = t?.due ? parseDateTime(t.due) : null;
  const blockP = t?.blockStart ? parseDateTime(t.blockStart) : null;
  const title = textInput(t?.title || '', { placeholder: '要做什么？', maxLength: 120 });
  const note = textarea(t?.note || '', { rows: 2 });
  const list = select([['', '无清单'], ...view.lists.map((l) => [l.id, l.name])], t ? t.listId || '' : listId || '');
  const prio = segmented(PRIORITIES, t?.priority || 0);
  const dueDate = dateInput(dueP ? fmtDate(dueP.day) : due != null ? fmtDate(due) : '');
  const dueTime = timeInput(dueP && t.dueHasTime ? fmtTime(dueP.min) : '');
  const dueBeforeNext = h('input', { type: 'checkbox', checked: !!t?.dueBeforeNextClass });
  const blockDate = dateInput(blockP ? fmtDate(blockP.day) : '');
  const blockTime = timeInput(blockP ? fmtTime(blockP.min) : '');
  const blockDur = numberInput(t?.blockMinutes ?? '', { min: 1, max: 1440, placeholder: '分钟（默认 60）' });
  const est = numberInput(t?.estimateMinutes ?? '', { min: 1, max: 1440, placeholder: '分钟' });
  const remind = remindSelect(t?.remindMinutes);
  const remindPersistent = h('input', { type: 'checkbox', checked: !!t?.remindPersistent });
  const repeat = select(REPEATS, t?.repeat?.freq || '');
  const tagsInput = textInput((t?.tags || []).join(', '), { placeholder: '如：学习, 重要（逗号或空格分隔）' });
  const dropped = h('input', { type: 'checkbox', checked: !!t?.dropped });
  const subs = (t ? subtasks(view.tasks, t.id) : []).map((s) => ({ rec: s, title: s.title, done: s.done, removed: false }));
  const subBox = h('div', { class: 'subtasks' });
  const subInput = textInput('', { placeholder: '子任务', 'aria-label': '添加子任务' });
  const renderSubs = () => subBox.replaceChildren(...subs.filter((s) => !s.removed).map((s) => h('div', { class: 'sub-row' },
    h('input', { type: 'checkbox', checked: s.done, 'aria-label': '完成子任务', onchange: (e) => { s.done = e.target.checked; } }),
    h('input', { class: 'input sub-title', type: 'text', value: s.title, 'aria-label': '子任务标题', onchange: (e) => { s.title = e.target.value; } }),
    h('button', { type: 'button', class: 'icon-btn', 'aria-label': '删除子任务', onclick: () => { s.removed = true; renderSubs(); } }, icon('x', 18)))));
  renderSubs();
  subInput.addEventListener('keydown', (e) => {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    e.stopPropagation();
    const v = subInput.value.trim();
    if (v) { subs.push({ rec: null, title: v, done: false, removed: false }); subInput.value = ''; renderSubs(); }
  });
  formSheet({
    title: t ? '编辑待办' : '添加待办',
    wide: true,
    fields: [
      field('标题', title),
      h('div', { class: 'grid-2' }, field('截止日期', dueDate), field('时间', dueTime)),
      h('label', { class: 'check-row' }, dueBeforeNext, h('span', {}, '课前要交')),
      group('日历时间块', h('div', { class: 'stack-sm' },
        h('div', { class: 'grid-2' }, field('日期', blockDate), field('开始时间', blockTime)),
        field('时长（分钟）', blockDur, '设了日期与开始时间后，会在「今日」和「日历」时间轴占用时间段'))),
      group('优先级', prio.el),
      h('div', { class: 'grid-2' }, field('清单', list), field('预计分钟', est)),
      field('标签', tagsInput),
      h('div', { class: 'grid-2' }, field('提醒', remind), field('重复', repeat)),
      h('label', { class: 'check-row' }, remindPersistent, h('span', {}, '持续提醒')),
      h('label', { class: 'check-row' }, dropped, h('span', {}, '不做了')),
      field('备注', note),
      group('子任务', h('div', {}, subBox, subInput)),
    ],
    extraActions: t ? [h('button', { type: 'button', class: 'btn danger-ghost', onclick: async () => { if (await confirmDialog(`删除待办「${t.title}」？`, '删除', { danger: true })) { document.querySelector('dialog.sheet[open]')?.close(); removeTask(t); } } }, '删除')] : [],
    onSubmit: async () => {
      const text = title.value.trim();
      if (!text) throw new Error('请填写标题');
      let dueStr = null;
      let hasTime = false;
      if (dueDate.value) {
        const dd = parseDate(dueDate.value);
        const tm = dueTime.value ? parseTime(dueTime.value) : NaN;
        hasTime = !Number.isNaN(tm);
        dueStr = fmtDateTime(dd, hasTime ? tm : 0);
      }
      const estimate = est.value ? Number(est.value) : null;
      let blockStart = null;
      let blockMinutes = null;
      if (blockDate.value && !blockTime.value) throw new Error('请填写时间块开始时间');
      if (!blockDate.value && blockTime.value) throw new Error('请选择时间块日期');
      if (blockDate.value && blockTime.value) {
        const bd = parseDate(blockDate.value);
        const bt = parseTime(blockTime.value);
        if (Number.isNaN(bd) || Number.isNaN(bt)) throw new Error('时间块日期或时间格式无效');
        blockStart = fmtDateTime(bd, bt);
        if (blockDur.value !== '') {
          const bm = Number(blockDur.value);
          if (!Number.isInteger(bm) || bm < 1 || bm > 1440) throw new Error('时间块时长需在 1–1440 分钟之间');
          blockMinutes = bm;
        } else {
          blockMinutes = estimate || 60;
        }
      }
      const tags = [...new Set(tagsInput.value.split(/[,，\s]+/).map((s) => s.trim()).filter(Boolean))];
      const now = Date.now();
      const isDropped = dropped.checked;
      const isDone = isDropped ? true : (t?.dropped ? false : !!t?.done);
      const doneAt = isDropped ? (t?.doneAt || now) : (t?.dropped ? null : (t?.doneAt ?? null));
      const f = {
        title: text, note: note.value, listId: list.value || null, priority: prio.get(), due: dueStr, dueHasTime: hasTime,
        dueBeforeNextClass: dueBeforeNext.checked,
        blockStart, blockMinutes,
        estimateMinutes: estimate, remindMinutes: remindValue(remind),
        remindPersistent: remindPersistent.checked,
        tags,
        dropped: isDropped, done: isDone, doneAt,
        repeat: repeat.value ? { ...(t?.repeat || newRecurrence(repeat.value)), freq: repeat.value } : null,
      };
      const main = t ? touch(t, f, now) : { ...newTask(f, now), ...f };
      const out = [main];
      subs.forEach((s, i) => {
        const title2 = s.title.trim();
        if (s.rec) {
          if (s.removed || !title2) out.push(tombstone(s.rec, now));
          else if (s.title !== s.rec.title || s.done !== s.rec.done || i !== s.rec.sortOrder) {
            out.push(touch(s.rec, { title: title2, done: s.done, doneAt: s.done ? s.rec.doneAt || now : null, sortOrder: i }, now));
          }
        } else if (!s.removed && title2) {
          out.push({ ...newTask({ title: title2, parentId: main.id, sortOrder: i, listId: main.listId }, now), done: s.done, doneAt: s.done ? now : null });
        }
      });
      store.put(...out);
      return false;
    },
  });
}

export function openListForm({ list } = {}) {
  const name = textInput(list?.name || '', { placeholder: '清单名称', maxLength: 30 });
  const color = colorSwatches(list?.colorIndex ?? 1);
  formSheet({
    title: list ? '编辑清单' : '新建清单',
    fields: [field('名称', name), group('颜色', color.el)],
    extraActions: list ? [h('button', { type: 'button', class: 'btn danger-ghost', onclick: async () => { if (await confirmDialog(`删除清单「${list.name}」？其中的待办会保留（移到「无清单」）。`, '删除', { danger: true })) { document.querySelector('dialog.sheet[open]')?.close(); removeList(list); if (ctx.onListRemoved) ctx.onListRemoved(list); } } }, '删除')] : [],
    onSubmit: async () => {
      const nm = name.value.trim();
      if (!nm) throw new Error('请填写清单名称');
      if (list) ctx.store.put(touch(list, { name: nm, colorIndex: color.get() }));
      else {
        const created = newList({ name: nm, colorIndex: color.get(), sort: ctx.store.view().lists.length });
        ctx.store.put(created);
        if (ctx.onListCreated) ctx.onListCreated(created);
      }
      return false;
    },
  });
}

// ---------- 账号 ----------

export function openPasswordForm() {
  const oldP = h('input', { class: 'input', type: 'password', autocomplete: 'current-password' });
  const newP = h('input', { class: 'input', type: 'password', autocomplete: 'new-password', minLength: 8 });
  const again = h('input', { class: 'input', type: 'password', autocomplete: 'new-password' });
  formSheet({
    title: '修改密码',
    submitText: '修改',
    fields: [field('当前密码', oldP), field('新密码', newP, '至少 8 位'), field('再次输入新密码', again)],
    onSubmit: async () => {
      if (newP.value.length < 8) throw new Error('新密码至少 8 位');
      if (newP.value !== again.value) throw new Error('两次输入的新密码不一致');
      try {
        await ctx.api.changePassword(oldP.value, newP.value);
      } catch (e) {
        throw new Error(e.status === 401 ? '当前密码不正确' : e.message);
      }
      toast('密码已修改');
      return false;
    },
  });
}

// ---------- 添加入口 ----------

export function openAddMenu({ date } = {}) {
  const pick = (fn) => () => { d.close(); fn(); };
  const item = (ic, label, desc, fn) => h('button', { type: 'button', class: 'add-item', onclick: pick(fn) }, icon(ic, 24), h('span', {}, h('b', {}, label), h('small', {}, desc)));
  const body = h('div', { class: 'add-menu' },
    h('div', { class: 'sheet-head' }, h('h2', {}, '添加'), h('button', { type: 'button', class: 'icon-btn', 'aria-label': '关闭', onclick: () => d.close() }, icon('x'))),
    item('check', '待办', '要完成的事，可设截止时间或时间块', () => openTaskForm({ due: date })),
    item('grid', '课程', '添加到课表', () => openCourseForm()));
  const d = openDialog(body, { label: '添加' });
}
