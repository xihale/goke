// Package server 是 Goke 的自建同步服务：管理员创建用户，客户端登录后按记录同步。
package server

import (
	"embed"
	"encoding/json"
	"errors"
	"io/fs"
	"log"
	"net"
	"net/http"
	"regexp"
	"strings"
	"time"

	"github.com/xihale/goke/server/internal/mcp"
	"github.com/xihale/goke/server/internal/store"
)

//go:embed web
var webFS embed.FS

type Server struct {
	st         *store.Store
	limiter    *loginLimiter
	relay      *relayHub
	trustProxy bool
	mux        *http.ServeMux
}

type Options struct {
	// TrustProxy 为 true 时用 X-Forwarded-For 识别客户端 IP（部署在反向代理后面时开启）。
	TrustProxy bool
}

func New(st *store.Store, opt Options) *Server {
	mcp.Warmup() // 工具 Schema 有错时启动即失败，而不是等第一个请求
	s := &Server{st: st, limiter: newLoginLimiter(8, 10*time.Minute), relay: newRelayHub(), trustProxy: opt.TrustProxy, mux: http.NewServeMux()}
	s.routes()
	return s
}

func (s *Server) Handler() http.Handler { return s.secure(s.mux) }

func (s *Server) routes() {
	m := s.mux
	m.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) { writeJSON(w, 200, map[string]string{"status": "ok"}) })

	m.HandleFunc("POST /api/v1/login", s.handleLogin)
	m.Handle("POST /api/v1/logout", s.auth(false, s.handleLogout))
	m.Handle("GET /api/v1/me", s.auth(false, s.handleMe))
	m.Handle("POST /api/v1/me/password", s.auth(false, s.handleChangePassword))

	m.Handle("GET /api/v1/sync/pull", s.auth(false, s.handlePull))
	m.Handle("POST /api/v1/sync/push", s.auth(false, s.handlePush))

	// MCP（Model Context Protocol）：外部 AI 助手经 Streamable HTTP 操作本账号的日程，
	// 写入与 push 同一条 LWW 落库路径，设备下次拉取即得。见 docs/specs/015-mcp.md。
	m.Handle("POST /mcp", s.auth(false, s.handleMCP))

	m.Handle("GET /api/v1/admin/users", s.auth(true, s.handleListUsers))
	m.Handle("POST /api/v1/admin/users", s.auth(true, s.handleCreateUser))
	m.Handle("PATCH /api/v1/admin/users/{id}", s.auth(true, s.handleUpdateUser))
	m.Handle("DELETE /api/v1/admin/users/{id}", s.auth(true, s.handleDeleteUser))
	m.Handle("POST /api/v1/admin/users/{id}/signout", s.auth(true, s.handleSignOutUser))

	m.HandleFunc("POST /api/v1/relay/{room}", s.handleRelayPush)
	m.HandleFunc("GET /api/v1/relay/{room}", s.handleRelayPoll)
	m.HandleFunc("DELETE /api/v1/relay/{room}", s.handleRelayClear)

	adminFS, _ := fs.Sub(webFS, "web/admin")
	m.Handle("GET /admin/", http.StripPrefix("/admin/", http.FileServerFS(adminFS)))
	m.HandleFunc("GET /admin", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "/admin/", http.StatusMovedPermanently)
	})

	relayFS, _ := fs.Sub(webFS, "web/relay")
	m.Handle("GET /relay/", noCache(http.StripPrefix("/relay/", http.FileServerFS(relayFS))))
	m.HandleFunc("GET /relay", func(w http.ResponseWriter, r *http.Request) {
		target := "/relay/"
		if r.URL.RawQuery != "" {
			target += "?" + r.URL.RawQuery
		}
		http.Redirect(w, r, target, http.StatusMovedPermanently)
	})

	// Web 端（单页应用，hash 路由，无构建步骤）：/ 返回入口页，静态资源在 /app/ 下。
	appFS, _ := fs.Sub(webFS, "web/app")
	m.Handle("GET /app/", noCache(http.StripPrefix("/app/", http.FileServerFS(appFS))))
	m.Handle("GET /{$}", noCache(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.ServeFileFS(w, r, appFS, "index.html")
	})))
}

