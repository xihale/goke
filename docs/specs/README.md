# 功能规格索引（Specs）

工程架构、模块划分与屏幕映射见 [architecture.md](../architecture.md)，视觉规范与组件约定见 [DESIGN.md](../../DESIGN.md)，测试与基准见 [testing.md](../testing.md)。早期 M1–M5 骨架与基础页面规格已并入上述文档，此处仅保留仍在演进或跨端协作的功能规格：

| 编号 | 文档 | 范围 |
|---|---|---|
| S009 | [009-parcel-reminders.md](009-parcel-reminders.md) | 课程/待办提醒、5 款桌面小组件、上课自动勿扰、系统日历双向同步与快递入日程链路 |
| S010 | [010-sync-server.md](010-sync-server.md) | Go 自建同步服务、端到端按记录同步、15 分钟后台周期拉取与「账号与同步」UI |
| S011 | [011-web-client.md](011-web-client.md) | 内嵌在服务端的原生 ES 模块 Web 客户端与多端兼容性约束 |
| S013 | [013-todo-ux.md](013-todo-ux.md) | 待办时间线分区、快速添加与新建确认、拖拽/滑动手势、空档/下节课联动 |
| S014 | [014-parcel-rule-engine.md](014-parcel-rule-engine.md) | 快递通知识别规则引擎、模板 DSL、内置种子与用户自定义/噪声词 |
| S015 | [015-mcp.md](015-mcp.md) | 服务端云端 MCP 接口（`POST /mcp`，11 个工具，LWW 写回同步） |
| S016 | [016-local-mcp.md](016-local-mcp.md) | App 本地 MCP 服务（本机/局域网 HTTP，14 个工具，独占 QuickJS 高级修改与撤销） |
