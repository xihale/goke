package server

import (
	"errors"
	"net/http"
	"strings"

	"github.com/xihale/goke/server/internal/store"
)

// dummyHash 用于「用户不存在」时也做一次密码运算，避免通过响应时间探测用户名。
var dummyHash, _ = HashPassword("not-a-real-password")

type loginReq struct {
	Username   string `json:"username"`
	Password   string `json:"password"`
	DeviceName string `json:"deviceName"`
}

func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	var req loginReq
	if !readJSON(w, r, &req) {
		return
	}
	req.Username = strings.TrimSpace(req.Username)
	key := s.clientIP(r) + "|" + strings.ToLower(req.Username)
	if s.limiter.blocked(key) {
		writeErr(w, http.StatusTooManyRequests, "too_many_attempts", "尝试次数过多，请稍后再试")
		return
	}
	u, err := s.st.UserByName(req.Username)
	hash := dummyHash
	if err == nil {
		hash = u.PassHash
	} else if !errors.Is(err, store.ErrNotFound) {
		internalErr(w, err)
		return
	}
	ok := CheckPassword(req.Password, hash) && err == nil
	if !ok {
		s.limiter.fail(key)
		writeErr(w, http.StatusUnauthorized, "invalid_credentials", "用户名或密码不正确")
		return
	}
	if u.Disabled {
		writeErr(w, http.StatusForbidden, "disabled", "该账号已被管理员停用")
		return
	}
	s.limiter.reset(key)
	plain, tokenHash, err := NewToken()
	if err != nil {
		internalErr(w, err)
		return
	}
	device := strings.TrimSpace(req.DeviceName)
	if len(device) > 64 {
		device = device[:64]
	}
	if err := s.st.CreateToken(u.ID, tokenHash, device); err != nil {
		internalErr(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"token": plain, "user": userView(u)})
}

func userView(u store.User) map[string]any {
	return map[string]any{"id": u.ID, "username": u.Username, "role": u.Role}
}

func (s *Server) handleLogout(w http.ResponseWriter, r *http.Request, ss *session) {
	if err := s.st.DeleteToken(ss.tokenHash); err != nil {
		internalErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleMe(w http.ResponseWriter, r *http.Request, ss *session) {
	cur, err := s.st.Cursor(ss.user.ID)
	if err != nil {
		internalErr(w, err)
		return
	}
	v := userView(ss.user)
	v["cursor"] = cur
	writeJSON(w, http.StatusOK, v)
}

func (s *Server) handleChangePassword(w http.ResponseWriter, r *http.Request, ss *session) {
	var req struct {
		Old string `json:"oldPassword"`
		New string `json:"newPassword"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	if !CheckPassword(req.Old, ss.user.PassHash) {
		writeErr(w, http.StatusUnauthorized, "invalid_credentials", "原密码不正确")
		return
	}
	if !validPassword(req.New) {
		writeErr(w, http.StatusBadRequest, "weak_password", "新密码至少 8 位")
		return
	}
	hash, err := HashPassword(req.New)
	if err != nil {
		internalErr(w, err)
		return
	}
	if err := s.st.SetPassword(ss.user.ID, hash); err != nil {
		internalErr(w, err)
		return
	}
	if err := s.st.DeleteOtherUserTokens(ss.user.ID, ss.tokenHash); err != nil {
		internalErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
