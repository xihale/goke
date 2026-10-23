// 极简的同步服务模拟（仅用于开发：预览界面、没有 Go 环境时跑前端测试）。
// 接口语义与 Go 服务端一致：按记录 LWW、递增序号。用法：node webtest/mock.mjs [端口]
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '../internal/server/web/app');
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };

export function startMock({ port = 0, users = { alice: 'alicepassword' }, seed = null } = {}) {
  const state = { users: new Map(), tokens: new Map(), calls: [] };
  for (const [name, pw] of Object.entries(users)) {
    state.users.set(name, { id: state.users.size + 1, username: name, password: pw, role: name === 'admin' ? 'admin' : 'user', seq: 0, records: new Map() });
  }
  if (seed) seed(state);

  const json = (res, code, body) => {
    res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify(body));
  };
  const readBody = (req) => new Promise((resolve) => {
    let b = '';
    req.on('data', (c) => { b += c; });
    req.on('end', () => { try { resolve(JSON.parse(b || 'null')); } catch { resolve(null); } });
  });

  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://x');
    const p = url.pathname;
    if (p.startsWith('/api/v1/')) {
      state.calls.push(`${req.method} ${p}`);
      if (state.failNext) { state.failNext = false; return req.socket.destroy(); }
      const body = req.method === 'GET' ? null : await readBody(req);
      if (p === '/api/v1/login') {
        const u = state.users.get(body?.username);
        if (!u || u.password !== body.password) return json(res, 401, { error: 'invalid_credentials', message: '用户名或密码不正确' });
        const token = `t${state.tokens.size + 1}-${Math.random().toString(36).slice(2)}`;
        state.tokens.set(token, u);
        return json(res, 200, { token, user: { id: u.id, username: u.username, role: u.role } });
      }
      const auth = (req.headers.authorization || '').replace(/^Bearer /, '');
      const u = state.tokens.get(auth);
      if (!u) return json(res, 401, { error: 'unauthorized', message: '请先登录' });
      if (p === '/api/v1/me') return json(res, 200, { id: u.id, username: u.username, role: u.role, cursor: u.seq });
      if (p === '/api/v1/logout') { state.tokens.delete(auth); res.writeHead(204); return res.end(); }
      if (p === '/api/v1/me/password') {
        if (body.oldPassword !== u.password) return json(res, 401, { error: 'invalid_credentials', message: '原密码不正确' });
        if ((body.newPassword || '').length < 8) return json(res, 400, { error: 'weak_password', message: '新密码至少 8 位' });
        u.password = body.newPassword;
        res.writeHead(204);
        return res.end();
      }
      if (p === '/api/v1/sync/pull') {
        const since = +url.searchParams.get('since') || 0;
        const limit = Math.min(+url.searchParams.get('limit') || 500, 1000);
        const all = [...u.records.values()].filter((r) => r.seq > since).sort((a, b) => a.seq - b.seq);
        const out = all.slice(0, limit);
        return json(res, 200, { records: out, cursor: out.length ? out.at(-1).seq : since, more: all.length > limit });
      }
      if (p === '/api/v1/sync/push') {
        const accepted = [];
        const rejected = [];
        for (const r of body.records) {
          const cur = u.records.get(r.id);
          if (cur && cur.updatedAt === r.updatedAt && JSON.stringify(cur.data) === JSON.stringify(r.data)) { accepted.push(r.id); continue; }
          if (cur && r.updatedAt <= cur.updatedAt) { rejected.push(cur); continue; }
          u.seq += 1;
          u.records.set(r.id, { ...r, deletedAt: r.deletedAt ?? null, seq: u.seq });
          accepted.push(r.id);
        }
        return json(res, 200, { accepted, rejected, cursor: u.seq });
      }
      return json(res, 404, { error: 'not_found' });
    }
    let f = p === '/' ? '/index.html' : p.replace(/^\/app/, '');
    const file = path.join(root, path.normalize(f));
    if (!file.startsWith(root) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); return res.end('not found'); }
    res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
    fs.createReadStream(file).pipe(res);
  });
  return new Promise((resolve) => server.listen(port, '127.0.0.1', () => resolve({
    url: `http://127.0.0.1:${server.address().port}`,
    state,
    close: () => new Promise((r) => { server.closeAllConnections?.(); server.close(r); }),
  })));
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const { seedDemo } = await import('./demo-data.mjs');
  const m = await startMock({ port: +process.argv[2] || 8099, users: { demo: 'demopassword', admin: 'adminpassword' }, seed: seedDemo });
  console.log(`mock server at ${m.url}  (demo / demopassword)`);
}
