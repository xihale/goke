# CLAUDE.md — Goke（工课）工程入口

> 本文件仅保留事实来源索引、技术栈概览、工程硬红线与高频命令。设计规范、架构细节与测试指南已分别拆至对应文档。

## 1. 事实来源与文档导航

冲突时优先级：**实际代码 > `DESIGN.md` > `docs/*`**。

- **代码地图与模块索引**：[CODEMAP.md](CODEMAP.md)（入口、端能力差异、高频文件与检索守则）
- **设计系统与 UI 约束**：[DESIGN.md](DESIGN.md)（Token 真源、视觉层级、组件库索引、图标/资产、无障碍与改设计检查清单）
- **工程结构与导航路由**：[docs/architecture.md](docs/architecture.md)（模块划分、目录树、状态流、屏幕 ↔ 文件映射）
- **构建、测试与 Benchmark**：[docs/testing.md](docs/testing.md)（单测、Robolectric 截图、Spotless、E2E、Macrobenchmark）
- **依赖与选型记录**：[docs/DEPENDENCIES.md](docs/DEPENDENCIES.md)（版本以 [gradle/libs.versions.toml](gradle/libs.versions.toml) 为准）
- **需求与决策背景**：[docs/specs/README.md](docs/specs/README.md)、`docs/adr/*`、[docs/jxfw-sync-design.md](docs/jxfw-sync-design.md)

## 2. 技术栈速查

| 层 | 选型 | 位置 |
|---|---|---|
| Android UI | Jetpack Compose + Material 3 Expressive + `material-kolor` | `app/src/main/java/app/goke/ui/` |
| 导航 | 状态驱动返回栈（`SnapshotStateList<BackEntry>` + `RouteStackHost` + `navigationevent` 作用域隔离） | `ui/Nav.kt`、`ui/GokeRoot.kt` |
| 图标 | `material-icons-core` + [GokeIcons.kt](app/src/main/java/app/goke/ui/icons/GokeIcons.kt)（矢量，不落地位图） | 各组件 `Icons.*` |
| 构建与格式 | Gradle KTS + AGP + Kotlin + Spotless / ktlint | `build.gradle.kts`、`.editorconfig` |
| 测试 | `:core` / `:script` JUnit 5；`:app` Robolectric + JUnit 4；`:benchmark` Macrobenchmark | `docs/testing.md` |
| Web 端 | 原生 ES 模块，**无构建/无框架**（ADR-0006），单 CSS，`go:embed` | `server/internal/server/web/app/` |
| 服务端 | Go + SQLite（ADR-0005） | `server/` |

## 3. 核心工程红线

1. **设计系统与组件规范遵循 [DESIGN.md](DESIGN.md)**：业务代码禁止硬编码 `Color(0xFF…)`；可点区域统一使用 `mintClickable` / `mintPressable`（严禁裸 `.clickable`）；点击不振动，触觉仅用于长按/拖拽/滑动提交；修改薄荷主题 Token 须同步 `Theme.kt` ↔ `web/app/js/theme.js` ↔ 小组件 `colors.xml`（含 `values-night`）。
2. **无 ViewModel**：状态经 `LocalApp` / `LocalActions` / `LocalNav` 与参数下传；写操作统一走 `Actions.commit(ChangeSet)`，不得绕过或污染撤销链。
3. **导航与返回红线（详见 [docs/architecture.md](docs/architecture.md)）**：所有二级页统一定义在 `Route` sealed interface 并经 `GokeRoot.RouteStackHost` 穷尽分发，禁止引入 `NavHost` / `ModalHost` 或旁路根级 `BackHandler`；子页 `BackHandler` 仅用于页内瞬态（抽屉/筛选/未保存放弃确认），必须带 `enabled = <条件>` 且绝不允许无放弃路径的校验死锁；新增 `Route` 须同步加入 `AllRoutesNavigationTest`。遵守 Ponytail 组件原则：二级页骨架统一用 `MintScreen`，底部抽屉统一用 `MintBottomSheet`，仅当确需自定义时须在 KDoc 与 `docs/architecture.md` 写明理由。
4. **文案外部化**：所有用户可见文案放在 `app/src/main/res/values/strings.xml`，禁止在 Kotlin 代码里写死中文字符串；改完运行 `check_strings.py` 校验。
5. **Web 端零外链与 CSP**：无 CDN、无外链字体/图片；遵守 `script-src 'self'; style-src 'self'`，禁止内联样式与字符串拼 HTML。
6. **依赖与资产登记**：新增第三方库或矢量插图许可证须登记到 [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md)。
7. **发版与更新日志**：每次打 `v*` tag 发版前，须先在 [CHANGELOG.md](CHANGELOG.md) 顶部补充对应版本（`## v<semver> (YYYY-MM-DD)`）面向用户的简洁中文更新日志（纯文本友好，供 GitHub Release 与应用内更新弹窗共用），提交后再打 tag 推送；CI 会在构建前强校验并提取该段作为 Release 正文。

## 4. 高频命令

```bash
./gradlew :app:testDebugUnitTest           # Robolectric 冒烟 + 截图 app/build/shots/
./gradlew :app:assembleDebug               # 构建 Debug APK
./gradlew spotlessCheck                    # ktlint 检查（spotlessApply 自动修复）
python3 scripts/check_strings.py --unused  # 字符串资源检查
python3 scripts/check_ui_rules.py          # Compose / 小组件 UI 规范检查
bash scripts/sync-e2e.sh                   # 同步端到端测试
```
