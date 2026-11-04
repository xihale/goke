package mcp

import (
	"encoding/json"
	"os"
	"testing"

	"github.com/xihale/mintcal/server/internal/store"
)

// TestReadsRealFixture 用 App / Web 真实产生的记录回放：Go 侧读取视图必须能解一切已知字段形状。
// 这是 JSON 结构漂移的回归闸门——core 模型加了字段而 Go 视图没跟上时，这里失败。
// fixture 由 server/webtest/gen-fixture.mjs 生成（core/src/test/resources/web-records.json）。
func TestReadsRealFixture(t *testing.T) {
	raw, err := os.ReadFile("../../../core/src/test/resources/web-records.json")
	if err != nil {
		t.Skipf("找不到 fixture：%v", err)
	}
	var recs []store.Record
	if err := json.Unmarshal(raw, &recs); err != nil {
		t.Fatal(err)
	}
	if len(recs) == 0 {
		t.Fatal("fixture 为空")
	}
	seen := map[string]bool{}
	for _, r := range recs {
		seen[r.Kind] = true
		switch r.Kind {
		case "Task":
			var v taskJSON
			if err := json.Unmarshal(r.Data, &v); err != nil {
				t.Fatalf("Task %s 解码失败：%v", r.ID, err)
			}
			if v.ID != r.ID {
				t.Fatalf("Task %s 的 id 字段不一致：%s", r.ID, v.ID)
			}
		case "TaskList":
			var v listJSON
			if err := json.Unmarshal(r.Data, &v); err != nil {
				t.Fatalf("TaskList %s 解码失败：%v", r.ID, err)
			}
		case "Event":
			var v eventJSON
			if err := json.Unmarshal(r.Data, &v); err != nil {
				t.Fatalf("Event %s 解码失败：%v", r.ID, err)
			}
		case "Course":
			var v courseJSON
			if err := json.Unmarshal(r.Data, &v); err != nil {
				t.Fatalf("Course %s 解码失败：%v", r.ID, err)
			}
		case "Semester":
			var v semesterJSON
			if err := json.Unmarshal(r.Data, &v); err != nil {
				t.Fatalf("Semester %s 解码失败：%v", r.ID, err)
			}
		case "CourseException":
			// 尚无读取视图；MCP 工具也不消费它
		default:
			t.Fatalf("fixture 出现未知 kind：%s", r.Kind)
		}
	}
	for _, k := range []string{"Task", "TaskList", "Semester", "Course"} {
		if !seen[k] {
			t.Fatalf("fixture 缺少 %s 记录，回放没有覆盖到", k)
		}
	}
}
