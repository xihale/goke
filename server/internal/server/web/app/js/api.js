// 与同步服务的 HTTP 接口（见 server/openapi.yaml）。同源部署，无需配置服务器地址。

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message || code || `HTTP ${status}`);
    this.status = status;
    this.code = code;
  }
}

/** 网络不可达（离线、服务器停机）。 */
export class NetworkError extends Error {}

export function createApi({ base = '', getToken = () => null, onUnauthorized = () => {}, fetchImpl } = {}) {
  const doFetch = (...a) => (fetchImpl || globalThis.fetch)(...a);

  async function request(method, path, body, { auth = true } = {}) {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const token = getToken();
    if (auth && token) headers.Authorization = `Bearer ${token}`;
    let res;
    try {
      res = await doFetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    } catch (e) {
      throw new NetworkError('无法连接服务器');
    }
    if (res.status === 204) return null;
    let data = null;
    try {
      data = await res.json();
    } catch (e) {
      data = null;
    }
    if (!res.ok) {
      if (res.status === 401 && auth && token) onUnauthorized();
      throw new ApiError(res.status, data?.error, data?.message);
    }
    return data;
  }

  return {
    mode: () => request('GET', '/api/v1/mode', undefined, { auth: false }),
    login: (username, password, deviceName) => request('POST', '/api/v1/login', { username, password, deviceName }, { auth: false }),
    logout: () => request('POST', '/api/v1/logout'),
    me: () => request('GET', '/api/v1/me'),
    changePassword: (oldPassword, newPassword) => request('POST', '/api/v1/me/password', { oldPassword, newPassword }),
    pull: (since, limit = 500) => request('GET', `/api/v1/sync/pull?since=${since}&limit=${limit}`),
    push: (records) => request('POST', '/api/v1/sync/push', { records }),
  };
}
