# Goke（工课）

源码可用、非商业、本地优先的 Android 校园日程工具，将 **课表、日历、待办与取件提醒** 整合在一个轻量应用里。支持广东工业大学教务同步、局域网电脑端管理、本地/云端 MCP 接入外部 AI 助手，以及可选的自建多端同步服务。

## 界面预览

| 周课表 | 今日与取件 | 成绩与均绩 | 桌面小组件 |
| :---: | :---: | :---: | :---: |
| ![周课表](landing/assets/shot-timetable.webp) | ![今日与取件](landing/assets/shot-today.webp) | ![成绩与均绩](landing/assets/shot-grades.webp) | ![桌面小组件](landing/assets/shot-widgets.webp) |

## 功能概览

- **四核心视图**：底部导航常驻 **今日 · 课表 · 日历 · 待办**，Material 3 风格，支持多套植物主题、自定义主题色与 Android 12+ 动态取色。
- **广工教务同步**：统一认证登录导入学期课表、开学日期与考试安排，支持每周自动更新调停课，并可在本地只读查询各学期成绩与加权均绩（GPA）。
- **课表与编辑**：周视图支持长按拖动调整时段、拖拽边缘改时长、空白格框选新建，以及冲突课程叠放切换。
- **待办与日历**：支持自然语言快速录入、子任务、时间线分区整理、手势完成/改期与多选批量处理，待办可直接排入日历与课表。
- **取件与系统联动**：本地规则自动识别取件通知并加入顺路取件日程；提供 5 款桌面小组件（支持设置页预览与一键添加）、上课自动勿扰、系统日历双向同步与提醒自检。
- **电脑端管理 & MCP**：可在手机端开启局域网 Web 管理，用电脑浏览器大屏编辑课表与待办；提供 App 本地 MCP（含 QuickJS 脚本批量修改与撤销）与服务端云端 MCP 两种接入方式。
- **纯本地可用 & 可选同步**：不登录即纯本地 SQLite 存储，支持完整导入导出备份；如需多端同步与网页端访问，可部署自带的 Go 同步服务。

## 仓库结构

| 目录 | 说明 |
| --- | --- |
| `app/` | Android 客户端（Kotlin / Jetpack Compose，最低 Android 8.0 / API 26） |
| `core/` | 纯 Kotlin 核心领域逻辑 |
| `script/` | QuickJS 脚本沙箱（用于批量修改与预览） |
| `server/` | Go + SQLite 自建同步服务与内嵌 Web 端 |
| `landing/` | 静态零构建产品介绍页（已部署于 [goke.xeed.ink](https://goke.xeed.ink)） |
| `benchmark/` | 真机 Macrobenchmark 性能基准测试 |

## 构建与测试

```bash
./gradlew :app:assembleDebug        # 构建 Debug APK
./gradlew :core:test :script:test   # 运行核心模块单元测试
./gradlew :app:testDebugUnitTest    # 运行 App 单元测试（Robolectric）
./gradlew spotlessCheck             # Kotlin 代码格式检查（spotlessApply 可自动修复）
```

自建同步服务与网页端的部署、测试说明见 [server/README.md](server/README.md)。

## 发布与签名

版本号由 `v*` Git Tag 通过 `git describe` 自动派生（支持 `v0.0.1-beta.1` 等 SemVer 预发布标签）。推送 `v*` 标签会自动触发 Release 工作流（`.github/workflows/release.yml`），通过 `ADAPTERS_SSH_KEY`（只读 Deploy Key）从私有仓库拉取 `adapters/` 模块并构建正式签名 APK 后创建 GitHub Release（含 `-` 后缀的标签自动标记为 Pre-release）。

GitHub Actions 的 `release` 环境需配置以下五个 Secrets（缺一不可，构建脚本不会回退 Debug 签名，缺失私有适配器时工作流也会直接中止）：

| Secret | 说明 |
| --- | --- |
| `ADAPTERS_SSH_KEY` | 私有 `adapters` 仓库只读 Deploy Key 私钥 |
| `SIGNING_KEYSTORE_BASE64` | Keystore 文件的 Base64 编码 |
| `SIGNING_KEYSTORE_PASSWORD` | Keystore 密码 |
| `SIGNING_KEY_ALIAS` | Key 别名 |
| `SIGNING_KEY_PASSWORD` | Key 密码 |

本地构建 `./gradlew :app:assembleRelease` 时，可在仓库根目录放置未入库的 `keystore.properties`，或设置环境变量 `SIGNING_KEYSTORE_FILE`、`SIGNING_KEYSTORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`。

## 半开源说明与贡献边界

为防止第三方校园与硬件协议被恶意滥用，本项目采用**核心及界面开源 + 逆向协议适配器闭源**的半开源架构：

- **公开仓库包含**：全部 Android UI 页面与组件、桌面小组件、本地 SQLite 存储、课表/日历/待办/取件核心逻辑、QuickJS 脚本沙箱、Go 同步服务端、Web 端与所有适配器接口契约。
- **公开仓库不包含**：教务、体测、空教室、选课、智慧热水等逆向通信协议、抓包文档与真实网络/蓝牙实现（本地私有 `adapters/` 模块由构建脚本按需挂载）。
- **欢迎 UI 与本地功能贡献**：即使没有私有适配器源码，也可直接克隆本仓库执行 `./gradlew :app:assembleDebug` 与 `./gradlew :app:testDebugUnitTest` 编译、预览和参与 UI 及本地功能开发（仅校内/热水联机功能在开源编译版本中不可用）。

## 开发文档

- [工程入口与规范](CLAUDE.md) · [代码模块索引](CODEMAP.md) · [视觉与组件规范](DESIGN.md)
- [架构说明](docs/architecture.md) · [测试与基准指南](docs/testing.md) · [功能规格索引](docs/specs/README.md)

## 许可证

本项目采用 [Goke Strict Noncommercial Share-Alike License 2.0 (Goke-NC-SA-2.0)](LICENSE)（基于 PolyForm Noncommercial License 1.0.0 强化修订，极端非商业共享许可）：

- **绝对非商业，且永久拒绝任何商业授权**：严禁对本软件本体及基于本软件的任何衍生版本、套件、插件或服务进行任何直接或间接的商业活动（包括但不限于付费分发、商业捆绑、商业咨询与付费部署/支持、广告投放、流量变现、商业 SaaS 托管及营利性机构使用等）。著作权人永久拒绝一切商业许可申请，且官方项目自身永不引入任何商业元素。
- **严禁以本项目名义发起或接受捐款与赞助**：作者及官方项目不接受、也永不接受任何形式的捐款、打赏、赞助或众筹；严禁任何个人或第三方以 Goke（工课）或其衍生项目的名义展示收款码、收款链接或募集资金。
- **衍生同协议共享（Share-Alike）**：基于本软件的修改、衍生套件或对外提供的网络服务，须完整沿用本协议并免费公开全部对应源代码。
- 第三方依赖库各自保留其原有协议，详见 [依赖清单](docs/DEPENDENCIES.md)。
