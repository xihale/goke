// 极简 DOM 辅助：不使用 innerHTML，所有文本都经 textContent，天然避免 XSS；
// 样式只通过 CSS 类与 CSS 变量（页面的 CSP 不允许内联样式属性）。

const SVG_NS = 'http://www.w3.org/2000/svg';

export function h(tag, props, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'style') for (const [p, val] of Object.entries(v)) el.style.setProperty(p, String(val));
    else if (k === 'dataset') Object.assign(el.dataset, v);
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k in el && !k.includes('-')) el[k] = v;
    else el.setAttribute(k, v === true ? '' : String(v));
  }
  append(el, kids);
  return el;
}

export function append(el, kids) {
  for (const k of kids.flat(Infinity)) {
    if (k == null || k === false) continue;
    el.append(k instanceof Node ? k : document.createTextNode(String(k)));
  }
  return el;
}

export const clear = (el) => { while (el.firstChild) el.removeChild(el.firstChild); return el; };

// 24x24 线性图标。路径简单，不依赖任何图标库。
const ICONS = {
  sun: 'M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4M12 8a4 4 0 100 8 4 4 0 000-8z',
  grid: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z',
  calendar: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4',
  check: 'M12 3a9 9 0 100 18 9 9 0 000-18zM8 12.5l3 3 5-6',
  user: 'M12 4a4 4 0 100 8 4 4 0 000-8zM4 20c0-4 3.6-6 8-6s8 2 8 6',
  settings: 'M4 7h9M17 7h3M4 17h3M11 17h9M15 4v6M9 14v6',
  plus: 'M12 5v14M5 12h14',
  left: 'M15 5l-7 7 7 7',
  right: 'M9 5l7 7-7 7',
  edit: 'M4 20h4L19 9l-4-4L4 16zM13.500 6.500l4 4',
  trash: 'M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13M10 11v6M14 11v6',
  sync: 'M20 8a8 8 0 00-14-2M4 4v4h4M4 16a8 8 0 0014 2M20 20v-4h-4',
  cloudOff: 'M3 3l18 18M7 18a4 4 0 01-.600-7.900A6 6 0 0113 6c1.300 0 2.500.400 3.500 1.100M18.500 11A4 4 0 0117 18H9',
  x: 'M6 6l12 12M18 6L6 18',
  clock: 'M12 3a9 9 0 100 18 9 9 0 000-18zM12 7v5l3 2',
  pin: 'M12 21s6-5.500 6-11a6 6 0 10-12 0c0 5.500 6 11 6 11zM12 7.500a2.500 2.500 0 100 5 2.500 2.500 0 000-5z',
  repeat: 'M17 3l3 3-3 3M4 11V9a3 3 0 013-3h13M7 21l-3-3 3-3M20 13v2a3 3 0 01-3 3H4',
  flag: 'M5 21V4h12l-2 4 2 4H5',
  list: 'M9 6h11M9 12h11M9 18h11M4.500 6h.010M4.500 12h.010M4.500 18h.010',
  download: 'M12 4v11M7 11l5 5 5-5M5 20h14',
  logout: 'M10 4H5v16h5M15 8l4 4-4 4M19 12H9',
  external: 'M14 4h6v6M20 4l-9 9M18 14v6H4V6h6',
  coffee: 'M5 8h11v6a4 4 0 01-4 4H9a4 4 0 01-4-4zM16 9h2a2 2 0 010 4h-2M8 3v2M12 3v2',
  sparkle: 'M12 3l1.800 5.200L19 10l-5.200 1.800L12 17l-1.800-5.200L5 10l5.200-1.800z',
  more: 'M5 12h.010M12 12h.010M19 12h.010',
};

export function icon(name, size = 22) {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('width', size);
  svg.setAttribute('height', size);
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '1.8');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');
  svg.classList.add('icon');
  const p = document.createElementNS(SVG_NS, 'path');
  p.setAttribute('d', ICONS[name] || ICONS.more);
  svg.append(p);
  return svg;
}

/** 圆环进度。 */
export function ring(done, total, size = 64) {
  const r = 26;
  const c = 2 * Math.PI * r;
  const frac = total ? Math.min(1, done / total) : 0;
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 64 64');
  svg.setAttribute('width', size);
  svg.setAttribute('height', size);
  svg.classList.add('ring');
  const mk = (cls, extra) => {
    const e = document.createElementNS(SVG_NS, 'circle');
    e.setAttribute('cx', '32'); e.setAttribute('cy', '32'); e.setAttribute('r', r);
    e.setAttribute('fill', 'none'); e.setAttribute('stroke-width', '7'); e.setAttribute('stroke-linecap', 'round');
    e.setAttribute('class', cls);
    for (const [k, v] of Object.entries(extra || {})) e.setAttribute(k, v);
    return e;
  };
  svg.append(mk('ring-track'));
  if (frac > 0) svg.append(mk('ring-bar', { 'stroke-dasharray': `${c * frac} ${c}`, transform: 'rotate(-90 32 32)' }));
  return svg;
}

// ---------- 提示 ----------

let toastHost = null;
export function toast(message, { action, onAction, ms = 5000 } = {}) {
  if (!toastHost) {
    toastHost = h('div', { class: 'toasts', 'aria-live': 'polite' });
    document.body.append(toastHost);
  }
  const el = h('div', { class: 'toast' }, h('span', {}, message));
  let timer;
  const close = () => { clearTimeout(timer); el.remove(); };
  if (action) {
    el.append(h('button', { class: 'toast-action', type: 'button', onclick: () => { close(); onAction && onAction(); } }, action));
  }
  toastHost.append(el);
  timer = setTimeout(close, ms);
  return close;
}

// ---------- 对话框（桌面居中、手机底部抽屉） ----------

/** 打开一个对话框。content 是 DOM 节点；返回 {close, el}。关闭时调用 onClose。 */
export function openDialog(content, { onClose, wide = false, label = '' } = {}) {
  const dlg = h('dialog', { class: `sheet${wide ? ' wide' : ''}`, 'aria-label': label });
  const inner = h('div', { class: 'sheet-body' }, content);
  dlg.append(inner);
  const finish = () => {
    dlg.remove();
    onClose && onClose();
  };
  dlg.addEventListener('close', finish);
  dlg.addEventListener('click', (e) => { if (e.target === dlg) dlg.close(); });
  document.body.append(dlg);
  dlg.showModal();
  return { el: dlg, close: () => dlg.open && dlg.close() };
}

/** 确认对话框，返回 Promise<boolean>。 */
export function confirmDialog(message, okText = '确定', { danger = false } = {}) {
  return new Promise((resolve) => {
    let result = false;
    const box = h('div', { class: 'confirm' },
      h('p', { class: 'confirm-msg' }, message),
      h('div', { class: 'row-actions' },
        h('button', { class: 'btn ghost', type: 'button', onclick: () => d.close() }, '取消'),
        h('button', { class: `btn ${danger ? 'danger' : 'primary'}`, type: 'button', onclick: () => { result = true; d.close(); } }, okText)));
    const d = openDialog(box, { onClose: () => resolve(result), label: message });
  });
}
