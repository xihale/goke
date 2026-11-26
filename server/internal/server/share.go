package server

import (
	"bytes"
	"compress/gzip"
	"crypto/rand"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"regexp"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/xihale/goke/server/internal/store"
)

const (
	defaultShareMaxDays    = 210
	maxShareTimetableBytes = 256 * 1024
	maxShareCourses        = 300
)

var shareCodeRe = regexp.MustCompile(`^[0-9]{6,12}$`)

type shareUpsertReq struct {
	StudentID   string          `json:"studentId"`
	ManageToken string          `json:"manageToken"`
	Name        string          `json:"name"`
	ExpiresAt   int64           `json:"expiresAt"`
	Timetable   json.RawMessage `json:"timetable"`
}

type shareManageReq struct {
	StudentID   string `json:"studentId"`
	ManageToken string `json:"manageToken"`
}

type shareTimetablePayload struct {
	Semester   shareSemester     `json:"semester"`
	Courses    []json.RawMessage `json:"courses"`
	Exceptions []json.RawMessage `json:"exceptions"`
}

type shareSemester struct {
	Name        string `json:"name"`
	Week1Monday string `json:"week1Monday"`
	TotalWeeks  int    `json:"totalWeeks"`
}

type shareTimetableRaw struct {
	Semester   json.RawMessage `json:"semester"`
	Courses    json.RawMessage `json:"courses"`
	Exceptions json.RawMessage `json:"exceptions"`
}

func (s *Server) shareEnabledOr503(w http.ResponseWriter) bool {
	if !s.shareEnabled {
		writeErr(w, http.StatusServiceUnavailable, "share_disabled", "课表分享功能未启用")
		return false
	}
	return true
}

func (s *Server) shareRateLimit(w http.ResponseWriter, r *http.Request) (string, bool) {
	ip := s.clientIP(r)
	if s.shareFailLimiter.blocked(ip) || s.shareWriteLimiter.blocked(ip) {
		writeErr(w, http.StatusTooManyRequests, "too_many_attempts", "操作过于频繁，请稍后再试")
		return ip, false
	}
	s.shareWriteLimiter.fail(ip)
	return ip, true
}

func (s *Server) buildShareURL(r *http.Request, code string) string {
	if s.sharePublicURL != "" {
		return s.sharePublicURL + "/s/" + code
	}
	host := r.Host
	scheme := "http"
	if r.TLS != nil {
		scheme = "https"
	}
	if s.trustProxy {
		if xfHost := strings.TrimSpace(strings.Split(r.Header.Get("X-Forwarded-Host"), ",")[0]); xfHost != "" {
			host = xfHost
		}
		if xfProto := strings.TrimSpace(strings.Split(r.Header.Get("X-Forwarded-Proto"), ",")[0]); xfProto != "" {
			scheme = xfProto
		}
	}
	if host == "" {
		return "/s/" + code
	}
	return scheme + "://" + host + "/s/" + code
}

func randomShareCode() (string, error) {
	n, err := rand.Int(rand.Reader, big.NewInt(90000000))
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%08d", n.Int64()+10000000), nil
}

func randomManageToken() (string, error) {
	var b [32]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	return hex.EncodeToString(b[:]), nil
}

func tokensMatch(plain, expectedHash string) bool {
	if plain == "" || expectedHash == "" {
		return false
	}
	got := HashToken(plain)
	return subtle.ConstantTimeCompare([]byte(got), []byte(expectedHash)) == 1
}

func gzipBytes(data []byte) ([]byte, error) {
	var buf bytes.Buffer
	gw := gzip.NewWriter(&buf)
	if _, err := gw.Write(data); err != nil {
		return nil, err
	}
	if err := gw.Close(); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

func gunzipBytes(data []byte) ([]byte, error) {
	gr, err := gzip.NewReader(bytes.NewReader(data))
	if err != nil {
		return nil, err
	}
	defer gr.Close()
	return io.ReadAll(io.LimitReader(gr, maxShareTimetableBytes+1024))
}

func setShareCORS(w http.ResponseWriter) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Access-Control-Allow-Methods", "GET, OPTIONS")
	w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Accept")
	w.Header().Set("Cache-Control", "no-store")
}

