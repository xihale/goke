'use strict';
const $ = (id) => document.getElementById(id);
let token = sessionStorage.getItem('mintcal-admin-token');

async function api(method, path, body) {
  const res = await fetch('/api/v1' + path, {
    method,
    headers: Object.assign({ 'Content-Type': 'application/json' }, token ? { Authorization: 'Bearer ' + token } : {}),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(data.message || ('请求失败（' + res.status + '）'));
    err.status = res.status;
    throw err;
  }
  return data;
}

function say(el, text, isErr) { el.textContent = text || ''; el.classList.toggle('err', !!isErr); }

function show(loggedIn) {
  $('login').hidden = loggedIn;
  $('panel').hidden = !loggedIn;
}

$('loginForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = new FormData(e.target);
  try {
    const r = await api('POST', '/login', { username: f.get('username'), password: f.get('password'), deviceName: '管理后台（网页）' });
    if (r.user.role !== 'admin') {
      await api('POST', '/logout').catch(() => {});
      throw new Error('该账号不是管理员');
    }
    token = r.token;
    sessionStorage.setItem('mintcal-admin-token', token);
    e.target.reset();
    say($('loginMsg'), '');
    await load();
  } catch (err) {
    token = null;
    say($('loginMsg'), err.message, true);
  }
});

$('logout').addEventListener('click', async () => {
  await api('POST', '/logout').catch(() => {});
  token = null;
  sessionStorage.removeItem('mintcal-admin-token');
  show(false);
});

$('createForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = new FormData(e.target);
  try {
    await api('POST', '/admin/users', { username: f.get('username'), password: f.get('password'), role: f.get('role') });
    e.target.reset();
    say($('msg'), '已创建用户');
    await load();
  } catch (err) { say($('msg'), err.message, true); }
});

function cell(tr, text) { const td = document.createElement('td'); td.textContent = text; tr.appendChild(td); return td; }

function button(label, cls, onClick) {
  const b = document.createElement('button');
  b.textContent = label;
  b.className = 'small ' + cls;
  b.addEventListener('click', async () => {
    try { await onClick(); await load(); } catch (err) { say($('msg'), err.message, true); }
  });
  return b;
}

async function load() {
  let data;
  try {
    data = await api('GET', '/admin/users');
  } catch (err) {
    if (err.status === 401 || err.status === 403) { token = null; sessionStorage.removeItem('mintcal-admin-token'); show(false); return; }
    say($('msg'), err.message, true);
    return;
  }
  show(true);
  const tbody = $('users');
  tbody.textContent = '';
  for (const u of data.users) {
    const tr = document.createElement('tr');
    const isMe = u.id === data.me;
    cell(tr, u.username + (isMe ? '（我）' : ''));
    cell(tr, u.role === 'admin' ? '管理员' : '普通用户');
    const st = document.createElement('td');
    const tag = document.createElement('span');
    tag.className = 'tag' + (u.disabled ? ' off' : '');
    tag.textContent = u.disabled ? '已停用' : '正常';
    st.appendChild(tag); tr.appendChild(st);
    cell(tr, String(u.devices));
    cell(tr, String(u.records));
    cell(tr, new Date(u.createdAt).toLocaleString());
    const ops = document.createElement('td');
    ops.appendChild(button('重置密码', 'ghost', async () => {
      const pw = prompt('输入「' + u.username + '」的新密码（至少 8 位）。重置后该用户所有设备需要重新登录。');
      if (pw === null) return;
      await api('PATCH', '/admin/users/' + u.id, { password: pw });
      say($('msg'), '已重置密码');
    }));
    ops.appendChild(button('退出所有设备', 'ghost', async () => {
      await api('POST', '/admin/users/' + u.id + '/signout');
      say($('msg'), '已让该用户的所有设备退出登录');
    }));
    if (!isMe) {
      ops.appendChild(button(u.disabled ? '启用' : '停用', 'ghost', () => api('PATCH', '/admin/users/' + u.id, { disabled: !u.disabled })));
      ops.appendChild(button(u.role === 'admin' ? '设为普通用户' : '设为管理员', 'ghost', () => api('PATCH', '/admin/users/' + u.id, { role: u.role === 'admin' ? 'user' : 'admin' })));
      ops.appendChild(button('删除', 'danger', async () => {
        if (!confirm('确定删除「' + u.username + '」？该用户在服务器上的所有同步数据都会被永久删除。')) return;
        await api('DELETE', '/admin/users/' + u.id);
      }));
    }
    tr.appendChild(ops);
    tbody.appendChild(tr);
  }
}

if (token) load(); else show(false);
