package server

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
	"time"

	"github.com/xihale/goke/server/internal/store"
)

type shareEnv struct {
	t   *testing.T
	st  *store.Store
	srv *Server
	h   http.Handler
	now time.Time
}

func newShareEnv(t *testing.T, opt Options) *shareEnv {
	t.Helper()
	st, err := store.Open(filepath.Join(t.TempDir(), "share.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { st.Close() })
	s := New(st, opt)
	se := &shareEnv{
		t:   t,
		st:  st,
		srv: s,
		h:   s.Handler(),
		now: time.Date(2026, 9, 1, 10, 0, 0, 0, time.UTC),
	}
	s.now = func() time.Time { return se.now }
	return se
}

func (e *shareEnv) req(method, path string, headers map[string]string, body any) *httptest.ResponseRecorder {
	e.t.Helper()
	var rd *bytes.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			e.t.Fatal(err)
		}
		rd = bytes.NewReader(b)
	} else {
		rd = bytes.NewReader(nil)
	}
	r := httptest.NewRequest(method, path, rd)
	r.RemoteAddr = "10.0.0.2:4321"
	r.Host = "share.goke.local"
	for k, v := range headers {
		r.Header.Set(k, v)
	}
	rec := httptest.NewRecorder()
	e.h.ServeHTTP(rec, r)
	return rec
}

func (e *shareEnv) reqJSON(method, path string, body any) (int, map[string]any, string) {
	rec := e.req(method, path, map[string]string{"Content-Type": "application/json"}, body)
	var out map[string]any
	_ = json.Unmarshal(rec.Body.Bytes(), &out)
	return rec.Code, out, rec.Body.String()
}

func sampleTimetable(week1Monday string, totalWeeks int) map[string]any {
	return map[string]any{
		"semester": map[string]any{
			"id":          "sem-1",
			"name":        "2026-2027 秋季学期",
			"week1Monday": week1Monday,
			"totalWeeks":  totalWeeks,
			"periods": []map[string]any{
				{"index": 1, "start": "08:30", "end": "09:15"},
				{"index": 2, "start": "09:20", "end": "10:05"},
			},
		},
		"courses": []map[string]any{
			{
				"id":          "c-1",
				"semesterId":  "sem-1",
				"name":        "高等数学",
				"teacher":     "张老师",
				"room":        "教一-201",
				"dayOfWeek":   1,
				"startPeriod": 1,
				"endPeriod":   2,
				"weeks":       []int{1, 2, 3, 4},
				"colorIndex":  0,
			},
		},
		"exceptions": []map[string]any{},
	}
}

