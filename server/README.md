# 薄荷广工同步服务（MintCal Server）

轻量级自建同步后端（Go + SQLite 单二进制），为 MintCal 提供多端数据同步、Web 端管理、用户管理后台以及云端 MCP 接口。无开放注册，由管理员统一创建账号。

## 快速启动

### Docker（推荐）

```bash
cd server
docker compose up -d
docker compose logs mintcal | grep 管理员   # 未预设密码时，初始密码仅在首次日志中打印一次
```

### 本地运行（需 Go 1.26+）

```bash
cd server
go run ./cmd/mintcal-server
```

常用环境变量：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `MINTCAL_ADDR` | 监听地址 | `:8080` |
| `MINTCAL_DB` | SQLite 数据库路径 | `./data/mintcal.db`（Docker 为 `/data/mintcal.db`） |
| `MINTCAL_ADMIN_USER` | 首次启动创建的管理员用户名 | `admin` |
| `MINTCAL_ADMIN_PASSWORD` | 首次启动管理员密码（不填则随机生成） | 随机 |
| `MINTCAL_TRUST_PROXY` | 设为 `1` 时信任 `X-Forwarded-For`（反代部署时开启） | 关闭 |

## Web 端与管理后台

- **Web 端（`/`）**：使用与 App 相同的账号登录，可在浏览器查看和编辑课表、日历与待办，并与 App 自动双向同步。
- **管理后台（`/admin/`）**：使用管理员账号登录，可创建用户、重置密码、停用/启用账号、强制下线设备或删除用户。
- **重置管理员密码**：执行 `mintcal-server passwd <用户名>` 并按提示输入新密码。

## 云端 MCP（可选）

服务端提供 `POST /mcp`（Streamable HTTP）端点，供 Claude、Claude Code 等外部 MCP 客户端读写云端日程与待办。（如需在局域网/本机直接调用 App 本地数据与脚本批量修改，请使用 App 设置中的「本地 MCP 服务」。）

1. **获取登录令牌**：

```bash
curl -s https://你的域名/api/v1/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"你的密码","deviceName":"MCP"}'
```

2. **客户端配置示例**：

```bash
# Claude Code
claude mcp add --transport http mintcal https://你的域名/mcp \
  --header "Authorization: Bearer <token>"
```

```json
{
  "mcpServers": {
    "mintcal": {
      "url": "https://你的域名/mcp",
      "headers": {
        "Authorization": "Bearer <token>"
      }
    }
  }
}
```

提供工具：`list_lists`、`create_list`、`get_task`、`search_tasks`、`create_task`、`update_task`、`complete_task`、`delete_task`、`get_agenda`、`list_courses`、`search_events`（只读兼容）。

## 公网部署与备份

- **HTTPS 反向代理**：公网使用时建议置于 Caddy / Nginx 等 HTTPS 反代之后，并设置 `MINTCAL_TRUST_PROXY=1`。
- **数据备份**：停止服务后备份数据库文件，或使用 `sqlite3 mintcal.db ".backup backup.db"` 在线备份。

## 开发与测试

```bash
cd server
go vet ./... && go test ./...         # Go 后端检查与测试
node --test webtest/*.test.mjs        # Web 前端测试（需 Node 20+）
```

端到端测试请在仓库根目录运行 `bash scripts/web-e2e.sh` 或 `bash scripts/sync-e2e.sh`。接口定义见 [openapi.yaml](openapi.yaml)，设计文档见 [S010](../docs/specs/010-sync-server.md) 与 [ADR-0005](../docs/adr/0005-go-self-hosted-server.md)。
