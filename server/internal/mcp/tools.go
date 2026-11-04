package mcp

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strings"
	"time"

	"github.com/google/uuid"

	"github.com/xihale/mintcal/server/internal/store"
)

// ---------- 输入结构（字段描述会进入工具的 JSON Schema） ----------

type repeatIn struct {
	Freq     string `json:"freq" jsonschema:"重复频率：DAILY / WEEKLY / MONTHLY / YEARLY；NONE 表示清除重复"`
	Interval int    `json:"interval,omitempty" jsonschema:"间隔数，默认 1（每 1 天/周/月/年）"`
	ByDays   []int  `json:"byDays,omitempty" jsonschema:"仅 WEEKLY：生效的星期几，1=周一 … 7=周日"`
	Until    string `json:"until,omitempty" jsonschema:"重复到哪天为止（YYYY-MM-DD），与 count 二选一"`
	Count    int    `json:"count,omitempty" jsonschema:"重复次数，与 until 二选一"`
}

type createTaskIn struct {
	Title         string    `json:"title" jsonschema:"标题（必填）"`
	Note          string    `json:"note,omitempty" jsonschema:"备注"`
	ListID        string    `json:"listId,omitempty" jsonschema:"清单 id；缺省为默认清单"`
	Priority      int       `json:"priority,omitempty" jsonschema:"0 无 / 1 低 / 2 中 / 3 高"`
	StartDate     string    `json:"startDate,omitempty" jsonschema:"开始日期（YYYY-MM-DD）"`
	Due           string    `json:"due,omitempty" jsonschema:"截止：YYYY-MM-DD 或 YYYY-MM-DD HH:MM；带时刻即视为日程"`
	RemindMinutes int       `json:"remindMinutes,omitempty" jsonschema:"提前多少分钟提醒"`
	Repeat        *repeatIn `json:"repeat,omitempty" jsonschema:"重复规则"`
	ParentID      string    `json:"parentId,omitempty" jsonschema:"父待办 id（子任务仅一层）"`
	BlockStart    string    `json:"blockStart,omitempty" jsonschema:"排入日历的开始时刻（YYYY-MM-DD HH:MM）"`
	BlockMinutes  int       `json:"blockMinutes,omitempty" jsonschema:"排入日历的时长（分钟），需与 blockStart 同时给"`
	Tags          []string  `json:"tags,omitempty" jsonschema:"标签"`
}

type updateTaskIn struct {
	ID            string    `json:"id" jsonschema:"待办 id（必填）"`
	Title         *string   `json:"title,omitempty" jsonschema:"新标题"`
	Note          *string   `json:"note,omitempty" jsonschema:"新备注；传空字符串即清空"`
	ListID        *string   `json:"listId,omitempty" jsonschema:"换清单；传空字符串回到默认清单"`
	Priority      *int      `json:"priority,omitempty" jsonschema:"0 无 / 1 低 / 2 中 / 3 高"`
	StartDate     *string   `json:"startDate,omitempty" jsonschema:"开始日期；空字符串清除"`
	Due           *string   `json:"due,omitempty" jsonschema:"截止；空字符串清除"`
	DueHasTime    *bool     `json:"dueHasTime,omitempty" jsonschema:"截止是否带时刻"`
	RemindMinutes *int      `json:"remindMinutes,omitempty" jsonschema:"提前提醒分钟数；0 清除提醒"`
	Repeat        *repeatIn `json:"repeat,omitempty" jsonschema:"重复规则；freq 填 NONE 即清除"`
	ParentID      *string   `json:"parentId,omitempty" jsonschema:"父待办 id；空字符串清除"`
	BlockStart    *string   `json:"blockStart,omitempty" jsonschema:"排入日历的开始时刻；空字符串清除"`
	BlockMinutes  *int      `json:"blockMinutes,omitempty" jsonschema:"排入日历的时长（分钟）"`
	Tags          []string  `json:"tags,omitempty" jsonschema:"整体替换标签"`
}

type completeTaskIn struct {
	ID      string `json:"id" jsonschema:"待办 id（必填）"`
	Done    *bool  `json:"done,omitempty" jsonschema:"true 完成（默认）/ false 重开"`
	Dropped *bool  `json:"dropped,omitempty" jsonschema:"true 表示「不做了」"`
}

