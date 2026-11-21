package server

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"

	"github.com/xihale/mintcal/server/internal/store"
)

func init() { Iterations = 1000 } // 测试里加快密码运算

type env struct {
	t   *testing.T
	srv *Server
	h   http.Handler
}

func newEnv(t *testing.T) *env {
	t.Helper()
	st, err := store.Open(filepath.Join(t.TempDir(), "t.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { st.Close() })
	s := New(st, Options{})
	if _, _, created, err := s.Bootstrap("root", "rootpassword"); err != nil || !created {
		t.Fatalf("bootstrap: %v %v", created, err)
	}
	return &env{t: t, srv: s, h: s.Handler()}
}

func (e *env) do(method, path, token string, body any) (int, map[string]any) {
	e.t.Helper()
	var rd *bytes.Reader
	if body != nil {
		b, _ := json.Marshal(body)
		rd = bytes.NewReader(b)
	} else {
		rd = bytes.NewReader(nil)
	}
	req := httptest.NewRequest(method, path, rd)
	req.RemoteAddr = "10.0.0.1:1234"
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	rec := httptest.NewRecorder()
	e.h.ServeHTTP(rec, req)
	var out map[string]any
	_ = json.Unmarshal(rec.Body.Bytes(), &out)
	return rec.Code, out
}

func (e *env) login(user, pw string) string {
	e.t.Helper()
	code, out := e.do("POST", "/api/v1/login", "", map[string]string{"username": user, "password": pw, "deviceName": "test"})
	if code != 200 {
		e.t.Fatalf("login %s: %d %v", user, code, out)
	}
	return out["token"].(string)
}

func (e *env) addUser(admin, name, pw, role string) {
	e.t.Helper()
	code, out := e.do("POST", "/api/v1/admin/users", admin, map[string]string{"username": name, "password": pw, "role": role})
	if code != 201 {
		e.t.Fatalf("create %s: %d %v", name, code, out)
	}
}

func rec(id string, updated int64, data string) map[string]any {
	return map[string]any{"id": id, "kind": "Course", "data": json.RawMessage(data), "updatedAt": updated, "deletedAt": nil}
}

func TestPasswordHashing(t *testing.T) {
	h, err := HashPassword("correct horse")
	if err != nil {
		t.Fatal(err)
	}
	if !CheckPassword("correct horse", h) || CheckPassword("wrong", h) || CheckPassword("x", "garbage") {
		t.Fatal("password check failed")
	}
}

func TestLoginAndAuth(t *testing.T) {
	e := newEnv(t)
	if code, _ := e.do("GET", "/api/v1/me", "", nil); code != 401 {
		t.Fatalf("want 401 got %d", code)
	}
	if code, _ := e.do("POST", "/api/v1/login", "", map[string]string{"username": "root", "password": "bad"}); code != 401 {
		t.Fatalf("want 401 got %d", code)
	}
	tok := e.login("root", "rootpassword")
	code, me := e.do("GET", "/api/v1/me", tok, nil)
	if code != 200 || me["username"] != "root" || me["role"] != "admin" {
		t.Fatalf("me: %d %v", code, me)
	}
	if code, _ := e.do("POST", "/api/v1/logout", tok, nil); code != 204 {
		t.Fatalf("logout %d", code)
	}
	if code, _ := e.do("GET", "/api/v1/me", tok, nil); code != 401 {
		t.Fatalf("token should be revoked, got %d", code)
	}
}

func TestLoginRateLimit(t *testing.T) {
	e := newEnv(t)
	for i := 0; i < 8; i++ {
		e.do("POST", "/api/v1/login", "", map[string]string{"username": "root", "password": "bad"})
	}
	code, _ := e.do("POST", "/api/v1/login", "", map[string]string{"username": "root", "password": "rootpassword"})
	if code != 429 {
		t.Fatalf("want 429 got %d", code)
	}
}

func TestAdminManagesUsers(t *testing.T) {
	e := newEnv(t)
	admin := e.login("root", "rootpassword")
	e.addUser(admin, "alice", "alicepassword", "user")
	if code, _ := e.do("POST", "/api/v1/admin/users", admin, map[string]string{"username": "ALICE", "password": "alicepassword"}); code != 409 {
		t.Fatalf("duplicate name (case-insensitive) want 409 got %d", code)
	}
	if code, _ := e.do("POST", "/api/v1/admin/users", admin, map[string]string{"username": "bob", "password": "short"}); code != 400 {
		t.Fatalf("weak password want 400 got %d", code)
	}
	alice := e.login("alice", "alicepassword")
	// 普通用户不能进后台接口
	if code, _ := e.do("GET", "/api/v1/admin/users", alice, nil); code != 403 {
		t.Fatalf("want 403 got %d", code)
	}
	code, list := e.do("GET", "/api/v1/admin/users", admin, nil)
	if code != 200 || len(list["users"].([]any)) != 2 {
		t.Fatalf("list: %d %v", code, list)
	}
	aliceID := int64(list["users"].([]any)[1].(map[string]any)["id"].(float64))
	path := "/api/v1/admin/users/" + itoa(aliceID)

	// 重置密码：旧令牌失效，新密码可登录
	if code, _ := e.do("PATCH", path, admin, map[string]any{"password": "newpassword1"}); code != 200 {
		t.Fatalf("reset %d", code)
	}
	if code, _ := e.do("GET", "/api/v1/me", alice, nil); code != 401 {
		t.Fatalf("old token should be invalid, got %d", code)
	}
	alice = e.login("alice", "newpassword1")

	// 停用：令牌失效且无法登录；启用后恢复
	if code, _ := e.do("PATCH", path, admin, map[string]any{"disabled": true}); code != 200 {
		t.Fatalf("disable %d", code)
	}
	if code, _ := e.do("GET", "/api/v1/me", alice, nil); code != 401 {
		t.Fatalf("disabled user token want 401 got %d", code)
	}
	if code, _ := e.do("POST", "/api/v1/login", "", map[string]string{"username": "alice", "password": "newpassword1"}); code != 403 {
		t.Fatalf("disabled login want 403 got %d", code)
	}
	if code, _ := e.do("PATCH", path, admin, map[string]any{"disabled": false}); code != 200 {
		t.Fatalf("enable %d", code)
	}
	e.login("alice", "newpassword1")

	// 删除
	if code, _ := e.do("DELETE", path, admin, nil); code != 204 {
		t.Fatalf("delete %d", code)
	}
	if code, _ := e.do("POST", "/api/v1/login", "", map[string]string{"username": "alice", "password": "newpassword1"}); code != 401 {
		t.Fatalf("deleted user login want 401 got %d", code)
	}
}

func TestLastAdminProtected(t *testing.T) {
	e := newEnv(t)
	admin := e.login("root", "rootpassword")
	_, list := e.do("GET", "/api/v1/admin/users", admin, nil)
	rootID := itoa(int64(list["users"].([]any)[0].(map[string]any)["id"].(float64)))
	for _, tc := range []struct {
		method string
		body   any
	}{
		{"DELETE", nil},
		{"PATCH", map[string]any{"disabled": true}},
		{"PATCH", map[string]any{"role": "user"}},
	} {
		if code, _ := e.do(tc.method, "/api/v1/admin/users/"+rootID, admin, tc.body); code != 400 {
			t.Fatalf("%s %v on self want 400 got %d", tc.method, tc.body, code)
		}
	}
	// 有第二个管理员后，可以由对方降级第一个
	e.addUser(admin, "boss2", "boss2password", "admin")
	boss := e.login("boss2", "boss2password")
	if code, _ := e.do("PATCH", "/api/v1/admin/users/"+rootID, boss, map[string]any{"role": "user"}); code != 200 {
		t.Fatalf("demote want 200 got %d", code)
	}
	// 现在 boss2 是唯一管理员，不能被删除（即使由自己之外的人，这里验证 self 保护）
	_, list = e.do("GET", "/api/v1/admin/users", boss, nil)
	bossID := itoa(int64(list["users"].([]any)[1].(map[string]any)["id"].(float64)))
	if code, _ := e.do("DELETE", "/api/v1/admin/users/"+bossID, boss, nil); code != 400 {
		t.Fatalf("want 400 got %d", code)
	}
}

func TestSyncPushPullAndLWW(t *testing.T) {
	e := newEnv(t)
	admin := e.login("root", "rootpassword")
	e.addUser(admin, "alice", "alicepassword", "user")
	e.addUser(admin, "bob", "bobpassword1", "user")
	a := e.login("alice", "alicepassword")
	b := e.login("bob", "bobpassword1")

	// 首次推送
	code, out := e.do("POST", "/api/v1/sync/push", a, map[string]any{"records": []any{
		rec("c1", 100, `{"type":"course","name":"高数"}`),
		rec("c2", 100, `{"type":"course","name":"英语"}`),
	}})
	if code != 200 || len(out["accepted"].([]any)) != 2 || out["cursor"].(float64) != 2 {
		t.Fatalf("push: %d %v", code, out)
	}
	// 另一台设备拉取
	a2 := e.login("alice", "alicepassword")
	code, out = e.do("GET", "/api/v1/sync/pull?since=0", a2, nil)
	recs := out["records"].([]any)
	if code != 200 || len(recs) != 2 || out["more"].(bool) || out["cursor"].(float64) != 2 {
		t.Fatalf("pull: %d %v", code, out)
	}
	// 新记录覆盖旧记录；落后的被拒绝并返回服务器版本
	code, out = e.do("POST", "/api/v1/sync/push", a2, map[string]any{"records": []any{
		rec("c1", 200, `{"type":"course","name":"高等数学"}`),
		rec("c2", 50, `{"type":"course","name":"过期"}`),
	}})
	if code != 200 || len(out["accepted"].([]any)) != 1 || len(out["rejected"].([]any)) != 1 {
		t.Fatalf("lww push: %d %v", code, out)
	}
	rej := out["rejected"].([]any)[0].(map[string]any)
	if rej["id"] != "c2" || !strings.Contains(string(mustJSON(rej["data"])), "英语") {
		t.Fatalf("rejected should carry server version: %v", rej)
	}
	// 增量拉取只拿到变化的一条
	code, out = e.do("GET", "/api/v1/sync/pull?since=2", a, nil)
	recs = out["records"].([]any)
	if len(recs) != 1 || recs[0].(map[string]any)["id"] != "c1" || out["cursor"].(float64) != 3 {
		t.Fatalf("incremental pull: %v", out)
	}
	// 相同内容重复推送是空操作，不产生新序号
	code, out = e.do("POST", "/api/v1/sync/push", a, map[string]any{"records": []any{rec("c1", 200, `{"type":"course","name":"高等数学"}`)}})
	if out["cursor"].(float64) != 3 {
		t.Fatalf("idempotent push should keep cursor: %v", out)
	}
	// 软删除（墓碑）也会同步
	del := rec("c2", 300, `{"type":"course","name":"英语"}`)
	del["deletedAt"] = 300
	e.do("POST", "/api/v1/sync/push", a, map[string]any{"records": []any{del}})
	_, out = e.do("GET", "/api/v1/sync/pull?since=3", a2, nil)
	if got := out["records"].([]any)[0].(map[string]any); got["deletedAt"] == nil {
		t.Fatalf("tombstone expected: %v", got)
	}
	// 用户之间数据隔离
	_, out = e.do("GET", "/api/v1/sync/pull?since=0", b, nil)
	if len(out["records"].([]any)) != 0 {
		t.Fatalf("bob must not see alice's data: %v", out)
	}
}

func TestPullPaging(t *testing.T) {
	e := newEnv(t)
	admin := e.login("root", "rootpassword")
	var rs []any
	for i := 0; i < 5; i++ {
		rs = append(rs, rec("r"+itoa(int64(i)), 10, `{"type":"course"}`))
	}
	e.do("POST", "/api/v1/sync/push", admin, map[string]any{"records": rs})
	_, out := e.do("GET", "/api/v1/sync/pull?since=0&limit=2", admin, nil)
	if len(out["records"].([]any)) != 2 || !out["more"].(bool) || out["cursor"].(float64) != 2 {
		t.Fatalf("page1: %v", out)
	}
	_, out = e.do("GET", "/api/v1/sync/pull?since=4&limit=2", admin, nil)
	if len(out["records"].([]any)) != 1 || out["more"].(bool) {
		t.Fatalf("last page: %v", out)
	}
}

func TestPushValidation(t *testing.T) {
	e := newEnv(t)
	admin := e.login("root", "rootpassword")
	bad := []any{
		map[string]any{"id": "", "kind": "Course", "data": json.RawMessage(`{}`), "updatedAt": 1},
		map[string]any{"id": "x", "kind": "Course", "data": json.RawMessage(`[1]`), "updatedAt": 1},
		map[string]any{"id": "x", "kind": "Course", "data": json.RawMessage(`{}`), "updatedAt": 0},
	}
	for i, r := range bad {
		if code, _ := e.do("POST", "/api/v1/sync/push", admin, map[string]any{"records": []any{r}}); code != 400 {
			t.Fatalf("case %d want 400 got %d", i, code)
		}
	}
}

func TestAdminPageServed(t *testing.T) {
	e := newEnv(t)
	req := httptest.NewRequest("GET", "/admin/", nil)
	rec := httptest.NewRecorder()
	e.h.ServeHTTP(rec, req)
	if rec.Code != 200 || !strings.Contains(rec.Body.String(), "管理后台") {
		t.Fatalf("admin page: %d", rec.Code)
	}
	if !strings.Contains(rec.Header().Get("Content-Security-Policy"), "script-src 'self'") {
		t.Fatal("CSP header missing")
	}
}

func TestWebAppServed(t *testing.T) {
	e := newEnv(t)
	get := func(path string) *httptest.ResponseRecorder {
		rec := httptest.NewRecorder()
		e.h.ServeHTTP(rec, httptest.NewRequest("GET", path, nil))
		return rec
	}
	cases := []struct{ path, ctype, contains string }{
		{"/", "text/html", "manifest.webmanifest"},
		{"/app/js/main.js", "javascript", "import"},
		{"/app/app.css", "text/css", "--primary"},
		{"/app/manifest.webmanifest", "", "薄荷广工"},
		{"/app/icon-192.png", "image/png", ""},
		{"/admin/", "text/html", "管理后台"},
	}
	for _, c := range cases {
		rec := get(c.path)
		if rec.Code != 200 {
			t.Fatalf("%s: %d", c.path, rec.Code)
		}
		if c.ctype != "" && !strings.Contains(rec.Header().Get("Content-Type"), c.ctype) {
			t.Fatalf("%s: content-type %q", c.path, rec.Header().Get("Content-Type"))
		}
		if c.contains != "" && !strings.Contains(rec.Body.String(), c.contains) {
			t.Fatalf("%s: body lacks %q", c.path, c.contains)
		}
		csp := rec.Header().Get("Content-Security-Policy")
		if !strings.Contains(csp, "script-src 'self'") || !strings.Contains(csp, "manifest-src 'self'") {
			t.Fatalf("%s: CSP %q", c.path, csp)
		}
	}
	if got := get("/").Header().Get("Cache-Control"); got != "no-cache" {
		t.Fatalf("index cache-control %q", got)
	}
	if rec := get("/app/nope.js"); rec.Code != 404 {
		t.Fatalf("missing asset: %d", rec.Code)
	}
	if rec := get("/nope"); rec.Code != 404 {
		t.Fatalf("unknown path: %d", rec.Code)
	}
}

func itoa(n int64) string   { b, _ := json.Marshal(n); return string(b) }
func mustJSON(v any) []byte { b, _ := json.Marshal(v); return b }
