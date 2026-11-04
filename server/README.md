# 薄荷日程同步服务（自建）

给 App 的「账号与同步」用的小型服务：**没有开放注册、没有邮箱**，由管理员在后台创建用户；用户在 App 里填服务器地址、用户名、密码登录后，数据按记录同步。不登录就是纯本地模式。设计见 [ADR-0005](../docs/adr/0005-go-self-hosted-server.md)，接口见 [openapi.yaml](openapi.yaml)。

## 运行

```bash
# 方式一：Docker
cd server && docker compose up -d
docker compose logs mintcal | grep 管理员     # 没设置密码时，初始管理员密码只在日志里出现一次

# 方式二：直接运行（需要 Go 1.24+）
cd server && go run ./cmd/mintcal-server
```

首次启动会创建管理员（默认用户名 `admin`）。用环境变量指定账号：

| 变量 | 含义 | 默认 |
| --- | --- | --- |
| `MINTCAL_ADDR` | 监听地址 | `:8080` |
| `MINTCAL_DB` | SQLite 数据库文件 | `./data/mintcal.db`（Docker 里 `/data/mintcal.db`） |
| `MINTCAL_ADMIN_USER` | 首次启动创建的管理员用户名 | `admin` |
| `MINTCAL_ADMIN_PASSWORD` | 首次启动的管理员密码；不设置则随机生成并打印一次 | 随机 |
| `MINTCAL_TRUST_PROXY` | 设为 `1` 时用 `X-Forwarded-For` 识别客户端 IP（反向代理后面使用） | 关 |

## Web 端

浏览器打开 `http://服务器:8080/`，用与 App 相同的账号登录，就能在手机或电脑上查看 / 编辑课表、日历、日程和待办（窄屏是手机布局，宽屏是电脑布局），数据与 App 双向同步。手机浏览器里可以「添加到主屏幕」。它内嵌在服务端二进制里，不需要额外部署；不含导入、脚本修改、快递识别与分享（这些仍在 App 里）。令牌保存在浏览器里，公网使用务必加 HTTPS。设计见 [S011](../docs/specs/011-web-client.md) 与 [ADR-0006](../docs/adr/0006-web-client-vanilla-esm.md)。

## MCP：外部 AI 助手管理日程

服务端内嵌 MCP（Model Context Protocol）端点：`POST /mcp`，Streamable HTTP 传输（无状态、JSON 响应）。任何 MCP 客户端（Claude、Claude Code、其他助手）都能用你的账号令牌读写日程——App 不内置 AI，AI 能力在这里。

**拿令牌**：用任意账号登录一次即可（`deviceName` 起个能认出来的名字，管理员后台可以单独注销它）：

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
// 其他支持远程 MCP 的客户端（自定义连接器 / mcpServers）
{ "mintcal": { "url": "https://cal.example.com/mcp", "headers": { "Authorization": "Bearer <token>" } } }
```

**工具一览**（输入输出为中文描述的 JSON）：

| 工具 | 说明 |
| --- | --- |
| `list_lists` / `create_list` | 待办清单及数量 / 新建清单 |
| `get_task` / `search_tasks` | 按 id 读取 / 按关键词、清单、状态、日期范围搜索待办 |
| `create_task` | 新建待办；只有日期是待办，带时刻即日程 |
| `update_task` / `complete_task` / `delete_task` | 改字段 / 完成、重开、「不做了」/ 删除（墓碑） |
| `get_agenda` | 一段时间内安排了什么，按时间排序 |
| `search_events` | 旧版日程记录（只读；日程已并入待办） |
| `list_courses` | 学期与每周课程表 |

**语义**：写入与 App 的 push 走同一条记录同步路径（updatedAt 后写者胜，冲突时重读重试），设备端打开 App、本地有变化或后台周期同步（约 15 分钟）时到达；删除幂等并级联删除子任务，留墓碑与其他端一致；新增待办按 App 的追加规则排序；服务端产生的记录 `source` 为 `CODE`。提醒由 App 在同步后调度。设计见 [S015](../docs/specs/015-mcp.md)。

## 管理后台

浏览器打开 `http://服务器:8080/admin/`，用管理员账号登录，可以：创建用户、重置密码、停用 / 启用、设为管理员 / 普通用户、让某用户的所有设备退出登录、删除用户（连同其同步数据）。系统始终至少保留一个可用的管理员；不能停用、降级或删除自己。

忘记管理员密码：`mintcal-server passwd <用户名>`（在服务器上执行，新密码从标准输入读取）。

## 部署建议

- 对外使用请放在 HTTPS 反向代理后面，例如 Caddy：

  ```
  cal.example.com {
      reverse_proxy 127.0.0.1:8080
  }
  ```

  并设置 `MINTCAL_TRUST_PROXY=1`。App 对 `http://` 地址会提示「密码和日程明文传输」。
- 备份：停止服务或使用 `sqlite3 mintcal.db ".backup backup.db"`，备份 `data/` 目录即可。
- 服务端不解析日程内容，只存 JSON；App 升级后新增的字段无需升级服务端。

## 开发

```bash
cd server
go vet ./... && go test ./...
```

Web 端的前端逻辑测试需要 Node ≥ 20：`cd server && node --test webtest/*.test.mjs`（默认用内置模拟服务）；`bash scripts/web-e2e.sh` 会启动真实服务端再跑一遍同步测试（含 `webtest/mcp.test.mjs` 的 MCP 端到端）。`node webtest/mock.mjs 8099` 可起一个带演示数据的模拟服务（账号 demo / demopassword），配合 `webtest/shots.mjs` 截图检查布局。

仓库根目录的 `scripts/sync-e2e.sh` 会启动一个临时服务端，用两台「设备」（Robolectric 里的两份本地数据库）跑端到端同步测试。