type idIn struct {
	ID string `json:"id" jsonschema:"待办 id（必填）"`
}

type searchTasksIn struct {
	Query  string `json:"query,omitempty" jsonschema:"关键词：标题 / 备注 / 标签包含匹配"`
	ListID string `json:"listId,omitempty" jsonschema:"限定清单 id"`
	Status string `json:"status,omitempty" jsonschema:"open / done / dropped / all，默认 open"`
	From   string `json:"from,omitempty" jsonschema:"日期范围起（YYYY-MM-DD，含）"`
	To     string `json:"to,omitempty" jsonschema:"日期范围止（YYYY-MM-DD，含）"`
	Limit  int    `json:"limit,omitempty" jsonschema:"最多返回条数，默认 50，上限 200"`
}

type agendaIn struct {
	From string `json:"from" jsonschema:"起始日期（YYYY-MM-DD，含，必填）"`
	To   string `json:"to" jsonschema:"结束日期（YYYY-MM-DD，含，必填）"`
}

type rangeIn struct {
	Query string `json:"query,omitempty" jsonschema:"关键词：标题 / 地点 / 备注包含匹配"`
	From  string `json:"from,omitempty" jsonschema:"日期范围起（YYYY-MM-DD，含）"`
	To    string `json:"to,omitempty" jsonschema:"日期范围止（YYYY-MM-DD，含）"`
	Limit int    `json:"limit,omitempty" jsonschema:"最多返回条数，默认 100"`
}

type createListIn struct {
	Name       string `json:"name" jsonschema:"清单名称（必填）"`
	ColorIndex int    `json:"colorIndex,omitempty" jsonschema:"颜色序号（默认 1）"`
}

// ---------- 工具实现 ----------

func (u *user) listLists(_ context.Context, _ struct{}) (any, error) {
	lists, err := u.lists()
	if err != nil {
		return nil, err
	}
	tasks, err := u.tasks()
	if err != nil {
		return nil, err
	}
	count := map[string][2]int{}
	for _, t := range tasks {
		key := ""
		if t.ListID != nil {
			key = *t.ListID
		}
		c := count[key]
		if t.Done {
			c[1]++
		} else {
			c[0]++
		}
		count[key] = c
	}
	type listOut struct {
		listJSON
		Open int `json:"open"`
		Done int `json:"done"`
	}
	out := []listOut{}
	for _, l := range lists {
		c := count[l.ID]
		out = append(out, listOut{l, c[0], c[1]})
	}
	if c := count[""]; c[0]+c[1] > 0 {
		out = append(out, listOut{listJSON{ID: "", Name: "默认清单"}, c[0], c[1]})
	}
	return map[string]any{"lists": out}, nil
}

func (u *user) createList(_ context.Context, in createListIn) (any, error) {
	if strings.TrimSpace(in.Name) == "" {
		return nil, fmt.Errorf("清单名称不能为空")
	}
	now := time.Now().UnixMilli()
	data := baseData("taskList", uuid.NewString(), now)
	data["name"] = strings.TrimSpace(in.Name)
	data["colorIndex"] = clampInt(in.ColorIndex, 0, 15)
	data["sort"] = 0
	if err := u.push("TaskList", data["id"].(string), now, data); err != nil {
		return nil, err
	}
	return map[string]any{"id": data["id"], "name": in.Name}, nil
}

func (u *user) getTask(_ context.Context, in idIn) (any, error) {
	t, listName, err := u.findTask(in.ID)
	if err != nil {
		return nil, err
	}
	return map[string]any{"listName": listName, "task": t}, nil
}

func (u *user) searchTasks(_ context.Context, in searchTasksIn) (any, error) {
	status := in.Status
	if status == "" {
		status = "open"
	}
	limit := in.Limit
	if limit <= 0 {
		limit = 50
	}
	if limit > 200 {
		limit = 200
	}
	var from, to time.Time
	if in.From != "" {
		if t, err := parseDate(in.From); err != nil {
			return nil, fmt.Errorf("from：%v", err)
		} else {
			from = t
		}
	}
	if in.To != "" {
		if t, err := parseDate(in.To); err != nil {
			return nil, fmt.Errorf("to：%v", err)
		} else {
			to = t
		}
	}
	q := strings.ToLower(strings.TrimSpace(in.Query))
	tasks, err := u.tasks()
	if err != nil {
		return nil, err
	}
	lists, _ := u.lists()
	names := map[string]string{}
	for _, l := range lists {
		names[l.ID] = l.Name
	}
	out := []map[string]any{}
	for _, t := range tasks {
		if matchStatus(t, status) && matchText(t, q) && matchList(t, in.ListID) && matchDate(t, from, to) {
			out = append(out, taskBrief(t, names[strings.TrimSpace(deref(t.ListID))]))
		}
		if len(out) >= limit {
			break
		}
	}
	return map[string]any{"tasks": out, "total": len(out)}, nil
}

