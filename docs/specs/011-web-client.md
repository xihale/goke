# S011 Web 端（网页版客户端）

## 背景与目标

Web 端是同步服务的**另一个客户端**，内嵌在 Go 服务端中随二进制发布，让用户在手机与 PC 浏览器中也能查看与编辑课表、日历与待办，与 App 读写同一套 JSON 记录。

## 非目标

- 不做浏览器后台推送提醒（浏览器无可靠定时提醒能力）。
- 不含：ICS / xlsx 导入、QuickJS 脚本「高级修改」、快递通知识别、分享链接/订阅、教务系统导入、系统日历同步、英文翻译。
- 重复日程仅支持整体修改；操作历史不同步，Web 端仅对「删除」提供短时撤销。

## 行为

- **入口**：`http://服务器:8080/`（`go:embed` 内嵌）；`/admin/` 为管理后台。
- **登录**：用户名 + 密码（无开放注册）。勾选「保持登录」令牌存 `localStorage`，否则存 `sessionStorage`；退出登录注销当前设备令牌并清空缓存。
- **页面**（hash 路由）：今日 `#/today`、课表 `#/timetable`、日历 `#/calendar`、待办 `#/todo`、设置 `#/settings`。
  - 今日：当天课程 / 日程 / 待办时间线 + 今日待办与进度（PC 双栏）。
  - 课表：周视图（节次时间列、今天高亮、周切换/跳回本周），点击课程查看详情、编辑、删除或按周调停课。
  - 日历：月视图（课程/日程/待办圆点、周次列）+ 选中日议程（PC 议程在右侧）。
  - 待办：智能清单（今天/未来 7 天/全部/已逾期/已完成）+ 自建清单；快速添加；子任务、优先级、截止、重复，以及「不做了（`dropped`）」状态展示。
  - 设置：学期管理（周次、节次）、主题（薄荷等预设 + 自定义种子色，跟随系统深浅色）、改密码、导出 JSON、退出登录；管理员有 `/admin/` 入口。
- **响应式与 PWA**：`< 700px` 手机布局（底栏 + 底部抽屉），`≥ 900px` PC 布局（侧栏 + 双栏 + 居中弹窗）；提供 `manifest.webmanifest` 可添加到主屏幕，不使用 Service Worker（防旧缓存）。
- **同步与兼容**：与 App 同规则（先拉后推、`updatedAt` LWW、墓碑、游标分页）；断网保留待推送队列并自动重试；未知字段与未知记录 `_t` 原样保留或跳过不破坏数据。
- **安全**：严格 CSP `script-src 'self'; style-src 'self'`，零外链与零内联样式，DOM 构建一律用 `textContent` 不拼 HTML；公网部署需配 HTTPS。

## 技术取舍

见 [ADR-0006](../adr/0006-web-client-vanilla-esm.md)：原生 ES 模块、无构建、`go:embed`。

## 测试

- `server/webtest/model.test.mjs`：日期工具、周次、课程展开、事件重复、待办筛选与完成、重复待办、同步元数据。
- `server/webtest/sync.test.mjs` & `mcp.test.mjs`：双端同步 / 冲突 / 离线恢复 / 分页 / 改密码 / MCP 双向收敛；`bash scripts/web-e2e.sh` 对真实 Go 服务端运行。
- `server`：`TestWebAppServed`（入口页、静态资源、manifest、CSP、缓存头）。
- `:core`：`WebCompatTest` 解码 `core/src/test/resources/web-records.json`（由 `server/webtest/gen-fixture.mjs` 生成）。
- 视觉检查：`node webtest/shots.mjs`（手机 / 桌面 × 浅色 / 深色）。