// noCache 让浏览器每次向服务器确认静态资源（升级服务端后立即生效）。
func noCache(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-cache")
		next.ServeHTTP(w, r)
	})
}

// secure 加上通用的安全响应头并限制请求体大小。
func (s *Server) secure(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		h.Set("X-Content-Type-Options", "nosniff")
		h.Set("Referrer-Policy", "no-referrer")
		h.Set("X-Frame-Options", "DENY")
		h.Set("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; form-action 'self'; manifest-src 'self'; base-uri 'none'; frame-ancestors 'none'")
		if strings.HasPrefix(r.URL.Path, "/api/") {
			h.Set("Cache-Control", "no-store")
		}
		r.Body = http.MaxBytesReader(w, r.Body, 16<<20)
		next.ServeHTTP(w, r)
	})
}

// ---------- 通用工具 ----------

type session struct {
	user      store.User
	tokenHash string
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeErr(w http.ResponseWriter, status int, code, msg string) {
	writeJSON(w, status, map[string]string{"error": code, "message": msg})
}

func readJSON(w http.ResponseWriter, r *http.Request, v any) bool {
	dec := json.NewDecoder(r.Body)
	if err := dec.Decode(v); err != nil {
		var tooBig *http.MaxBytesError
		if errors.As(err, &tooBig) {
			writeErr(w, http.StatusRequestEntityTooLarge, "too_large", "请求体太大")
		} else {
			writeErr(w, http.StatusBadRequest, "bad_request", "请求格式不正确")
		}
		return false
	}
	return true
}

func internalErr(w http.ResponseWriter, err error) {
	log.Printf("internal error: %v", err)
	writeErr(w, http.StatusInternalServerError, "internal", "服务器内部错误")
}

func (s *Server) clientIP(r *http.Request) string {
	if s.trustProxy {
		if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
			return strings.TrimSpace(strings.Split(xff, ",")[0])
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

// auth 校验 Bearer 令牌；admin 为 true 时还要求管理员角色。
func (s *Server) auth(admin bool, next func(http.ResponseWriter, *http.Request, *session)) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		const prefix = "Bearer "
		h := r.Header.Get("Authorization")
		if !strings.HasPrefix(h, prefix) {
			writeErr(w, http.StatusUnauthorized, "unauthorized", "请先登录")
			return
		}
		hash := HashToken(strings.TrimSpace(h[len(prefix):]))
		u, err := s.st.UserByToken(hash)
		if errors.Is(err, store.ErrNotFound) {
			writeErr(w, http.StatusUnauthorized, "unauthorized", "登录已失效，请重新登录")
			return
		}
		if err != nil {
			internalErr(w, err)
			return
		}
		if admin && u.Role != "admin" {
			writeErr(w, http.StatusForbidden, "forbidden", "需要管理员权限")
			return
		}
		next(w, r, &session{user: u, tokenHash: hash})
	})
}

var usernameRe = regexp.MustCompile(`^[A-Za-z0-9_.-]{3,32}$`)

func validPassword(pw string) bool { return len(pw) >= MinPasswordLen && len(pw) <= MaxPasswordLen }

// Bootstrap 在没有任何用户时创建第一个管理员。返回 (用户名, 明文密码, 是否新建)。
// 密码为空时随机生成，由调用方打印一次。
func (s *Server) Bootstrap(username, password string) (string, string, bool, error) {
	n, err := s.st.CountUsers()
	if err != nil || n > 0 {
		return "", "", false, err
	}
	if username == "" {
		username = "admin"
	}
	if !usernameRe.MatchString(username) {
		return "", "", false, errors.New("管理员用户名只能包含字母、数字、_ . -，长度 3–32")
	}
	if password == "" {
		password = RandomPassword(16)
	} else if !validPassword(password) {
		return "", "", false, errors.New("管理员密码至少 8 位")
	}
	hash, err := HashPassword(password)
	if err != nil {
		return "", "", false, err
	}
	if _, err := s.st.CreateUser(username, hash, "admin"); err != nil {
		return "", "", false, err
	}
	return username, password, true, nil
}
