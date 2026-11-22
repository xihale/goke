// Package mcp 把用户的日程数据以 MCP（Model Context Protocol）工具暴露给外部 AI 助手。
// 传输为 Streamable HTTP（无状态、JSON 响应），鉴权与同步接口共用 Bearer 令牌。
// 写入按同步协议落库（按 updatedAt 后写者胜），App / Web 下次拉取即得；
// 读取直接查同步记录表，data 与客户端 JSON 同构（`_t` 为类型判别字段）。
package mcp

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/modelcontextprotocol/go-sdk/mcp"
	"github.com/xihale/goke/server/internal/store"
)

const version = "0.1.0"

// handlerCache 缓存已构造好的 MCP 端点，键为 (store, userID)：工具 Schema 推导只在首次构造时发生，
// 避免每个请求重复做反射推导；缓存同时把构造期 panic（Schema 写错）收敛成首次使用即失败。
var handlerCache sync.Map // handlerKey -> http.Handler

type handlerKey struct {
	st *store.Store
	id int64
}

// Warmup 构造一个空用户的工具集，强制完成全部工具的 Schema 推导。
// 在服务启动时调用：Schema 写错应当让进程起不来，而不是等第一个用户请求时报错。
func Warmup() {
	newServer(nil, 0)
}

// Handler 返回以 userID 视角操作的 MCP 端点。无状态：不维护会话，每个请求独立处理。
func Handler(st *store.Store, userID int64) http.Handler {
	k := handlerKey{st, userID}
	if v, ok := handlerCache.Load(k); ok {
		return v.(http.Handler)
	}
	h := mcp.NewStreamableHTTPHandler(
		func(*http.Request) *mcp.Server { return newServer(st, userID) },
		&mcp.StreamableHTTPOptions{Stateless: true, JSONResponse: true},
	)
	v, _ := handlerCache.LoadOrStore(k, h)
	return v.(http.Handler)
}

// user 是一组工具共用的数据访问上下文（一个 HTTP 请求生命周期内）。
type user struct {
	st *store.Store
	id int64
}

func newServer(st *store.Store, userID int64) *mcp.Server {
	s := mcp.NewServer(&mcp.Implementation{Name: "goke", Version: version}, nil)
	u := user{st: st, id: userID}
	add(s, "list_lists", "列出待办清单，含每个清单的待办数量", u.listLists)
	add(s, "create_list", "新建待办清单", u.createList)
	add(s, "get_task", "按 id 查看一条待办的完整内容", u.getTask)
	add(s, "search_tasks", "搜索待办：关键词、清单、状态、日期范围", u.searchTasks)
	add(s, "create_task", "新建待办；只有日期是待办，带了具体时刻就是日程", u.createTask)
	add(s, "update_task", "修改待办的任意字段；只写需要改的字段", u.updateTask)
	add(s, "complete_task", "完成 / 重开 / 放弃待办", u.completeTask)
	add(s, "delete_task", "删除待办", u.deleteTask)
	add(s, "get_agenda", "查看一段时间内安排了什么（按时间排序的日程与待办）", u.getAgenda)
	add(s, "search_events", "搜索旧版日程记录（日程已并入待办，仅供读取历史数据）", u.searchEvents)
	add(s, "list_courses", "列出学期与每周课程表", u.listCourses)
	return s
}

// add 注册一个工具：输入按 In 的 JSON Schema 自动校验，输出序列化为 JSON 文本；
// 业务错误以 IsError 结果返回（而不是协议层错误），便于模型阅读并重试。
func add[In any](s *mcp.Server, name, desc string, h func(context.Context, In) (any, error)) {
	mcp.AddTool(s, &mcp.Tool{Name: name, Description: desc},
		func(ctx context.Context, req *mcp.CallToolRequest, in In) (*mcp.CallToolResult, any, error) {
			out, err := h(ctx, in)
			if err != nil {
				return &mcp.CallToolResult{
					Content: []mcp.Content{&mcp.TextContent{Text: err.Error()}},
					IsError: true,
				}, nil, nil
			}
			b, err := json.Marshal(out)
			if err != nil {
				return nil, nil, err
			}
			return &mcp.CallToolResult{Content: []mcp.Content{&mcp.TextContent{Text: string(b)}}}, nil, nil
		})
}

