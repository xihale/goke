# S016 本地 MCP：App 内置服务与高级修改工具

## 背景与目标

针对未部署自建同步服务的本地用户，或需要让外部 AI Agent 直接执行「高级修改」（QuickJS 批量脚本 + 实时 Diff 预览 + 撤销）的场景，App 内置了一个轻量 MCP 服务，直接读写本地 `Repository` 并走可撤销变更集。

## 行为

- **服务与网络（`app/goke/mcp/LocalMcpServer.kt`、`McpStore.kt`）**：
  - 基于原生 `ServerSocket` 实现 Streamable HTTP（`POST /mcp` 与 `/`、`GET /health`，`GET /mcp` 返回 `405`、`DELETE /mcp` 返回 `200`），默认端口 `17530`（可配置 `1024–65535`）。
  - 默认仅绑定 `127.0.0.1`（供本机/ADB 转发 Agent 连接），可在「设置 → 本地 MCP」按需开启局域网监听（`0.0.0.0`）。
  - 全程强制 `Authorization: Bearer <token>` 鉴权，令牌经 Android Keystore 加密存于 `McpTokenVault`，支持一键刷新与复制客户端配置。
- **工具（14 个，`LocalMcpTools.kt`）**：
  - **10 个标准工具**（语义与云端 MCP 对齐）：`list_lists`、`create_list`、`get_task`、`search_tasks`、`create_task`、`update_task`、`complete_task`、`delete_task`、`get_agenda`、`list_courses`（不含已废弃的 `search_events`）。
  - **4 个独占「高级修改」工具**：
    - `get_script_sdk`：返回 `goke.d.ts` 类型声明与内置示例，供 AI 编写合法的批量修改脚本。
    - `preview_script`：在 QuickJS 沙箱（无 IO、带超时与内存限制）Dry Run 运行脚本，缓存生成的 `ChangeSet` 并返回预览摘要与 `previewId`（最近保留 16 份预览）。
    - `apply_script`：按 `previewId` 提交已预览的 `ChangeSet` 落库并记入可撤销操作历史。
    - `undo_change`：撤销最近一次可撤销操作。
- **变更集与撤销**：所有写操作统一经 `Repository.commit` 写入 SQLite 与 `oplog`，与 UI 操作共享同一套撤销链与冲突保护。

## 验收与测试

- `:app`：`LocalMcpServerTest`（鉴权校验、MCP 握手与工具列表、待办读写、QuickJS `preview_script` → `apply_script` → `undo_change` 闭环）、`ScreensSmokeTest` 渲染 `McpScreen`。