func (u *user) createTask(_ context.Context, in createTaskIn) (any, error) {
	if strings.TrimSpace(in.Title) == "" {
		return nil, fmt.Errorf("标题不能为空")
	}
	now := time.Now().UnixMilli()
	data := baseData("task", uuid.NewString(), now)
	data["title"] = strings.TrimSpace(in.Title)
	data["note"] = in.Note
	data["priority"] = clampInt(in.Priority, 0, 3)
	data["source"] = "CODE"
	if in.ListID != "" {
		if _, err := u.findList(in.ListID); err != nil {
			return nil, err
		}
		data["listId"] = in.ListID
	}
	if in.StartDate != "" {
		if _, s, err := normalizeDateTime(in.StartDate); err != nil {
			return nil, err
		} else {
			data["startDate"] = s
		}
	}
	if in.Due != "" {
		hasTime, s, err := normalizeDateTime(in.Due)
		if err != nil {
			return nil, err
		}
		data["due"] = s
		data["dueHasTime"] = hasTime
	}
	if in.RemindMinutes > 0 {
		data["remindMinutes"] = in.RemindMinutes
	}
	if in.Repeat != nil {
		r, err := repeatJSON(in.Repeat)
		if err != nil {
			return nil, err
		}
		if r != nil {
			data["repeat"] = r
		}
	}
	if in.ParentID != "" {
		tasks, err := u.tasks()
		if err != nil {
			return nil, err
		}
		parent, _, err := u.findTask(in.ParentID)
		if err != nil {
			return nil, err
		}
		if parent.ParentID != nil {
			return nil, fmt.Errorf("子任务只能有一层：父待办 %s 自己也是子任务", in.ParentID)
		}
		data["parentId"] = in.ParentID
		// 与 App 的 makeSubtask 一致：排在兄弟末尾
		order := 0
		for _, t := range tasks {
			if t.ParentID != nil && *t.ParentID == in.ParentID && t.SortOrder > order {
				order = t.SortOrder
			}
		}
		data["sortOrder"] = order + 1
	} else {
		// 与 App 的快速添加 / 编辑页一致：顶层追加 = 全部待办里最大 sortOrder + 1
		tasks, err := u.tasks()
		if err != nil {
			return nil, err
		}
		order := 0
		for _, t := range tasks {
			if t.SortOrder > order {
				order = t.SortOrder
			}
		}
		data["sortOrder"] = order + 1
	}
	if in.BlockStart != "" {
		if in.BlockMinutes <= 0 {
			return nil, fmt.Errorf("blockStart 需要与 blockMinutes 同时给")
		}
		_, s, err := normalizeDateTime(in.BlockStart)
		if err != nil {
			return nil, err
		}
		data["blockStart"] = s
		data["blockMinutes"] = in.BlockMinutes
	} else if in.BlockMinutes > 0 {
		return nil, fmt.Errorf("blockMinutes 需要与 blockStart 同时给")
	}
	if in.Tags != nil {
		data["tags"] = in.Tags
	}
	id := data["id"].(string)
	if err := u.push("Task", id, now, data); err != nil {
		return nil, err
	}
	return map[string]any{"id": id}, nil
}