func TestShareLifecycleAndPrivacy(t *testing.T) {
	e := newShareEnv(t, Options{ShareEnabled: true, ShareMaxDays: 210})
	exp := e.now.Add(30 * 24 * time.Hour).UnixMilli()
	studentID := "202600012345"

	// 1. Create share
	code, out, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId": studentID,
		"name":      "张三的课表",
		"expiresAt": exp,
		"timetable": sampleTimetable("2026-08-31", 18),
	})
	if code != 200 {
		t.Fatalf("create want 200 got %d: %v", code, out)
	}
	shareCode, _ := out["code"].(string)
	if !regexp.MustCompile(`^[0-9]{8}$`).MatchString(shareCode) {
		t.Fatalf("expected 8-digit share code, got %q", shareCode)
	}
	manageToken, _ := out["manageToken"].(string)
	if len(manageToken) != 64 {
		t.Fatalf("expected 64-char hex manageToken, got %q", manageToken)
	}
	if out["experimental"] != true {
		t.Fatalf("expected experimental=true, got %v", out["experimental"])
	}
	if out["shareUrl"] != "http://share.goke.local/s/"+shareCode {
		t.Fatalf("unexpected shareUrl: %v", out["shareUrl"])
	}

	// 2. Wrong/missing manageToken -> 403
	if c, _, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId": studentID,
		"name":      "恶意覆盖",
		"expiresAt": exp,
		"timetable": sampleTimetable("2026-08-31", 18),
	}); c != 403 {
		t.Fatalf("missing token want 403 got %d", c)
	}
	if c, _, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId":   studentID,
		"manageToken": "wrong-token",
		"name":        "恶意覆盖",
		"expiresAt":   exp,
		"timetable":   sampleTimetable("2026-08-31", 18),
	}); c != 403 {
		t.Fatalf("wrong token want 403 got %d", c)
	}

	// 3. 1:1 overwrite with matching manageToken preserves shareCode
	code, out, _ = e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId":   studentID,
		"manageToken": manageToken,
		"name":        "张三的新课表",
		"expiresAt":   exp,
		"timetable":   sampleTimetable("2026-08-31", 18),
	})
	if code != 200 {
		t.Fatalf("overwrite want 200 got %d: %v", code, out)
	}
	if out["code"] != shareCode {
		t.Fatalf("overwrite must preserve code %s, got %v", shareCode, out["code"])
	}
	if _, hasTok := out["manageToken"]; hasTok {
		t.Fatalf("overwrite must not reissue manageToken")
	}

	// 4. Status endpoint
	if c, _, _ := e.reqJSON("POST", "/api/v1/share/status", map[string]any{
		"studentId":   "unknown-id",
		"manageToken": manageToken,
	}); c != 200 {
		t.Fatalf("status unknown want 200 got %d", c)
	}
	if c, _, _ := e.reqJSON("POST", "/api/v1/share/status", map[string]any{
		"studentId":   studentID,
		"manageToken": "bad",
	}); c != 403 {
		t.Fatalf("status bad token want 403 got %d", c)
	}
	c, stOut, _ := e.reqJSON("POST", "/api/v1/share/status", map[string]any{
		"studentId":   studentID,
		"manageToken": manageToken,
	})
	if c != 200 || stOut["exists"] != true || stOut["code"] != shareCode || stOut["name"] != "张三的新课表" {
		t.Fatalf("status matched failed: %d %v", c, stOut)
	}

	// 5. Public GET JSON + privacy check
	pubRec := e.req("GET", "/api/v1/share/"+shareCode, nil, nil)
	if pubRec.Code != 200 {
		t.Fatalf("public GET want 200 got %d", pubRec.Code)
	}
	if pubRec.Header().Get("Access-Control-Allow-Origin") != "*" {
		t.Fatalf("missing CORS header")
	}
	pubBody := pubRec.Body.String()
	for _, secret := range []string{studentID, HashToken(studentID), manageToken, HashToken(manageToken), "studentId", "student_id_hash", "manage_hash"} {
		if strings.Contains(pubBody, secret) {
			t.Fatalf("public JSON leaked sensitive string %q", secret)
		}
	}
	var pubJSON map[string]any
	if err := json.Unmarshal(pubRec.Body.Bytes(), &pubJSON); err != nil {
		t.Fatal(err)
	}
	if pubJSON["schema"] != "goke.timetable.v1" || pubJSON["name"] != "张三的新课表" {
		t.Fatalf("unexpected public JSON: %v", pubJSON)
	}

	// 6. Delete + recreate gets new code & token
	if c, _, _ := e.reqJSON("DELETE", "/api/v1/share", map[string]any{
		"studentId":   studentID,
		"manageToken": "wrong",
	}); c != 403 {
		t.Fatalf("delete wrong token want 403 got %d", c)
	}
	c, delOut, _ := e.reqJSON("DELETE", "/api/v1/share", map[string]any{
		"studentId":   studentID,
		"manageToken": manageToken,
	})
	if c != 200 || delOut["deleted"] != true {
		t.Fatalf("delete failed: %d %v", c, delOut)
	}
	if r := e.req("GET", "/api/v1/share/"+shareCode, nil, nil); r.Code != 404 {
		t.Fatalf("deleted share should return 404, got %d", r.Code)
	}
	c, out2, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId": studentID,
		"name":      "重新创建",
		"expiresAt": exp,
		"timetable": sampleTimetable("2026-08-31", 18),
	})
	if c != 200 || out2["manageToken"] == "" || out2["manageToken"] == manageToken {
		t.Fatalf("recreate should issue new manageToken, got %d %v", c, out2)
	}
}

