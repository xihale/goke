# 薄荷广工同步服务（MintCal Server）

为 App 的「账号与同步」提供轻量自建后端：**无开放注册、无邮箱**，由管理员在后台创建用户；App 登录后按记录双向同步，不登录则为纯本地模式。设计见 [ADR-0005](../docs/adr/0005-go-self-hosted-server.md)，REST 接口见 [openapi.yaml](openapi.yaml)。

## 运行

```bash
# 方式一：Docker
cd server && docker compose up -d
docker compose logs mintcal | grep 管理员     # 未设密码时，初始管理员密码只在日志里打印一次

# 方式二：直接运行（需要 Go 1.26+）
cd server && go run ./cmd/mintcal-server
```

首次启动会创建管理员（默认用户名 `admin`），可用环境变量覆盖：

| 变量 | 含义 | 默认 |
| --- | --- | --- |
| `MINTCAL_ADDR` | 监听地址 | `:8080` |
| `MINTCAL_DB` | SQLite 数据库文件 | `./data/mintcal.db`（Docker 里 `/data/mintcal.db`） |
| `MINTCAL_ADMIN_USER` | 首次启动创建的管理员用户名 | `admin` |
| `MINTCAL_ADMIN_PASSWORD` | 首次启动的管理员密码；不设置则随机生成并打印一次 | 随机 |
| `MINTCAL_TRUST_PROXY` | 设为 `1` 时用 `X-Forwarded-For` 识别客户端 IP（反向代理后使用） | 关 |

## Web 端

浏览器打开 `http://服务器:8080/`，用与 App 相同的账号登录即可查看与编辑课表、日历、日程与待办，与 App 双向同步。Web 端内嵌在服务端二进制中（`go:embed`），无构建步骤；不含导入、QuickJS 脚本修改、快递识别与系统日历同步。设计见 [S011](../docs/specs/011-web-client.md) 与 [ADR-0006](../docs/adr/0006-web-client-vanilla-esm.md)。

## MCP：外部 AI 助手管理日程

服务端内嵌 MCP（Model Context Protocol）端点：`POST /mcp`（Streamable HTTP，无状态 JSON 响应）。任意 MCP 客户端（Claude、Claude Code 等）可用账号令牌读写日程。如果希望 AI 直接在局域网/本机操作 App 内的本地数据与 QuickJS「高级修改」脚本，请使用 App 内置的 **本地 MCP 服务**（见 [S016](../docs/specs/016-local-mcp.md)）。

**获取令牌**：用任意账号登录一次（`deviceName` 可用于后台识别并单独注销）：

```bash
curl -s https://cal.example.com/api/v1/login -d '{"username":"alice","password":"…","deviceName":"MCP"}' -H 'Content-Type: application/json'
# → {"token":"…","user":{…}}
```

**客户端配置**：

```bash
# Claude Code
claude mcp add --transport http mintcal https://cal.example.com/mcp \
  --header "Authorization: Bearer <token>"
```

```json
// 其他支持远程 MCP 的客户端（mcpServers）
{ "mintcal": { "url": "https://cal.example.com/mcp", "headers": { "Authorization": "Bearer <token>" } } }
```

**工具一览**（11 个，详见 [S015](../docs/specs/015-mcp.md)）：

| 工具 | 说明 |
| --- | --- |
| `list_lists` / `create_list` | 待办清单及数量 / 新建清单 |
| `get_task` / `search_tasks` | 按 id 读取 / 按关键词、清单、状态、日期范围搜索待办 |
| `create_task` | 新建待办；只有日期是待办，带时刻即日程 |
| `update_task` / `complete_task` / `delete_task` | 改字段 / 完成、重开、「不做了」/ 级联删除（墓碑） |
| `get_agenda` | 一段时间内安排了什么，按时间排序 |
| `search_events` | 旧版日程记录（只读；日程已并入待办） |
| `list_courses` | 学期与每周课程表 |

写入与 App 的 `push` 走同一条 LWW 路径（冲突时重读重试），设备打开 App、本地改动或每 15 分钟后台周期同步时自动拉取；服务端记录 `source` 为 `CODE`。

## 管理后台

打开 `http://服务器:8080/admin/` 使用管理员账号登录，可创建用户、重置密码、停用/启用、调整管理员权限、强制下线用户全部设备或删除用户。系统至少保留一个可用管理员，不能停用、降级或删除自己。

忘记管理员密码：`mintcal-server passwd <用户名>`（从标准输入读取新密码）。

## 部署建议

- 公网部署请置于 HTTPS 反向代理后（如 Caddy）并设置 `MINTCAL_TRUST_PROXY=1`：

  ```caddyfile
  cal.example.com {
      reverse_proxy 127.0.0.1:8080
  }
  ```

- 备份：停止服务或使用 `sqlite3 mintcal.db ".backup backup.db"` 备份 `data/` 目录。
- 服务端只存 JSON 不解析业务字段，App 升级新增字段通常无需升级服务端。

## 开发与测试

```bash
cd server
go vet ./... && go test ./...
```

Web 端前端测试需要 Node ≥ 20：`cd server && node --test webtest/*.test.mjs`（默认对内置模拟服务运行）；仓库根目录 `bash scripts/web-e2e.sh` 会启动真实服务端运行 Web 与 MCP 端到端测试；`bash scripts/sync-e2e.sh` 会驱动两台 Robolectric 模拟设备执行双端同步测试。`node webtest/mock.mjs 8099` 可启动演示服务（demo / demopassword），配合 `webtest/shots.mjs` 截图。