// ---------- 记录的读取视图（与 core 的 kotlinx 序列化字段一一对应） ----------

type taskJSON struct {
	ID                 string         `json:"id"`
	Title              string         `json:"title"`
	Note               string         `json:"note"`
	ListID             *string        `json:"listId"`
	Tags               []string       `json:"tags"`
	Priority           int            `json:"priority"`
	StartDate          *string        `json:"startDate"`
	Due                *string        `json:"due"`
	DueHasTime         bool           `json:"dueHasTime"`
	EstimateMinutes    *int           `json:"estimateMinutes"`
	Done               bool           `json:"done"`
	DoneAt             *int64         `json:"doneAt"`
	Dropped            bool           `json:"dropped"`
	ParentID           *string        `json:"parentId"`
	Repeat             map[string]any `json:"repeat"`
	RemindMinutes      *int           `json:"remindMinutes"`
	RemindPersistent   bool           `json:"remindPersistent"`
	CourseID           *string        `json:"courseId"`
	DueBeforeNextClass bool           `json:"dueBeforeNextClass"`
	BlockStart         *string        `json:"blockStart"`
	BlockMinutes       *int           `json:"blockMinutes"`
	SortOrder          int            `json:"sortOrder"`
	Source             string         `json:"source"`
	Extra              map[string]any `json:"extra"`
	UpdatedAt          int64          `json:"updatedAt"`
	DeletedAt          *int64         `json:"deletedAt"`
}

type listJSON struct {
	ID         string `json:"id"`
	Name       string `json:"name"`
	ColorIndex int    `json:"colorIndex"`
	Sort       int    `json:"sort"`
	UpdatedAt  int64  `json:"updatedAt"`
}

type eventJSON struct {
	ID            string         `json:"id"`
	Title         string         `json:"title"`
	Start         string         `json:"start"`
	End           string         `json:"end"`
	AllDay        bool           `json:"allDay"`
	Location      string         `json:"location"`
	Note          string         `json:"note"`
	RemindMinutes *int           `json:"remindMinutes"`
	Recurrence    map[string]any `json:"recurrence"`
	Kind          string         `json:"kind"`
	UpdatedAt     int64          `json:"updatedAt"`
}

type courseJSON struct {
	ID            string `json:"id"`
	SemesterID    string `json:"semesterId"`
	Name          string `json:"name"`
	Teacher       string `json:"teacher"`
	Room          string `json:"room"`
	DayOfWeek     int    `json:"dayOfWeek"`
	StartPeriod   int    `json:"startPeriod"`
	EndPeriod     int    `json:"endPeriod"`
	Weeks         []int  `json:"weeks"`
	RemindMinutes *int   `json:"remindMinutes"`
}

type semesterJSON struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Week1Monday string `json:"week1Monday"`
	TotalWeeks  int    `json:"totalWeeks"`
	IsCurrent   bool   `json:"isCurrent"`
}

// ---------- 读取辅助 ----------

func decode[T any](r store.Record) (T, error) {
	var v T
	err := json.Unmarshal(r.Data, &v)
	return v, err
}

// lists 载入用户的全部清单，按 sort 排序。
func (u *user) lists() ([]listJSON, error) {
	recs, err := u.st.RecordsByKind(u.id, []string{"TaskList"}, false)
	if err != nil {
		return nil, err
	}
	out := []listJSON{}
	for _, r := range recs {
		l, err := decode[listJSON](r)
		if err != nil {
			continue
		}
		out = append(out, l)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Sort < out[j].Sort })
	return out, nil
}

// tasks 载入用户的全部未删除待办。
func (u *user) tasks() ([]taskJSON, error) {
	recs, err := u.st.RecordsByKind(u.id, []string{"Task"}, false)
	if err != nil {
		return nil, err
	}
	out := []taskJSON{}
	for _, r := range recs {
		t, err := decode[taskJSON](r)
		if err != nil {
			continue
		}
		out = append(out, t)
	}
	return out, nil
}

// ---------- 写入辅助 ----------

// baseData 构造与 App 序列化一致的记录 JSON：显式写出全部默认值（kotlinx encodeDefaults 语义）。
func baseData(t string, id string, updatedAt int64) map[string]any {
	return map[string]any{
		"_t":        t,
		"id":        id,
		"updatedAt": updatedAt,
		"deletedAt": nil,
		"userId":    nil,
	}
}

