// 表单控件的小工具。每个控件返回 {el, get, set?}，保存时统一读取。

import { h, icon, openDialog } from './dom.js';
import { COURSE_COLORS } from './theme.js';
import { fmtTime } from './util.js';

export function field(label, control, hint) {
  return h('label', { class: 'field' }, h('span', { class: 'field-label' }, label), control, hint ? h('small', { class: 'hint' }, hint) : null);
}

export function group(label, control, hint) {
  return h('div', { class: 'field', role: 'group', 'aria-label': label }, h('span', { class: 'field-label' }, label), control, hint ? h('small', { class: 'hint' }, hint) : null);
}

export function textInput(value = '', attrs = {}) {
  return h('input', { class: 'input', type: 'text', value, autocomplete: 'off', ...attrs });
}

export function numberInput(value, attrs = {}) {
  return h('input', { class: 'input', type: 'number', inputMode: 'numeric', value: value ?? '', ...attrs });
}

export function select(options, value, attrs = {}) {
  const el = h('select', { class: 'input select', ...attrs }, options.map(([v, label]) => h('option', { value: String(v) }, label)));
  el.value = String(value ?? options[0][0]);
  return el;
}

export function textarea(value = '', attrs = {}) {
  return h('textarea', { class: 'input', rows: 3, value, ...attrs });
}

/** 分段按钮：单选。 */
export function segmented(options, initial, onChange) {
  let cur = initial;
  const btns = options.map(([v, label]) => h('button', {
    type: 'button',
    class: 'seg-btn',
    'aria-pressed': String(v === cur),
    onclick: () => { cur = v; sync(); onChange && onChange(v); },
  }, label));
  const sync = () => btns.forEach((b, i) => b.setAttribute('aria-pressed', String(options[i][0] === cur)));
  return { el: h('div', { class: 'seg' }, btns), get: () => cur, set: (v) => { cur = v; sync(); } };
}

/** 8 种课程 / 日程颜色。 */
export function colorSwatches(initial = 0, onChange) {
  let cur = initial;
  const dark = document.documentElement.dataset.theme === 'dark';
  const pal = COURSE_COLORS[dark ? 'dark' : 'light'];
  const btns = pal.map((_, i) => h('button', {
    type: 'button',
    class: `swatch c${i}`,
    'aria-label': `颜色 ${i + 1}`,
    'aria-pressed': String(i === cur),
    onclick: () => { cur = i; sync(); onChange && onChange(i); },
  }));
  const sync = () => btns.forEach((b, i) => b.setAttribute('aria-pressed', String(i === cur)));
  return { el: h('div', { class: 'swatches' }, btns), get: () => cur };
}

export const REMIND_OPTIONS = [
  ['', '不提醒'], ['0', '开始时'], ['5', '提前 5 分钟'], ['10', '提前 10 分钟'], ['15', '提前 15 分钟'],
  ['30', '提前 30 分钟'], ['60', '提前 1 小时'], ['1440', '提前 1 天'],
];

export function remindSelect(value, defaultLabel) {
  const opts = REMIND_OPTIONS.map(([v, l]) => (v === '' && defaultLabel ? ['', defaultLabel] : [v, l]));
  return select(opts, value == null ? '' : String(value));
}

export const remindValue = (el) => (el.value === '' ? null : Number(el.value));

export function periodOptions(periods) {
  return periods.map((p) => [p.index, `第 ${p.index} 节 ${p.start}`]);
}

/** 对话框的标准外壳：标题栏 + 内容 + 底部操作栏。返回 {dlg, form, setError}。 */
export function formSheet({ title, fields, submitText = '保存', onSubmit, extraActions = [], wide = false }) {
  const err = h('p', { class: 'form-error', role: 'alert', hidden: true });
  const form = h('form', { class: 'form', noValidate: true });
  const submitBtn = h('button', { type: 'submit', class: 'btn primary' }, submitText);
  let dlg;
  let submitting = false;
  const setError = (msg) => { err.textContent = msg || ''; err.hidden = !msg; };
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (submitting) return;
    submitting = true;
    submitBtn.disabled = true;
    setError('');
    try {
      const keepOpen = await onSubmit({ close: () => dlg.close(), setError });
      if (!keepOpen) dlg.close();
    } catch (ex) {
      setError(ex.message || '保存失败');
    } finally {
      submitting = false;
      submitBtn.disabled = false;
    }
  });
  form.append(
    h('div', { class: 'sheet-head' },
      h('h2', {}, title),
      h('button', { type: 'button', class: 'icon-btn', 'aria-label': '关闭', onclick: () => dlg.close() }, icon('x'))),
    h('div', { class: 'sheet-fields' }, fields, err),
    h('div', { class: 'sheet-foot' },
      h('div', { class: 'foot-left' }, extraActions),
      h('button', { type: 'button', class: 'btn ghost', onclick: () => dlg.close() }, '取消'),
      submitBtn));
  dlg = openDialog(form, { label: title, wide });
  const first = form.querySelector('input:not([type=checkbox]):not([type=hidden]),textarea');
  if (first && !matchMedia('(pointer: coarse)').matches) first.focus();
  return { dlg, form, setError };
}

export const timeLabel = (min) => fmtTime(min);
