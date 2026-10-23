// 用无头浏览器给网页版截图（手机 / 桌面、浅色 / 深色）。需要先启动 mock.mjs。
// 用法：NODE_PATH=... node webtest/shots.mjs <baseUrl> <outDir>
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');

const [base = 'http://127.0.0.1:8099', out = '/data/shots/web'] = process.argv.slice(2);
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM || '/usr/local/bin/chromium', args: ['--no-sandbox'] });

async function session(name, viewport, colorScheme, routes, extra) {
  const ctx = await browser.newContext({ viewport, colorScheme, deviceScaleFactor: 2, hasTouch: viewport.width < 700, isMobile: viewport.width < 700 });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(`console: ${m.text()}`); });
  await page.goto(base + '/');
  await page.fill('input[name=username]', 'demo');
  await page.fill('input[name=password]', 'demopassword');
  await page.click('button[type=submit]');
  await page.waitForSelector('.shell');
  for (const r of routes) {
    await page.evaluate((x) => { location.hash = `#/${x}`; }, r);
    await page.waitForTimeout(500);
    await page.screenshot({ path: `${out}/${name}-${r}.png`, fullPage: r !== 'timetable' ? true : true });
  }
  if (extra) await extra(page, name);
  await ctx.close();
  return errors;
}

const routes = ['today', 'timetable', 'calendar', 'todo', 'settings'];
const all = [];
all.push(...await session('m', { width: 390, height: 844 }, 'light', routes, async (page, n) => {
  await page.evaluate(() => { location.hash = '#/timetable'; });
  await page.waitForTimeout(300);
  await page.click('.tt-block');
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${out}/${n}-detail.png` });
  await page.keyboard.press('Escape');
  await page.evaluate(() => { location.hash = '#/today'; });
  await page.waitForTimeout(300);
  await page.click('.fab');
  await page.waitForTimeout(400);
  await page.click('.add-item:nth-child(3)');
  await page.waitForTimeout(500);
  await page.screenshot({ path: `${out}/${n}-taskform.png` });
}));
all.push(...await session('d', { width: 1366, height: 820 }, 'light', routes, async (page, n) => {
  await page.evaluate(() => { location.hash = '#/timetable'; });
  await page.waitForTimeout(300);
  await page.click('.add-btn');
  await page.waitForTimeout(500);
  await page.screenshot({ path: `${out}/${n}-courseform.png` });
}));
all.push(...await session('dd', { width: 1366, height: 820 }, 'dark', ['today', 'timetable']));
all.push(...await session('md', { width: 390, height: 844 }, 'dark', ['today', 'todo']));
await browser.close();
console.log(all.length ? `ERRORS:\n${all.join('\n')}` : 'no browser errors');