func TestShareExpiryAndValidation(t *testing.T) {
	e := newShareEnv(t, Options{ShareEnabled: true, ShareMaxDays: 210})

	// Fall semester: week1Monday 2026-08-31, 18 weeks -> semEnd 2027-01-04, +6w = 2027-02-15, +1d = 2027-02-16
	maxFall := time.Date(2027, 2, 16, 0, 0, 0, 0, time.UTC).UnixMilli()
	if c, _, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId": "s-cap",
		"name":      "超期测试",
		"expiresAt": maxFall + 1000,
		"timetable": sampleTimetable("2026-08-31", 18),
	}); c != 400 {
		t.Fatalf("expiry > maxAllowed want 400 got %d", c)
	}
	if c, _, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId": "s-past",
		"name":      "过去时间",
		"expiresAt": e.now.UnixMilli() - 1000,
		"timetable": sampleTimetable("2026-08-31", 18),
	}); c != 400 {
		t.Fatalf("expiry <= now want 400 got %d", c)
	}

	// Create valid short-lived share, then advance clock past expiry
	exp := e.now.Add(24 * time.Hour).UnixMilli()
	c, out, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId": "s-exp",
		"name":      "即将过期",
		"expiresAt": exp,
		"timetable": sampleTimetable("2026-08-31", 18),
	})
	if c != 200 {
		t.Fatalf("create want 200 got %d", c)
	}
	code := out["code"].(string)

	e.now = e.now.Add(25 * time.Hour)
	if r := e.req("GET", "/api/v1/share/"+code, nil, nil); r.Code != 404 {
		t.Fatalf("expired share want 404 got %d", r.Code)
	}
	// Recreate after expiry without old manageToken
	c, out2, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId": "s-exp",
		"name":      "过期后重建",
		"expiresAt": e.now.Add(48 * time.Hour).UnixMilli(),
		"timetable": sampleTimetable("2026-08-31", 18),
	})
	if c != 200 || out2["manageToken"] == "" {
		t.Fatalf("recreate after expiry failed: %d %v", c, out2)
	}

	// Past semester clamps capBySem to at least now + 24h
	if c, _, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId": "s-old-ok",
		"name":      "往期课表",
		"expiresAt": e.now.Add(12 * time.Hour).UnixMilli(),
		"timetable": sampleTimetable("2020-02-24", 16),
	}); c != 200 {
		t.Fatalf("past semester within 24h clamp want 200 got %d", c)
	}
	if c, _, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId": "s-old-over",
		"name":      "往期课表超限",
		"expiresAt": e.now.Add(25 * time.Hour).UnixMilli(),
		"timetable": sampleTimetable("2020-02-24", 16),
	}); c != 400 {
		t.Fatalf("past semester > 24h clamp want 400 got %d", c)
	}

	// Oversized courses (>300)
	manyCourses := make([]map[string]any, 301)
	for i := range manyCourses {
		manyCourses[i] = map[string]any{"id": fmt.Sprintf("c-%d", i), "name": "课", "dayOfWeek": 1, "startPeriod": 1, "endPeriod": 2, "weeks": []int{1}}
	}
	if c, _, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId": "s-many",
		"name":      "太多课",
		"expiresAt": e.now.Add(24 * time.Hour).UnixMilli(),
		"timetable": map[string]any{
			"semester": map[string]any{"name": "S", "week1Monday": "2026-08-31", "totalWeeks": 18},
			"courses":  manyCourses,
		},
	}); c != 400 {
		t.Fatalf("courses > 300 want 400 got %d", c)
	}

	// Empty display name is allowed, while >64 runes is rejected
	cEmpty, outEmpty, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId": "s-empty-name",
		"name":      "   ",
		"expiresAt": e.now.Add(24 * time.Hour).UnixMilli(),
		"timetable": sampleTimetable("2026-08-31", 18),
	})
	if cEmpty != 200 || outEmpty["name"] != "" {
		t.Fatalf("empty name want 200 and empty name got %d %v", cEmpty, outEmpty)
	}
	emptyCode := outEmpty["code"].(string)
	rEmpty := e.req("GET", "/api/v1/share/"+emptyCode, nil, nil)
	if rEmpty.Code != 200 {
		t.Fatalf("GET empty name share want 200 got %d", rEmpty.Code)
	}
	var gotEmpty map[string]any
	if err := json.Unmarshal(rEmpty.Body.Bytes(), &gotEmpty); err != nil || gotEmpty["name"] != "" {
		t.Fatalf("GET empty name share want empty name got %v (%v)", gotEmpty, err)
	}

	if cLong, _, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId": "s-long-name",
		"name":      strings.Repeat("课", 65),
		"expiresAt": e.now.Add(24 * time.Hour).UnixMilli(),
		"timetable": sampleTimetable("2026-08-31", 18),
	}); cLong != 400 {
		t.Fatalf("name > 64 runes want 400 got %d", cLong)
	}
}