// errLWW 表示写入因「服务器上有更新的版本」被同步协议拒绝；调用方据此决定是否重读重试。
var errLWW = errors.New("lww rejected")

// push 把一条记录按同步协议写库（与客户端 push 走同一 LWW 规则）。
// 若设备的更新版本时间戳更新，本次写入会被拒绝并作为工具错误返回，避免假成功。
func (u *user) push(kind string, id string, updatedAt int64, data map[string]any) error {
	b, err := json.Marshal(data)
	if err != nil {
		return err
	}
	accepted, rejected, _, err := u.st.Push(u.id, []store.Record{{
		ID: id, Kind: kind, Data: b, UpdatedAt: updatedAt,
	}})
	if err != nil {
		return err
	}
	if len(rejected) > 0 {
		return fmt.Errorf("%w：服务器上 %s 有更新的版本（updatedAt=%d）", errLWW, id, rejected[0].UpdatedAt)
	}
	if len(accepted) == 0 {
		return fmt.Errorf("写入未生效：%s", id)
	}
	return nil
}

// pushTombstone 写入墓碑：wire 的 deletedAt 与 data 内的 deletedAt 同步设置，
// 与 App 客户端推送的删除记录同构，设备拉取后按墓碑移除本地记录。
func (u *user) pushTombstone(kind string, existing store.Record, now int64) error {
	var m map[string]any
	if err := json.Unmarshal(existing.Data, &m); err != nil {
		return err
	}
	m["updatedAt"] = now
	m["deletedAt"] = now
	b, err := json.Marshal(m)
	if err != nil {
		return err
	}
	accepted, rejected, _, err := u.st.Push(u.id, []store.Record{{
		ID: existing.ID, Kind: kind, Data: b, UpdatedAt: now, DeletedAt: &now,
	}})
	if err != nil {
		return err
	}
	if len(rejected) > 0 {
		return fmt.Errorf("%w：服务器上 %s 有更新的版本（updatedAt=%d）", errLWW, existing.ID, rejected[0].UpdatedAt)
	}
	if len(accepted) == 0 {
		return fmt.Errorf("删除未生效：%s", existing.ID)
	}
	return nil
}

// dataT 把同步 kind 映射回记录类型判别值（与 core 的 @SerialName 一致）。
func dataT(kind string) string {
	switch kind {
	case "Task":
		return "task"
	case "TaskList":
		return "taskList"
	case "Event":
		return "event"
	case "Course":
		return "course"
	case "Semester":
		return "semester"
	case "CourseException":
		return "courseException"
	}
	return strings.ToLower(kind)
}

// ---------- 时间解析（与 App 端 parseDateTimeLenient 相同的宽容度） ----------

const (
	dateLayout     = "2006-01-02"
	datetimeLayout = "2006-01-02T15:04"
)

// normalizeDateTime 接受 "YYYY-MM-DD"、"YYYY-MM-DD HH:MM[:SS]" 或 "T" 分隔，
// 返回 (是否带时刻, 规范化的字符串)。与 App 一致：纯日期按当天 00:00。
func normalizeDateTime(s string) (bool, string, error) {
	s = strings.TrimSpace(strings.ReplaceAll(s, " ", "T"))
	if t, err := time.Parse(dateLayout, s); err == nil {
		return false, t.Format(dateLayout), nil
	}
	if t, err := time.Parse(datetimeLayout, s); err == nil {
		return true, t.Format(datetimeLayout), nil
	}
	if t, err := time.Parse("2006-01-02T15:04:05", s); err == nil {
		if t.Second() == 0 {
			return true, t.Format(datetimeLayout), nil
		}
		return true, t.Format("2006-01-02T15:04:05"), nil
	}
	return false, "", fmt.Errorf("时间格式应为 YYYY-MM-DD 或 YYYY-MM-DD HH:MM，收到：%q", s)
}

func parseDate(s string) (time.Time, error) {
	return time.Parse(dateLayout, strings.TrimSpace(s))
}

func dateOf(dt string) string {
	if len(dt) >= len(dateLayout) {
		return dt[:len(dateLayout)]
	}
	return dt
}
