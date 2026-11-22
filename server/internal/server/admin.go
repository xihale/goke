package server

import (
	"errors"
	"net/http"
	"strconv"
	"strings"

	"github.com/xihale/goke/server/internal/store"
)

func (s *Server) handleListUsers(w http.ResponseWriter, r *http.Request, ss *session) {
	users, err := s.st.ListUsers()
	if err != nil {
		internalErr(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"users": users, "me": ss.user.ID})
}

func (s *Server) handleCreateUser(w http.ResponseWriter, r *http.Request, ss *session) {
	var req struct {
		Username string `json:"username"`
		Password string `json:"password"`
		Role     string `json:"role"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	req.Username = strings.TrimSpace(req.Username)
	if req.Role == "" {
		req.Role = "user"
	}
	switch {
	case !usernameRe.MatchString(req.Username):
		writeErr(w, http.StatusBadRequest, "bad_username", "用户名只能包含字母、数字、_ . -，长度 3–32")
		return
	case !validPassword(req.Password):
		writeErr(w, http.StatusBadRequest, "weak_password", "密码至少 8 位")
		return
	case req.Role != "user" && req.Role != "admin":
		writeErr(w, http.StatusBadRequest, "bad_role", "角色只能是 user 或 admin")
		return
	}
	hash, err := HashPassword(req.Password)
	if err != nil {
		internalErr(w, err)
		return
	}
	u, err := s.st.CreateUser(req.Username, hash, req.Role)
	if errors.Is(err, store.ErrExists) {
		writeErr(w, http.StatusConflict, "exists", "用户名已存在")
		return
	}
	if err != nil {
		internalErr(w, err)
		return
	}
	writeJSON(w, http.StatusCreated, u)
}

func pathID(w http.ResponseWriter, r *http.Request) (int64, bool) {
	id, err := strconv.ParseInt(r.PathValue("id"), 10, 64)
	if err != nil {
		writeErr(w, http.StatusBadRequest, "bad_id", "用户 ID 不正确")
		return 0, false
	}
	return id, true
}

// lastAdminGuard：不允许让系统失去最后一个可用的管理员（停用、降级、删除都会触发）。
func (s *Server) wouldLoseLastAdmin(target store.User) (bool, error) {
	if target.Role != "admin" || target.Disabled {
		return false, nil
	}
	n, err := s.st.CountActiveAdmins()
	return n <= 1, err
}

func (s *Server) handleUpdateUser(w http.ResponseWriter, r *http.Request, ss *session) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	var req struct {
		Password *string `json:"password"`
		Disabled *bool   `json:"disabled"`
		Role     *string `json:"role"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	target, err := s.st.User(id)
	if errors.Is(err, store.ErrNotFound) {
		writeErr(w, http.StatusNotFound, "not_found", "用户不存在")
		return
	}
	if err != nil {
		internalErr(w, err)
		return
	}
	if req.Role != nil && *req.Role != "user" && *req.Role != "admin" {
		writeErr(w, http.StatusBadRequest, "bad_role", "角色只能是 user 或 admin")
		return
	}
	if req.Password != nil && !validPassword(*req.Password) {
		writeErr(w, http.StatusBadRequest, "weak_password", "密码至少 8 位")
		return
	}
	demote := req.Role != nil && *req.Role != "admin"
	disable := req.Disabled != nil && *req.Disabled
	if demote || disable {
		if id == ss.user.ID {
			writeErr(w, http.StatusBadRequest, "self", "不能停用或降级自己")
			return
		}
		if lost, err := s.wouldLoseLastAdmin(target); err != nil {
			internalErr(w, err)
			return
		} else if lost {
			writeErr(w, http.StatusBadRequest, "last_admin", "至少要保留一个可用的管理员")
			return
		}
	}
	if req.Password != nil {
		hash, err := HashPassword(*req.Password)
		if err != nil {
			internalErr(w, err)
			return
		}
		if err := s.st.SetPassword(id, hash); err != nil {
			internalErr(w, err)
			return
		}
		_ = s.st.DeleteUserTokens(id) // 重置密码后所有设备需重新登录
	}
	if req.Role != nil {
		if err := s.st.SetRole(id, *req.Role); err != nil {
			internalErr(w, err)
			return
		}
	}
	if req.Disabled != nil {
		if err := s.st.SetDisabled(id, *req.Disabled); err != nil {
			internalErr(w, err)
			return
		}
		if *req.Disabled {
			_ = s.st.DeleteUserTokens(id)
		}
	}
	u, err := s.st.User(id)
	if err != nil {
		internalErr(w, err)
		return
	}
	writeJSON(w, http.StatusOK, u)
}

func (s *Server) handleDeleteUser(w http.ResponseWriter, r *http.Request, ss *session) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	if id == ss.user.ID {
		writeErr(w, http.StatusBadRequest, "self", "不能删除自己")
		return
	}
	target, err := s.st.User(id)
	if errors.Is(err, store.ErrNotFound) {
		writeErr(w, http.StatusNotFound, "not_found", "用户不存在")
		return
	}
	if err != nil {
		internalErr(w, err)
		return
	}
	if lost, err := s.wouldLoseLastAdmin(target); err != nil {
		internalErr(w, err)
		return
	} else if lost {
		writeErr(w, http.StatusBadRequest, "last_admin", "至少要保留一个可用的管理员")
		return
	}
	if err := s.st.DeleteUser(id); err != nil {
		internalErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleSignOutUser(w http.ResponseWriter, r *http.Request, ss *session) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	if _, err := s.st.User(id); errors.Is(err, store.ErrNotFound) {
		writeErr(w, http.StatusNotFound, "not_found", "用户不存在")
		return
	}
	if err := s.st.DeleteUserTokens(id); err != nil {
		internalErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
