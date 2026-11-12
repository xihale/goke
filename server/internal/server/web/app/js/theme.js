// 主题：深浅色 + 预设主题色 / 自定义种子色。颜色通过 CSS 变量下发（见 app.css）。
// 默认「薄荷」的取值与 Android 端 ui/theme/Theme.kt 一致；其他种子色按 HSL 推导。

export const PRESETS = [
  { id: 'mint', name: '薄荷', seed: '#2E776C' },
  { id: 'peach', name: '蜜桃', seed: '#E8836B' },
  { id: 'lavender', name: '薰衣草', seed: '#7A6CC4' },
  { id: 'sky', name: '天空', seed: '#4A90D9' },
  { id: 'rose', name: '玫瑰', seed: '#D65C7A' },
  { id: 'lemon', name: '柠檬', seed: '#C9A227' },
];

export const COURSE_COLORS = {
  light: [[0xD9E8EF, 0x3B6173], [0xBFDCD5, 0x2C5B53], [0xDAD8EE, 0x4F4D7C], [0xF3EBCF, 0x75662F], [0xF7DFD2, 0x85503A], [0xDFE8DA, 0x4B5F49], [0xF4D9E0, 0x83465A], [0xD2D8EA, 0x454F7A]],
  dark: [[0x2B4350, 0xBFE0F0], [0x24463F, 0xB2E3D8], [0x38355A, 0xD3D0F5], [0x4A4224, 0xF0E5B6], [0x503629, 0xF5D2BF], [0x33442F, 0xCFE5C8], [0x56323E, 0xF4CBD7], [0x303A5E, 0xC9D3F5]],
};

export const hex = (n) => `#${n.toString(16).padStart(6, '0')}`;

export function hexToHsl(hexStr) {
  const m = /^#?([0-9a-f]{6})$/i.exec(hexStr || '');
  if (!m) return null;
  const n = parseInt(m[1], 16);
  const r = ((n >> 16) & 255) / 255;
  const g = ((n >> 8) & 255) / 255;
  const b = (n & 255) / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  let hh = 0;
  let s = 0;
  if (max !== min) {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    if (max === r) hh = (g - b) / d + (g < b ? 6 : 0);
    else if (max === g) hh = (b - r) / d + 2;
    else hh = (r - g) / d + 4;
    hh *= 60;
  }
  return { h: hh, s: s * 100, l: l * 100 };
}

export function hsl(h, s, l) {
  return `hsl(${Math.round(h)} ${Math.round(Math.max(0, Math.min(100, s)))}% ${Math.round(Math.max(0, Math.min(100, l)))}%)`;
}

const MINT = {
  light: {
    primary: '#2E776C', onPrimary: '#FFFFFF', primaryContainer: '#CDEBE3', onPrimaryContainer: '#1B473F',
    secondaryContainer: '#FBE3D6', onSecondaryContainer: '#7A4630', tertiaryContainer: '#E4E0F6', onTertiaryContainer: '#3D3873',
    background: '#F2F7F5', surface: '#FFFFFF', surfaceHigh: '#F7FAF9', surfaceVariant: '#E9F1EE', onSurface: '#24312D',
    onSurfaceVariant: '#5E6F69', outline: '#6E7F7A', outlineVariant: '#E4ECE9', error: '#B23E50', errorContainer: '#FCE9E7', onErrorContainer: '#7A2A22',
  },
  dark: {
    primary: '#7CCDBA', onPrimary: '#0B2A24', primaryContainer: '#24413B', onPrimaryContainer: '#BFEDE2',
    secondaryContainer: '#4A3329', onSecondaryContainer: '#F6D5C3', tertiaryContainer: '#2F2C45', onTertiaryContainer: '#E4E0F6',
    background: '#141A19', surface: '#1E2624', surfaceHigh: '#26302D', surfaceVariant: '#2A3431', onSurface: '#E6EEEB',
    onSurfaceVariant: '#93A39E', outline: '#6C7C77', outlineVariant: '#2A3431', error: '#FF8F84', errorContainer: '#4A2A27', onErrorContainer: '#FFD9D5',
  },
};

/** 由种子色推导调色板。薄荷用固定值，保证与手机端一致。 */
export function palette(seed, dark) {
  if (!seed || seed.toLowerCase() === PRESETS[0].seed.toLowerCase()) return MINT[dark ? 'dark' : 'light'];
  const c = hexToHsl(seed);
  if (!c) return MINT[dark ? 'dark' : 'light'];
  const { h: hh } = c;
  const s = Math.max(28, Math.min(c.s, 70));
  const base = dark ? MINT.dark : MINT.light;
  if (dark) {
    return {
      ...base,
      primary: hsl(hh, Math.min(s + 5, 65), 70), onPrimary: hsl(hh, 50, 12),
      primaryContainer: hsl(hh, s * 0.5, 22), onPrimaryContainer: hsl(hh, 55, 86),
      background: hsl(hh, 10, 9), surface: hsl(hh, 10, 13), surfaceHigh: hsl(hh, 10, 17), surfaceVariant: hsl(hh, 9, 19),
      onSurface: hsl(hh, 14, 91), onSurfaceVariant: hsl(hh, 8, 65), outline: hsl(hh, 7, 46), outlineVariant: hsl(hh, 9, 20),
    };
  }
  return {
    ...base,
    primary: hsl(hh, s, c.l > 55 ? 42 : Math.min(c.l, 46)), onPrimary: '#FFFFFF',
    primaryContainer: hsl(hh, s * 0.8, 87), onPrimaryContainer: hsl(hh, s * 0.8, 20),
    background: hsl(hh, 22, 96), surface: '#FFFFFF', surfaceHigh: hsl(hh, 20, 97.5), surfaceVariant: hsl(hh, 20, 92),
    onSurface: hsl(hh, 14, 17), onSurfaceVariant: hsl(hh, 8, 45), outline: hsl(hh, 8, 62), outlineVariant: hsl(hh, 16, 90),
  };
}

const KEY = 'mintcal-theme';

export function loadTheme(storage = globalThis.localStorage) {
  try {
    const t = JSON.parse(storage.getItem(KEY) || 'null');
    if (t && ['system', 'light', 'dark'].includes(t.mode)) return { mode: t.mode, seed: t.seed || PRESETS[0].seed };
  } catch (e) { /* 用默认值 */ }
  return { mode: 'system', seed: PRESETS[0].seed };
}

export function saveTheme(t, storage = globalThis.localStorage) {
  try { storage.setItem(KEY, JSON.stringify(t)); } catch (e) { /* 忽略 */ }
}

const camelToKebab = (s) => s.replace(/[A-Z]/g, (m) => `-${m.toLowerCase()}`);

export function applyTheme(t, root = document.documentElement) {
  const sysDark = typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches;
  const dark = t.mode === 'dark' || (t.mode === 'system' && sysDark);
  root.dataset.theme = dark ? 'dark' : 'light';
  for (const [k, v] of Object.entries(palette(t.seed, dark))) root.style.setProperty(`--${camelToKebab(k)}`, v);
  const cs = COURSE_COLORS[dark ? 'dark' : 'light'];
  cs.forEach(([bg, fg], i) => {
    root.style.setProperty(`--c${i}-bg`, hex(bg));
    root.style.setProperty(`--c${i}-fg`, hex(fg));
  });
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', palette(t.seed, dark).background);
  return dark;
}