func TestShareContentNegotiationAndViewer(t *testing.T) {
	e := newShareEnv(t, Options{ShareEnabled: true, ShareMaxDays: 210, SharePublicURL: "https://goke.example.com"})
	exp := e.now.Add(10 * 24 * time.Hour).UnixMilli()
	c, out, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{
		"studentId": "s-web",
		"name":      "李四",
		"expiresAt": exp,
		"timetable": sampleTimetable("2026-08-31", 18),
	})
	if c != 200 {
		t.Fatalf("create failed: %d", c)
	}
	code := out["code"].(string)
	if out["shareUrl"] != "https://goke.example.com/s/"+code {
		t.Fatalf("SharePublicURL override failed: %v", out["shareUrl"])
	}

	// Browser Accept -> 302
	rBrowser := e.req("GET", "/s/"+code, map[string]string{"Accept": "text/html,application/xhtml+xml"}, nil)
	if rBrowser.Code != 302 || rBrowser.Header().Get("Location") != "/s/view?code="+code {
		t.Fatalf("browser redirect failed: %d %s", rBrowser.Code, rBrowser.Header().Get("Location"))
	}
	if !strings.Contains(rBrowser.Header().Get("Vary"), "Accept") {
		t.Fatalf("missing Vary: Accept")
	}

	// Sec-Fetch-Dest: document with */* -> 302
	rNav := e.req("GET", "/s/"+code, map[string]string{"Accept": "*/*", "Sec-Fetch-Dest": "document"}, nil)
	if rNav.Code != 302 {
		t.Fatalf("Sec-Fetch-Dest document want 302 got %d", rNav.Code)
	}

	// Accept: application/json -> 200 JSON + CORS
	rJSON := e.req("GET", "/s/"+code, map[string]string{"Accept": "application/json"}, nil)
	if rJSON.Code != 200 || rJSON.Header().Get("Access-Control-Allow-Origin") != "*" {
		t.Fatalf("Accept json failed: %d", rJSON.Code)
	}

	// Accept: */* (curl) -> 200 JSON
	rCurl := e.req("GET", "/s/"+code, map[string]string{"Accept": "*/*"}, nil)
	if rCurl.Code != 200 {
		t.Fatalf("Accept */* want 200 got %d", rCurl.Code)
	}

	// ?format=json overrides browser Accept
	rFmt := e.req("GET", "/s/"+code+"?format=json", map[string]string{"Accept": "text/html"}, nil)
	if rFmt.Code != 200 {
		t.Fatalf("?format=json want 200 got %d", rFmt.Code)
	}

	// OPTIONS CORS & 404 CORS on /s/{code}
	rOpt := e.req("OPTIONS", "/s/"+code, nil, nil)
	if rOpt.Code != 204 || rOpt.Header().Get("Access-Control-Allow-Origin") != "*" {
		t.Fatalf("OPTIONS failed: %d", rOpt.Code)
	}
	r404 := e.req("GET", "/s/99999999", map[string]string{"Accept": "application/json"}, nil)
	if r404.Code != 404 || r404.Header().Get("Access-Control-Allow-Origin") != "*" {
		t.Fatalf("404 CORS failed: %d %q", r404.Code, r404.Header().Get("Access-Control-Allow-Origin"))
	}

	// /s/view HTML & assets
	rView := e.req("GET", "/s/view?code="+code, nil, nil)
	if rView.Code != 200 || !strings.Contains(rView.Body.String(), "/s/assets/share.js") {
		t.Fatalf("/s/view failed: %d", rView.Code)
	}
	if strings.Contains(rView.Body.String(), "s-web") {
		t.Fatalf("/s/view leaked studentId")
	}
	if rCSS := e.req("GET", "/s/assets/share.css", nil, nil); rCSS.Code != 200 {
		t.Fatalf("/s/assets/share.css failed: %d", rCSS.Code)
	}
	if rJS := e.req("GET", "/s/assets/share.js", nil, nil); rJS.Code != 200 {
		t.Fatalf("/s/assets/share.js failed: %d", rJS.Code)
	}
}

func TestShareDisabled(t *testing.T) {
	e := newShareEnv(t, Options{ShareEnabled: false})
	if c, _, _ := e.reqJSON("PUT", "/api/v1/share", map[string]any{}); c != 503 {
		t.Fatalf("disabled PUT want 503 got %d", c)
	}
	if r := e.req("GET", "/api/v1/share/12345678", nil, nil); r.Code != 503 {
		t.Fatalf("disabled GET API want 503 got %d", r.Code)
	}
	if r := e.req("GET", "/s/12345678", nil, nil); r.Code != 503 || r.Header().Get("Access-Control-Allow-Origin") != "*" {
		t.Fatalf("disabled GET /s/ want 503 + CORS got %d %q", r.Code, r.Header().Get("Access-Control-Allow-Origin"))
	}
	if r := e.req("GET", "/s/view?code=12345678", nil, nil); r.Code != 503 {
		t.Fatalf("disabled GET /s/view want 503 got %d", r.Code)
	}
}