func (u *user) updateTask(_ context.Context, in updateTaskIn) (any, error) {
	err := u.withTask(in.ID, func(m map[string]any) error {
		if in.Title != nil {
			if strings.TrimSpace(*in.Title) == "" {
				return fmt.Errorf("标题不能为空")
			}
			m["title"] = strings.TrimSpace(*in.Title)
		}
		if in.Note != nil {
			m["note"] = *in.Note
		}
		if in.ListID != nil {
			if *in.ListID == "" {
				m["listId"] = nil
			} else if _, err := u.findList(*in.ListID); err != nil {
				return err
			} else {
				m["listId"] = *in.ListID
			}
		}
		if in.Priority != nil {
			m["priority"] = clampInt(*in.Priority, 0, 3)
		}
		if in.StartDate != nil {
			if *in.StartDate == "" {
				m["startDate"] = nil
			} else if _, s, err := normalizeDateTime(*in.StartDate); err != nil {
				return err
			} else {
				m["startDate"] = s
			}
		}
		if in.Due != nil {
			if *in.Due == "" {
				m["due"] = nil
				m["dueHasTime"] = false
			} else if hasTime, s, err := normalizeDateTime(*in.Due); err != nil {
				return err
			} else {
				m["due"] = s
				m["dueHasTime"] = hasTime
			}
		}
		if in.DueHasTime != nil {
			if *in.DueHasTime && m["due"] == nil {
				return fmt.Errorf("没有截止日期，无法单独标记「带时刻」")
			}
			m["dueHasTime"] = *in.DueHasTime
		}
		if in.RemindMinutes != nil {
			if *in.RemindMinutes > 0 {
				m["remindMinutes"] = *in.RemindMinutes
			} else {
				m["remindMinutes"] = nil
			}
		}
		if in.Repeat != nil {
			r, err := repeatJSON(in.Repeat)
			if err != nil {
				return err
			}
			m["repeat"] = r // freq=NONE 时 r 为 nil，即清除
		}
		if in.ParentID != nil {
			if *in.ParentID == "" {
				m["parentId"] = nil
			} else {
				parent, _, err := u.findTask(*in.ParentID)
				if err != nil {
					return err
				}
				if parent.ParentID != nil {
					return fmt.Errorf("子任务只能有一层：父待办 %s 自己也是子任务", *in.ParentID)
				}
				tasks, err := u.tasks()
				if err != nil {
					return err
				}
				for _, t := range tasks {
					if t.ParentID != nil && *t.ParentID == in.ID {
						return fmt.Errorf("%s 已有子任务，不能再变成别人的子任务", in.ID)
					}
				}
				m["parentId"] = *in.ParentID
			}
		}
		if in.BlockStart != nil {
			if *in.BlockStart == "" {
				m["blockStart"] = nil
				m["blockMinutes"] = nil
			} else if _, s, err := normalizeDateTime(*in.BlockStart); err != nil {
				return err
			} else {
				m["blockStart"] = s
			}
		}
		if in.BlockMinutes != nil {
			if *in.BlockMinutes <= 0 {
				m["blockMinutes"] = nil
			} else {
				if m["blockStart"] == nil {
					return fmt.Errorf("blockMinutes 需要先给 blockStart")
				}
				m["blockMinutes"] = *in.BlockMinutes
			}
		}
		if in.Tags != nil {
			m["tags"] = in.Tags
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	return map[string]any{"id": in.ID, "updated": true}, nil
}

// completeTask 完成 / 重开 / 放弃；与设备并发写入冲突时自动重读重试。重复完成是幂等的。
func (u *user) completeTask(_ context.Context, in completeTaskIn) (any, error) {
	err := u.withTask(in.ID, func(m map[string]any) error {
		done := in.Done == nil || *in.Done
		dropped := in.Dropped != nil && *in.Dropped
		m["done"] = done || dropped
		m["dropped"] = dropped
		if done || dropped {
			m["doneAt"] = time.Now().UnixMilli()
		} else {
			m["doneAt"] = nil
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	done := in.Done == nil || *in.Done
	dropped := in.Dropped != nil && *in.Dropped
	return map[string]any{"id": in.ID, "done": done || dropped, "dropped": dropped}, nil
}

// deleteTask 删除待办并级联删除存活的子任务（与 App 的删除语义一致）。
// 幂等：待办不存在或已是墓碑时直接返回成功，便于客户端安全重试。
func (u *user) deleteTask(_ context.Context, in idIn) (any, error) {
	children, err := u.childIDs(in.ID)
	if err != nil {
		return nil, err
	}
	cascaded := 0
	for attempt := 0; ; attempt++ {
		now := time.Now().UnixMilli()
		rec, err := u.st.Record(u.id, in.ID)
		if errors.Is(err, store.ErrNotFound) {
			break // 幂等：本来就不存在
		}
		if err != nil {
			return nil, err
		}
		if rec.Kind != "Task" {
			return nil, fmt.Errorf("记录 %s 的类型是 %s，不是 Task", in.ID, rec.Kind)
		}
		if rec.DeletedAt != nil {
			break // 幂等：已是墓碑
		}
		if err := u.pushTombstone("Task", rec, now); err != nil {
			if !errors.Is(err, errLWW) || attempt == 2 {
				return nil, err
			}
			continue // 设备刚写了新版本：重读后再删
		}
		// 子任务：设备先删的（LWW 拒绝）视为已删，跳过
		for _, cid := range children {
			crec, err := u.st.Record(u.id, cid)
			if err != nil || crec.Kind != "Task" || crec.DeletedAt != nil {
				continue
			}
			if err := u.pushTombstone("Task", crec, now); err == nil {
				cascaded++
			}
		}
		break
	}
	return map[string]any{"id": in.ID, "deleted": true, "cascaded": cascaded}, nil
}

// childIDs 返回待办的存活子任务 id。
func (u *user) childIDs(id string) ([]string, error) {
	tasks, err := u.tasks()
	if err != nil {
		return nil, err
	}
	out := []string{}
	for _, t := range tasks {
		if t.ParentID != nil && *t.ParentID == id {
			out = append(out, t.ID)
		}
	}
	return out, nil
}

func (u *user) getAgenda(_ context.Context, in agendaIn) (any, error) {
	from, err := parseDate(in.From)
	if err != nil {
		return nil, fmt.Errorf("from：%v", err)
	}
	to, err := parseDate(in.To)
	if err != nil {
		return nil, fmt.Errorf("to：%v", err)
	}
	if to.Before(from) {
		return nil, fmt.Errorf("to 早于 from")
	}
	type item struct {
		key  string
		body map[string]any
	}
	items := []item{}
	tasks, err := u.tasks()
	if err != nil {
		return nil, err
	}
	lists, _ := u.lists()
	names := map[string]string{}
	for _, l := range lists {
		names[l.ID] = l.Name
	}
	for _, t := range tasks {
		anchor, key := taskAnchor(t)
		if key == "" {
			continue
		}
		d := dateOf(key)
		if d < in.From || d > in.To {
			continue
		}
		b := taskBrief(t, names[strings.TrimSpace(deref(t.ListID))])
		b["anchor"] = anchor
		items = append(items, item{key, b})
	}
	recs, err := u.st.RecordsByKind(u.id, []string{"Event"}, false)
	if err != nil {
		return nil, err
	}
	for _, r := range recs {
		e, err := decode[eventJSON](r)
		if err != nil {
			continue
		}
		d := dateOf(e.Start)
		if d >= in.From && d <= in.To {
			items = append(items, item{e.Start, eventBrief(e)})
		}
	}
	sort.Slice(items, func(i, j int) bool { return items[i].key < items[j].key })
	out := make([]map[string]any, 0, len(items))
	for _, it := range items {
		out = append(out, it.body)
	}
	return map[string]any{"from": in.From, "to": in.To, "items": out, "total": len(out)}, nil
}

func (u *user) searchEvents(_ context.Context, in rangeIn) (any, error) {
	limit := in.Limit
	if limit <= 0 {
		limit = 100
	}
	q := strings.ToLower(strings.TrimSpace(in.Query))
	recs, err := u.st.RecordsByKind(u.id, []string{"Event"}, false)
	if err != nil {
		return nil, err
	}
	out := []map[string]any{}
	for _, r := range recs {
		e, err := decode[eventJSON](r)
		if err != nil {
			continue
		}
		if in.From != "" {
			d := dateOf(e.Start)
			if d < in.From {
				continue
			}
		}
		if in.To != "" {
			d := dateOf(e.Start)
			if d > in.To {
				continue
			}
		}
		if q != "" && !strings.Contains(strings.ToLower(e.Title+" "+e.Location+" "+e.Note), q) {
			continue
		}
		out = append(out, eventBrief(e))
		if len(out) >= limit {
			break
		}
	}
	sort.Slice(out, func(i, j int) bool {
		return out[i]["start"].(string) < out[j]["start"].(string)
	})
	return map[string]any{"events": out, "total": len(out)}, nil
}

func (u *user) listCourses(_ context.Context, _ struct{}) (any, error) {
	semRecs, err := u.st.RecordsByKind(u.id, []string{"Semester"}, false)
	if err != nil {
		return nil, err
	}
	semesters := []semesterJSON{}
	for _, r := range semRecs {
		if s, err := decode[semesterJSON](r); err == nil {
			semesters = append(semesters, s)
		}
	}
	courseRecs, err := u.st.RecordsByKind(u.id, []string{"Course"}, false)
	if err != nil {
		return nil, err
	}
	courses := []courseJSON{}
	for _, r := range courseRecs {
		if c, err := decode[courseJSON](r); err == nil {
			courses = append(courses, c)
		}
	}
	sort.Slice(courses, func(i, j int) bool {
		if courses[i].DayOfWeek != courses[j].DayOfWeek {
			return courses[i].DayOfWeek < courses[j].DayOfWeek
		}
		return courses[i].StartPeriod < courses[j].StartPeriod
	})
	return map[string]any{"semesters": semesters, "courses": courses}, nil
}

// ---------- 查找与校验 ----------

// withTask 读取待办 → 变换 → 推送；与设备并发写入冲突（LWW 拒绝）时重读最新版本重试，最多 3 次。
// 闭包里的校验错误直接返回，不触发重试。
func (u *user) withTask(
	id string,
	mutate func(m map[string]any) error,
) error {
	for attempt := 0; ; attempt++ {
		rec, err := u.record("Task", id)
		if err != nil {
			return err
		}
		var m map[string]any
		if err := json.Unmarshal(rec.Data, &m); err != nil {
			return err
		}
		if err := mutate(m); err != nil {
			return err
		}
		now := time.Now().UnixMilli()
		m["updatedAt"] = now
		err = u.push("Task", id, now, m)
		if err == nil || !errors.Is(err, errLWW) {
			return err
		}
		if attempt == 2 {
			return fmt.Errorf("多次重试后仍与设备写入冲突：%s", id)
		}
	}
}

func (u *user) record(kind, id string) (store.Record, error) {
	r, err := u.st.Record(u.id, id)
	if err != nil {
		return store.Record{}, fmt.Errorf("记录不存在：%s", id)
	}
	if r.Kind != kind {
		return store.Record{}, fmt.Errorf("记录 %s 的类型是 %s，不是 %s", id, r.Kind, kind)
	}
	if r.DeletedAt != nil {
		return store.Record{}, fmt.Errorf("记录已删除：%s", id)
	}
	return r, nil
}

// findTask 返回未删除的待办与其清单名。
func (u *user) findTask(id string) (taskJSON, string, error) {
	rec, err := u.record("Task", id)
	if err != nil {
		return taskJSON{}, "", err
	}
	t, err := decode[taskJSON](rec)
	if err != nil {
		return taskJSON{}, "", err
	}
	listName := ""
	if t.ListID != nil {
		if l, err := u.findList(*t.ListID); err == nil {
			listName = l.Name
		}
	}
	return t, listName, nil
}

func (u *user) findList(id string) (listJSON, error) {
	rec, err := u.record("TaskList", id)
	if err != nil {
		return listJSON{}, fmt.Errorf("清单不存在：%s", id)
	}
	return decode[listJSON](rec)
}

// repeatIn → core Recurrence JSON；freq=NONE 返回 nil（清除）。
func repeatJSON(in *repeatIn) (map[string]any, error) {
	if in == nil {
		return nil, nil
	}
	if in.Freq == "NONE" {
		return nil, nil
	}
	switch in.Freq {
	case "DAILY", "WEEKLY", "MONTHLY", "YEARLY":
	default:
		return nil, fmt.Errorf("repeat.freq 应为 DAILY / WEEKLY / MONTHLY / YEARLY / NONE")
	}
	interval := in.Interval
	if interval <= 0 {
		interval = 1
	}
	r := map[string]any{"freq": in.Freq, "interval": interval, "byDays": []any{}, "until": nil, "count": nil, "exDates": []any{}}
	if in.ByDays != nil {
		if in.Freq != "WEEKLY" {
			return nil, fmt.Errorf("repeat.byDays 仅 WEEKLY 支持")
		}
		for _, d := range in.ByDays {
			if d < 1 || d > 7 {
				return nil, fmt.Errorf("repeat.byDays 取值 1（周一）到 7（周日）")
			}
			r["byDays"] = append(r["byDays"].([]any), d)
		}
	}
	if in.Until != "" {
		if _, s, err := normalizeDateTime(in.Until); err != nil {
			return nil, fmt.Errorf("repeat.until：%v", err)
		} else {
			r["until"] = s
		}
	}
	if in.Count > 0 {
		r["count"] = in.Count
	}
	return r, nil
}

// ---------- 过滤与展示 ----------

func deref[T any](p *T) T {
	var zero T
	if p == nil {
		return zero
	}
	return *p
}

func clampInt(v, lo, hi int) int {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}

func matchStatus(t taskJSON, status string) bool {
	switch status {
	case "open":
		return !t.Done
	case "done":
		return t.Done && !t.Dropped
	case "dropped":
		return t.Dropped
	default: // all
		return true
	}
}

func matchText(t taskJSON, q string) bool {
	if q == "" {
		return true
	}
	hay := strings.ToLower(t.Title + " " + t.Note + " " + strings.Join(t.Tags, " "))
	return strings.Contains(hay, q)
}

func matchList(t taskJSON, listID string) bool {
	return listID == "" || deref(t.ListID) == listID
}

// matchDate 按「截止 ?? 开始 ?? 排期」的锚点日期过滤；锚点为空且给了范围时视为不匹配。
func matchDate(t taskJSON, from, to time.Time) bool {
	if from.IsZero() && to.IsZero() {
		return true
	}
	anchor := ""
	switch {
	case t.Due != nil:
		anchor = dateOf(*t.Due)
	case t.StartDate != nil:
		anchor = *t.StartDate
	case t.BlockStart != nil:
		anchor = dateOf(*t.BlockStart)
	}
	if anchor == "" {
		return false
	}
	d, err := parseDate(anchor)
	if err != nil {
		return false
	}
	if !from.IsZero() && d.Before(from) {
		return false
	}
	if !to.IsZero() && d.After(to) {
		return false
	}
	return true
}

// taskAnchor 返回待办在时间轴上的锚点（blockStart ?? due ?? startDate）与排序键；
// 纯日期锚点排到当天末尾（T23:59）。
func taskAnchor(t taskJSON) (anchor, key string) {
	switch {
	case t.BlockStart != nil:
		return "block", *t.BlockStart
	case t.Due != nil && t.DueHasTime:
		return "due", *t.Due
	case t.Due != nil:
		return "due", dateOf(*t.Due) + "T23:59"
	case t.StartDate != nil:
		return "start", *t.StartDate + "T23:59"
	}
	return "", ""
}

func taskBrief(t taskJSON, listName string) map[string]any {
	m := map[string]any{
		"id":        t.ID,
		"title":     t.Title,
		"listId":    t.ListID,
		"listName":  listName,
		"priority":  t.Priority,
		"done":      t.Done,
		"dropped":   t.Dropped,
		"updatedAt": t.UpdatedAt,
	}
	if t.Note != "" {
		m["note"] = t.Note
	}
	if len(t.Tags) > 0 {
		m["tags"] = t.Tags
	}
	if t.StartDate != nil {
		m["startDate"] = *t.StartDate
	}
	if t.Due != nil {
		m["due"] = *t.Due
		m["dueHasTime"] = t.DueHasTime
	}
	if t.RemindMinutes != nil {
		m["remindMinutes"] = *t.RemindMinutes
	}
	if t.Repeat != nil {
		m["repeat"] = t.Repeat
	}
	if t.ParentID != nil {
		m["parentId"] = *t.ParentID
	}
	if t.BlockStart != nil {
		m["blockStart"] = *t.BlockStart
		m["blockMinutes"] = deref(t.BlockMinutes)
	}
	return m
}

func eventBrief(e eventJSON) map[string]any {
	m := map[string]any{
		"id": e.ID, "title": e.Title, "start": e.Start, "end": e.End,
		"allDay": e.AllDay, "kind": e.Kind, "updatedAt": e.UpdatedAt,
	}
	if e.Location != "" {
		m["location"] = e.Location
	}
	if e.Note != "" {
		m["note"] = e.Note
	}
	if e.Recurrence != nil {
		m["recurrence"] = e.Recurrence
	}
	return m
}
