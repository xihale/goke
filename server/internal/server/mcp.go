package server

import (
	"net/http"

	"github.com/xihale/goke/server/internal/mcp"
)

// handleMCP 把已鉴权的请求转交 MCP 处理器；每个请求按当前用户构造工具集（无状态）。
func (s *Server) handleMCP(
	w http.ResponseWriter,
	r *http.Request,
	ss *session,
) {
	mcp.Handler(s.st, ss.user.ID).ServeHTTP(w, r)
}