func (s *Server) handleShareUpsert(w http.ResponseWriter, r *http.Request) {
	if !s.shareEnabledOr503(w) {
		return
	}
	ip, ok := s.shareRateLimit(w, r)
	if !ok {
		return
	}
	var req shareUpsertReq
	if !readJSON(w, r, &req) {
		return
	}
	studentID := strings.TrimSpace(req.StudentID)
	if studentID == "" || utf8.RuneCountInString(studentID) > 128 {
		writeErr(w, http.StatusBadRequest, "invalid_student_id", "请提供有效的学号")
		return
	}
	name := strings.TrimSpace(req.Name)
	nameLen := utf8.RuneCountInString(name)
	if nameLen < 1 || nameLen > 64 {
		writeErr(w, http.StatusBadRequest, "invalid_name", "展示名称长度需为 1 到 64 个字符")
		return
	}

	rawTrim := bytes.TrimSpace(req.Timetable)
	if len(rawTrim) == 0 || bytes.Equal(rawTrim, []byte("null")) {
		writeErr(w, http.StatusBadRequest, "invalid_timetable", "缺少课表数据")
		return
	}
	if len(rawTrim) > maxShareTimetableBytes {
		writeErr(w, http.StatusBadRequest, "timetable_too_large", "课表数据超出 256KB 限制")
		return
	}
	var compact bytes.Buffer
	if err := json.Compact(&compact, rawTrim); err != nil {
		writeErr(w, http.StatusBadRequest, "invalid_timetable", "课表数据格式不正确")
		return
	}
	if compact.Len() > maxShareTimetableBytes {
		writeErr(w, http.StatusBadRequest, "timetable_too_large", "课表数据超出 256KB 限制")
		return
	}

	var tt shareTimetablePayload
	if err := json.Unmarshal(compact.Bytes(), &tt); err != nil {
		writeErr(w, http.StatusBadRequest, "invalid_timetable", "课表数据格式不正确")
		return
	}
	w1mStr := strings.TrimSpace(tt.Semester.Week1Monday)
	w1m, err := time.Parse("2006-01-02", w1mStr)
	if err != nil {
		writeErr(w, http.StatusBadRequest, "invalid_semester", "第 1 周周一日期格式需为 YYYY-MM-DD")
		return
	}
	if tt.Semester.TotalWeeks < 1 || tt.Semester.TotalWeeks > 30 {
		writeErr(w, http.StatusBadRequest, "invalid_semester", "学期总周数需在 1 到 30 之间")
		return
	}
	if len(tt.Courses) > maxShareCourses {
		writeErr(w, http.StatusBadRequest, "too_many_courses", "课程数量不能超过 300 门")
		return
	}

	nowT := s.now()
	nowMs := nowT.UnixMilli()
	semEnd := w1m.AddDate(0, 0, tt.Semester.TotalWeeks*7)
	nextSemStart := semEnd.AddDate(0, 0, 8*7)
	if m := w1m.Month(); m >= time.August && m <= time.December {
		nextSemStart = semEnd.AddDate(0, 0, 6*7)
	}
	capBySem := nextSemStart.AddDate(0, 0, 1).UnixMilli()
	capByDays := nowT.Add(time.Duration(s.shareMaxDays) * 24 * time.Hour).UnixMilli()
	maxAllowed := capBySem
	if capByDays < maxAllowed {
		maxAllowed = capByDays
	}
	if req.ExpiresAt <= nowMs || req.ExpiresAt > maxAllowed {
		writeErr(w, http.StatusBadRequest, "invalid_expiry", "分享有效期超出允许范围")
		return
	}

	gz, err := gzipBytes(compact.Bytes())
	if err != nil {
		internalErr(w, err)
		return
	}

	studentHash := HashToken(studentID)
	semName := strings.TrimSpace(tt.Semester.Name)
	existing, err := s.st.GetShareByStudentHash(studentHash)
	if err != nil && !errors.Is(err, store.ErrNotFound) {
		internalErr(w, err)
		return
	}

	active := err == nil && existing.ExpiresAt > nowMs
	if active {
		if !tokensMatch(req.ManageToken, existing.ManageHash) {
			s.shareFailLimiter.fail(ip)
			writeErr(w, http.StatusForbidden, "forbidden", "该学号已存在云端课表，管理密钥不匹配")
			return
		}
		s.shareFailLimiter.reset(ip)
		updated := store.SharedTimetable{
			StudentIDHash: studentHash,
			ShareCode:     existing.ShareCode,
			ManageHash:    existing.ManageHash,
			DisplayName:   name,
			SemesterName:  semName,
			PayloadGz:     gz,
			PayloadSize:   compact.Len(),
			CreatedAt:     existing.CreatedAt,
			UpdatedAt:     nowMs,
			ExpiresAt:     req.ExpiresAt,
		}
		if err := s.st.UpsertShare(updated); err != nil {
			internalErr(w, err)
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{
			"code":         updated.ShareCode,
			"shareUrl":     s.buildShareURL(r, updated.ShareCode),
			"name":         updated.DisplayName,
			"updatedAt":    updated.UpdatedAt,
			"expiresAt":    updated.ExpiresAt,
			"experimental": true,
		})
		return
	}

	manageToken, err := randomManageToken()
	if err != nil {
		internalErr(w, err)
		return
	}
	manageHash := HashToken(manageToken)

	var shareCode string
	for i := 0; i < 8; i++ {
		code, err := randomShareCode()
		if err != nil {
			internalErr(w, err)
			return
		}
		rec := store.SharedTimetable{
			StudentIDHash: studentHash,
			ShareCode:     code,
			ManageHash:    manageHash,
			DisplayName:   name,
			SemesterName:  semName,
			PayloadGz:     gz,
			PayloadSize:   compact.Len(),
			CreatedAt:     nowMs,
			UpdatedAt:     nowMs,
			ExpiresAt:     req.ExpiresAt,
		}
		err = s.st.UpsertShare(rec)
		if errors.Is(err, store.ErrExists) {
			continue
		}
		if err != nil {
			internalErr(w, err)
			return
		}
		shareCode = code
		break
	}
	if shareCode == "" {
		internalErr(w, errors.New("failed to allocate unique share code"))
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"code":         shareCode,
		"shareUrl":     s.buildShareURL(r, shareCode),
		"manageToken":  manageToken,
		"name":         name,
		"updatedAt":    nowMs,
		"expiresAt":    req.ExpiresAt,
		"experimental": true,
	})
}

