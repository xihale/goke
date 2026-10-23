# ADR-0006：Web 端用原生 ES 模块，无构建，内嵌进 Go 二进制

- 状态：已采纳
- 背景：用户要求 Web 端同时适配手机与 PC。Web 端只是同步服务的另一个客户端，功能是 App 的子集；项目强调依赖少、构建步骤少，并且本地环境（沙箱）没有联网安装前端依赖的保证。
- 决定：
  - **不用前端框架与打包器**：手写 ES 模块（`server/internal/server/web/app/js/`），一个 `h()` 辅助函数生成 DOM，视图是「状态 → DOM」的函数，store 变更后重新渲染。`go:embed` 打进服务端二进制，`/` 与 `/app/` 提供，部署仍是单文件 / 单镜像。
  - **业务逻辑与 UI 分层**：`util.js` / `model.js` / `store.js` / `api.js` 不依赖 DOM，可直接用 `node --test` 测试（含对真实 Go 服务端的端到端同步）；`model.js` 是 Kotlin `:core`（周次、课程展开、重复规则、待办过滤）的 JS 版本，由夹具测试保证记录格式互通。
  - **hash 路由**（`#/today` 等）：服务端不需要为每个页面做回退路由。
  - **CSP 严格**：`script-src 'self'; style-src 'self'`，无内联脚本 / 样式，所有动态样式经 CSSOM（`style.setProperty`）设置；渲染不拼 HTML。
  - **令牌存储**：`localStorage`（保持登录）或 `sessionStorage`。没有 httpOnly Cookie 方案，因为 API 是给 App 用的 Bearer 令牌；取舍是零第三方脚本 + 严格 CSP + 建议 HTTPS。
  - **响应式**：CSS 手机优先，700px / 900px 两个断点（底部导航 ↔ 侧栏、抽屉 ↔ 居中弹窗、单栏 ↔ 双栏）。主题用 CSS 变量，主色由种子色在浏览器端按 M3 思路生成（薄荷用固定值，保证与 App 一致）。
  - 没有 Service Worker：避免缓存过期导致用户停在旧版本；入口页 `Cache-Control: no-cache`。
- 后果：
  - 没有新增任何依赖（见 `docs/DEPENDENCIES.md`）；代码量比用框架多一些手写 DOM，换来无构建、可审计。
  - 视图没有虚拟 DOM，大列表（上千待办）靠分页 / 筛选与按需渲染；对个人使用规模足够。
  - `model.js` 与 Kotlin 逻辑是两份实现，改规则时要同步修改并更新夹具测试。
