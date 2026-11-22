package server

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/modelcontextprotocol/go-sdk/mcp"

	"github.com/xihale/goke/server/internal/store"
)

type mcpTransport struct {
	base  http.RoundTripper
	token string
}

func (r mcpTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	req = req.Clone(req.Context())
	req.Header.Set("Authorization", "Bearer "+r.token)
	return r.base.RoundTrip(req)
}

// mcpSession 对测试服务器建立一个带鉴权头的 MCP 客户端会话（无状态端点）。
func mcpSession(t *testing.T, h http.Handler, token string) (*mcp.ClientSession, func()) {
	t.Helper()
	ts := httptest.NewServer(h)
	client := mcp.NewClient(&mcp.Implementation{Name: "goke-test", Version: "0.0.0"}, nil)
	tr := &mcp.StreamableClientTransport{
		Endpoint:             ts.URL + "/mcp",
		HTTPClient:           &http.Client{Transport: mcpTransport{base: http.DefaultTransport, token: token}},
		DisableStandaloneSSE: true,
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	ss, err := client.Connect(ctx, tr, nil)
	if err != nil {
		cancel()
		ts.Close()
		t.Fatalf("mcp connect: %v", err)
	}
	return ss, func() { ss.Close(); cancel(); ts.Close() }
}

// call 执行一个工具调用；wantErr 为 true 时断言结果为 IsError，返回 {"error": 文本}；否则解析 JSON 输出。
func call(t *testing.T, ss *mcp.ClientSession, name string, args map[string]any, wantErr bool) map[string]any {
	t.Helper()
	res, err := ss.CallTool(context.Background(), &mcp.CallToolParams{Name: name, Arguments: args})
	if err != nil {
		t.Fatalf("call %s: %v", name, err)
	}
	var textOut string
	for _, c := range res.Content {
		if tc, ok := c.(*mcp.TextContent); ok {
			textOut += tc.Text
		}
	}
	if res.IsError != wantErr {
		t.Fatalf("call %s: IsError=%v want %v: %s", name, res.IsError, wantErr, textOut)
	}
	text, ok := res.Content[0].(*mcp.TextContent)
	if !ok {
		t.Fatalf("call %s: content %T 不是文本", name, res.Content[0])
	}
	if wantErr {
		return map[string]any{"error": text.Text}
	}
	var out map[string]any
	if err := json.Unmarshal([]byte(text.Text), &out); err != nil {
		t.Fatalf("call %s: 输出不是 JSON：%q", name, text.Text)
	}
	return out
}

// pullRecords 以某用户身份分页拉取全部同步记录。
func (e *env) pullRecords(token string) ([]store.Record, error) {
	e.t.Helper()
	var out []store.Record
	since := int64(0)
	for {
		code, body := e.do("GET", "/api/v1/sync/pull?since="+itoa(since), token, nil)
		if code != 200 {
			e.t.Fatalf("pull: %d %v", code, body)
		}
		raw, _ := json.Marshal(body["records"])
		var recs []store.Record
		if err := json.Unmarshal(raw, &recs); err != nil {
			e.t.Fatal(err)
		}
		out = append(out, recs...)
		if more, _ := body["more"].(bool); !more {
			return out, nil
		}
		if n := len(recs); n > 0 {
			since = recs[n-1].Seq
		} else {
			return out, nil
		}
	}
}

// pushRecords 以某用户身份推送记录（走真实 HTTP 接口）。
func (e *env) pushRecords(token string, recs []store.Record) error {
	e.t.Helper()
	recsJSON, err := json.Marshal(recs)
	if err != nil {
		return err
	}
	var payload []map[string]any
	if err := json.Unmarshal(recsJSON, &payload); err != nil {
		return err
	}
	code, body := e.do("POST", "/api/v1/sync/push", token, map[string]any{"records": payload})
	if code != 200 {
		e.t.Fatalf("push: %d %v", code, body)
	}
	return nil
}

func TestMCPAuth(t *testing.T) {
	e := newEnv(t)
	e.addUser(e.login("root", "rootpassword"), "alice", "alicepassword", "user")
	alice := e.login("alice", "alicepassword")

	handshake := map[string]any{"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": map[string]any{}}
	if code, _ := e.do("POST", "/mcp", "", handshake); code != 401 {
		t.Fatalf("未带令牌应 401，得到 %d", code)
	}
	if code, _ := e.do("POST", "/mcp", "wrong-token", handshake); code != 401 {
		t.Fatalf("错误令牌应 401，得到 %d", code)
	}
	ss, done := mcpSession(t, e.h, alice)
	defer done()
	if _, err := ss.ListTools(context.Background(), nil); err != nil {
		t.Fatalf("tools/list: %v", err)
	}
}

func TestMCPTaskLifecycle(t *testing.T) {
	e := newEnv(t)
	root := e.login("root", "rootpassword")
	e.addUser(root, "alice", "alicepassword", "user")
	alice := e.login("alice", "alicepassword")

	ss, done := mcpSession(t, e.h, alice)
	defer done()

	// 清单 → 待办 → 拉取校验 wire 格式
	if _, ok := call(t, ss, "list_lists", map[string]any{}, false)["lists"]; !ok {
		t.Fatal("list_lists 输出缺少 lists")
	}
	created := call(t, ss, "create_list", map[string]any{"name": "工作"}, false)
	listID := created["id"].(string)

	res := call(t, ss, "create_task", map[string]any{
		"title": "写周报", "listId": listID, "priority": 2,
		"due": "2026-11-05 14:30", "remindMinutes": 10,
		"repeat": map[string]any{"freq": "WEEKLY", "byDays": []any{3}},
	}, false)
	taskID := res["id"].(string)

	recs, err := e.pullRecords(alice)
	if err != nil {
		t.Fatal(err)
	}
	var wire *store.Record
	for i, r := range recs {
		if r.Kind == "Task" {
			wire = &recs[i]
		}
	}
	if wire == nil {
		t.Fatalf("pull 未返回 Task 记录：%d 条", len(recs))
	}
	var data map[string]any
	if err := json.Unmarshal(wire.Data, &data); err != nil {
		t.Fatal(err)
	}
	for k, want := range map[string]any{
		"_t": "task", "id": taskID, "due": "2026-11-05T14:30", "dueHasTime": true, "source": "CODE", "priority": float64(2),
	} {
		if data[k] != want {
			t.Fatalf("data[%s]=%v want %v", k, data[k], want)
		}
	}
	if data["listId"] != listID {
		t.Fatalf("data.listId=%v want %v", data["listId"], listID)
	}
	if rp, ok := data["repeat"].(map[string]any); !ok || rp["freq"] != "WEEKLY" {
		t.Fatalf("data.repeat=%v", data["repeat"])
	}

	// 搜索与读取
	if got := call(t, ss, "search_tasks", map[string]any{"query": "周报", "status": "all"}, false)["total"]; got != float64(1) {
		t.Fatalf("search_tasks total=%v", got)
	}
	get := call(t, ss, "get_task", map[string]any{"id": taskID}, false)
	if get["listName"] != "工作" {
		t.Fatalf("get_task listName=%v", get)
	}

	// 完成 → 修改 → 删除（墓碑）
	call(t, ss, "complete_task", map[string]any{"id": taskID}, false)
	task := call(t, ss, "get_task", map[string]any{"id": taskID}, false)["task"].(map[string]any)
	if task["done"] != true || task["doneAt"] == nil {
		t.Fatalf("complete_task 未生效：%v", task)
	}
	call(t, ss, "update_task", map[string]any{"id": taskID, "title": "写双周报", "due": ""}, false)
	task = call(t, ss, "get_task", map[string]any{"id": taskID}, false)["task"].(map[string]any)
	if task["title"] != "写双周报" || task["due"] != nil {
		t.Fatalf("update_task 未生效：%v", task)
	}
	call(t, ss, "delete_task", map[string]any{"id": taskID}, false)
	recs, err = e.pullRecords(alice)
	if err != nil {
		t.Fatal(err)
	}
	var tomb *store.Record
	for i, r := range recs {
		if r.Kind == "Task" && r.ID == taskID {
			tomb = &recs[i]
		}
	}
	if tomb == nil || tomb.DeletedAt == nil {
		t.Fatalf("删除应产生墓碑：%v", tomb)
	}
	call(t, ss, "get_task", map[string]any{"id": taskID}, true)

	// 输入校验
	call(t, ss, "create_task", map[string]any{"title": " "}, true)
	call(t, ss, "create_task", map[string]any{"title": "x", "due": "11月5日"}, true)
	call(t, ss, "create_task", map[string]any{"title": "x", "listId": "no-such"}, true)
}

func TestMCPAgendaAndCourses(t *testing.T) {
	e := newEnv(t)
	root := e.login("root", "rootpassword")
	e.addUser(root, "bob", "bobpassword", "user")
	bob := e.login("bob", "bobpassword")

	ss, done := mcpSession(t, e.h, bob)
	defer done()

	call(t, ss, "create_task", map[string]any{
		"title": "小组会", "due": "2026-11-04 10:00", "blockStart": "2026-11-04 10:00", "blockMinutes": 90,
	}, false)
	call(t, ss, "create_task", map[string]any{"title": "只有一天", "due": "2026-11-06"}, false)

	agenda := call(t, ss, "get_agenda", map[string]any{"from": "2026-11-04", "to": "2026-11-05"}, false)
	if agenda["total"] != float64(1) {
		t.Fatalf("agenda 应只含 11-04 一条：%v", agenda)
	}
	item := agenda["items"].([]any)[0].(map[string]any)
	if item["anchor"] != "block" || item["title"] != "小组会" {
		t.Fatalf("agenda 首项：%v", item)
	}

	if week := call(t, ss, "get_agenda", map[string]any{"from": "2026-11-01", "to": "2026-11-07"}, false)["total"]; week != float64(2) {
		t.Fatalf("一周应两条：%v", week)
	}

	if _, ok := call(t, ss, "list_courses", map[string]any{}, false)["courses"]; !ok {
		t.Fatal("list_courses 缺少 courses")
	}
}

func TestMCPIsolationAndLWW(t *testing.T) {
	e := newEnv(t)
	root := e.login("root", "rootpassword")
	e.addUser(root, "alice", "alicepassword", "user")
	e.addUser(root, "bob", "bobpassword", "user")
	alice := e.login("alice", "alicepassword")
	bob := e.login("bob", "bobpassword")

	ass, adone := mcpSession(t, e.h, alice)
	defer adone()
	bss, bdone := mcpSession(t, e.h, bob)
	defer bdone()

	taskID := call(t, ass, "create_task", map[string]any{"title": "Alice 的待办"}, false)["id"].(string)

	// bob 看不到 alice 的待办
	call(t, bss, "get_task", map[string]any{"id": taskID}, true)
	if found := call(t, bss, "search_tasks", map[string]any{"query": "Alice", "status": "all"}, false)["total"]; found != float64(0) {
		t.Fatalf("用户数据泄漏：%v", found)
	}

	// 设备推入更新的版本后，MCP 的旧写入应被 LWW 拒绝并作为工具错误返回
	recs, err := e.pullRecords(alice)
	if err != nil || len(recs) == 0 {
		t.Fatalf("pull: %v", err)
	}
	future := time.Now().Add(1 * time.Hour).UnixMilli()
	for _, r := range recs {
		if r.ID == taskID {
			if err := e.pushRecords(alice, []store.Record{{ID: r.ID, Kind: r.Kind, Data: r.Data, UpdatedAt: future}}); err != nil {
				t.Fatal(err)
			}
		}
	}
	call(t, ass, "update_task", map[string]any{"id": taskID, "title": "晚到的新标题"}, true)
}

func TestMCPSemantics(t *testing.T) {
	e := newEnv(t)
	root := e.login("root", "rootpassword")
	e.addUser(root, "carol", "carolpassword", "user")
	carol := e.login("carol", "carolpassword")

	ss, done := mcpSession(t, e.h, carol)
	defer done()

	// 设备先推一条 sortOrder=50 的待办（模拟既有数据）
	deviceTask := map[string]any{
		"_t": "task", "id": "device-task-1", "title": "设备建的", "sortOrder": 50,
		"updatedAt": time.Now().UnixMilli() - 1000, "deletedAt": nil, "userId": nil,
	}
	db, _ := json.Marshal(deviceTask)
	if err := e.pushRecords(carol, []store.Record{{ID: "device-task-1", Kind: "Task", Data: db, UpdatedAt: time.Now().UnixMilli() - 1000}}); err != nil {
		t.Fatal(err)
	}

	// 顶层追加 = 全局最大 sortOrder + 1
	a := call(t, ss, "create_task", map[string]any{"title": "助手建的第一条"}, false)
	b := call(t, ss, "create_task", map[string]any{"title": "助手建的第二条"}, false)
	got := call(t, ss, "get_task", map[string]any{"id": a["id"].(string)}, false)["task"].(map[string]any)
	if got["sortOrder"] != float64(51) {
		t.Fatalf("第一条应 sortOrder=51，得到 %v", got["sortOrder"])
	}
	got = call(t, ss, "get_task", map[string]any{"id": b["id"].(string)}, false)["task"].(map[string]any)
	if got["sortOrder"] != float64(52) {
		t.Fatalf("第二条应 sortOrder=52，得到 %v", got["sortOrder"])
	}

	// 子任务：排兄弟末尾；顶层才能当父
	parent := call(t, ss, "create_task", map[string]any{"title": "父任务"}, false)["id"].(string)
	c1 := call(t, ss, "create_task", map[string]any{"title": "子一", "parentId": parent}, false)["id"].(string)
	call(t, ss, "create_task", map[string]any{"title": "子二", "parentId": parent}, false)
	sub := call(t, ss, "get_task", map[string]any{"id": c1}, false)["task"].(map[string]any)
	if sub["sortOrder"] != float64(1) {
		t.Fatalf("子任务应 sortOrder=1，得到 %v", sub["sortOrder"])
	}
	// 已有子任务的不能变成别人的子任务；子任务也不能当父
	y := call(t, ss, "create_task", map[string]any{"title": "有娃的", "parentId": parent}, false)["id"].(string)
	call(t, ss, "update_task", map[string]any{"id": y, "parentId": ""}, false) // 先脱离子任务便于构造独立小家
	zy := call(t, ss, "create_task", map[string]any{"title": "娃", "parentId": y}, false)["id"].(string)
	call(t, ss, "update_task", map[string]any{"id": y, "parentId": parent}, true)
	call(t, ss, "create_task", map[string]any{"title": "x", "parentId": zy}, true)

	// 删除父任务级联删子任务；再删一次幂等成功
	out := call(t, ss, "delete_task", map[string]any{"id": parent}, false)
	if out["cascaded"] != float64(2) {
		t.Fatalf("级联应删 2 个子任务：%v", out)
	}
	recs, err := e.pullRecords(carol)
	if err != nil {
		t.Fatal(err)
	}
	tombs := map[string]bool{}
	for _, r := range recs {
		if r.DeletedAt != nil {
			tombs[r.ID] = true
		}
	}
	if !tombs[parent] {
		t.Fatal("父任务应有墓碑")
	}
	for _, r := range recs {
		if r.Kind == "Task" && !tombs[r.ID] {
			title := ""
			var d map[string]any
			_ = json.Unmarshal(r.Data, &d)
			title, _ = d["title"].(string)
			if title == "子一" || title == "子二" {
				t.Fatalf("子任务 %s 未被级联删除", r.ID)
			}
		}
	}
	if out := call(t, ss, "delete_task", map[string]any{"id": parent}, false); out["deleted"] != true {
		t.Fatalf("重复删除应幂等成功：%v", out)
	}
	call(t, ss, "delete_task", map[string]any{"id": "no-such-id"}, false)

	// 没有 due 时不能单独标记带时刻
	call(t, ss, "update_task", map[string]any{"id": b["id"].(string), "dueHasTime": true}, true)
}