func (s *Server) handleShareStatus(w http.ResponseWriter, r *http.Request) {
	if !s.shareEnabledOr503(w) {
		return
	}
	ip, ok := s.shareRateLimit(w, r)
	if !ok {
		return
	}
	var req shareManageReq
	if !readJSON(w, r, &req) {
		return
	}
	studentID := strings.TrimSpace(req.StudentID)
	if studentID == "" {
		writeErr(w, http.StatusBadRequest, "invalid_student_id", "请提供有效的学号")
		return
	}
	studentHash := HashToken(studentID)
	existing, err := s.st.GetShareByStudentHash(studentHash)
	if errors.Is(err, store.ErrNotFound) {
		writeJSON(w, http.StatusOK, map[string]any{"exists": false})
		return
	}
	if err != nil {
		internalErr(w, err)
		return
	}
	nowMs := s.now().UnixMilli()
	if existing.ExpiresAt <= nowMs {
		_, _ = s.st.DeleteShare(studentHash)
		writeJSON(w, http.StatusOK, map[string]any{"exists": false})
		return
	}
	if !tokensMatch(req.ManageToken, existing.ManageHash) {
		s.shareFailLimiter.fail(ip)
		writeErr(w, http.StatusForbidden, "forbidden", "管理密钥不匹配")
		return
	}
	s.shareFailLimiter.reset(ip)
	writeJSON(w, http.StatusOK, map[string]any{
		"exists":       true,
		"code":         existing.ShareCode,
		"shareUrl":     s.buildShareURL(r, existing.ShareCode),
		"name":         existing.DisplayName,
		"semesterName": existing.SemesterName,
		"updatedAt":    existing.UpdatedAt,
		"expiresAt":    existing.ExpiresAt,
	})
}

func (s *Server) handleShareDelete(w http.ResponseWriter, r *http.Request) {
	if !s.shareEnabledOr503(w) {
		return
	}
	ip, ok := s.shareRateLimit(w, r)
	if !ok {
		return
	}
	var req shareManageReq
	if !readJSON(w, r, &req) {
		return
	}
	studentID := strings.TrimSpace(req.StudentID)
	if studentID == "" {
		writeErr(w, http.StatusBadRequest, "invalid_student_id", "请提供有效的学号")
		return
	}
	studentHash := HashToken(studentID)
	existing, err := s.st.GetShareByStudentHash(studentHash)
	if errors.Is(err, store.ErrNotFound) {
		writeJSON(w, http.StatusOK, map[string]any{"deleted": false})
		return
	}
	if err != nil {
		internalErr(w, err)
		return
	}
	nowMs := s.now().UnixMilli()
	if existing.ExpiresAt <= nowMs {
		_, _ = s.st.DeleteShare(studentHash)
		writeJSON(w, http.StatusOK, map[string]any{"deleted": false})
		return
	}
	if !tokensMatch(req.ManageToken, existing.ManageHash) {
		s.shareFailLimiter.fail(ip)
		writeErr(w, http.StatusForbidden, "forbidden", "管理密钥不匹配")
		return
	}
	s.shareFailLimiter.reset(ip)
	deleted, err := s.st.DeleteShare(studentHash)
	if err != nil {
		internalErr(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"deleted": deleted})
}

func (s *Server) serveShareJSON(w http.ResponseWriter, code string) {
	setShareCORS(w)
	if !s.shareEnabledOr503(w) {
		return
	}
	if !shareCodeRe.MatchString(code) {
		writeErr(w, http.StatusNotFound, "not_found", "课表分享不存在")
		return
	}
	sh, err := s.st.GetShareByCode(code)
	if errors.Is(err, store.ErrNotFound) {
		writeErr(w, http.StatusNotFound, "not_found", "课表分享不存在")
		return
	}
	if err != nil {
		internalErr(w, err)
		return
	}
	if sh.ExpiresAt <= s.now().UnixMilli() {
		_, _ = s.st.DeleteShare(sh.StudentIDHash)
		writeErr(w, http.StatusNotFound, "not_found", "课表分享已过期")
		return
	}
	raw, err := gunzipBytes(sh.PayloadGz)
	if err != nil {
		internalErr(w, err)
		return
	}
	var tt shareTimetableRaw
	if err := json.Unmarshal(raw, &tt); err != nil {
		internalErr(w, err)
		return
	}
	if len(tt.Courses) == 0 || bytes.Equal(bytes.TrimSpace(tt.Courses), []byte("null")) {
		tt.Courses = json.RawMessage("[]")
	}
	if len(tt.Exceptions) == 0 || bytes.Equal(bytes.TrimSpace(tt.Exceptions), []byte("null")) {
		tt.Exceptions = json.RawMessage("[]")
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"schema":     "goke.timetable.v1",
		"code":       sh.ShareCode,
		"name":       sh.DisplayName,
		"updatedAt":  sh.UpdatedAt,
		"expiresAt":  sh.ExpiresAt,
		"semester":   tt.Semester,
		"courses":    tt.Courses,
		"exceptions": tt.Exceptions,
	})
}

func (s *Server) handleShareGetAPI(w http.ResponseWriter, r *http.Request) {
	s.serveShareJSON(w, r.PathValue("code"))
}

func (s *Server) handleShareOptions(w http.ResponseWriter, r *http.Request) {
	setShareCORS(w)
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleShareShortURL(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Vary", "Accept")
	if !s.shareEnabledOr503(w) {
		return
	}
	code := r.PathValue("code")
	if !shareCodeRe.MatchString(code) {
		writeErr(w, http.StatusNotFound, "not_found", "课表分享不存在")
		return
	}
	format := r.URL.Query().Get("format")
	accept := strings.ToLower(r.Header.Get("Accept"))
	secDest := strings.ToLower(r.Header.Get("Sec-Fetch-Dest"))
	secMode := strings.ToLower(r.Header.Get("Sec-Fetch-Mode"))
	wantsJSON := format == "json" || (!strings.Contains(accept, "text/html") && secDest != "document" && secMode != "navigate")
	if wantsJSON {
		s.serveShareJSON(w, code)
		return
	}
	http.Redirect(w, r, "/s/view?code="+code, http.StatusFound)
}

func (s *Server) handleShareShortURLOptions(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Vary", "Accept")
	setShareCORS(w)
	w.WriteHeader(http.StatusNoContent)
}
