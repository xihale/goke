# 工程架构与导航

## 模块划分

| 模块 | 路径 | 职责 |
|---|---|---|
| `:app` | `app/` | Jetpack Compose UI、本地 SQLite 存储（手写 `SQLiteOpenHelper`）、提醒、桌面小组件、本地 MCP、Android 系统集成 |
| `:core` | `core/` | 纯 Kotlin 领域逻辑（无 Android 依赖，可 JVM 单测） |
| `:script` | `script/` | QuickJS 沙箱（高级修改批量脚本，无 IO） |
| `:benchmark` | `benchmark/` | Macrobenchmark 真机性能测试 |
| `server/` | `server/` | Go + SQLite 自建同步服务端、原生 ES 模块 Web 客户端、MCP 接口 |

## 目录结构

```text
app/src/main/java/app/mintcal/
├── MintCalApp.kt / MainActivity.kt / DeepLink.kt / BenchSeed.kt
├── ui/
│   ├── MintCalRoot.kt      # Tabs（5 个常驻 Tab）+ mintDestinations 路由表 + 撤销浮钮
│   ├── Nav.kt              # Route sealed interface、Nav 门面、Actions（变更集）
│   ├── Common.kt           # LocalTabVisible / gated / liveSnapshot / rememberNow
│   ├── Format.kt
│   ├── theme/Theme.kt      # ★ 全部设计 Token（详见 DESIGN.md）
│   ├── components/         # ★ 可复用 UI 组件（Kit / Rows / FormKit / MonthPager / DialPickers …）
│   └── screens/            # 按功能分文件，一屏一文件
├── data/                   # Repository / Settings / Attachments / SecretStore …
├── calendar/ jxfw/ lanweb/ links/ mcp/ notify/ share/ sync/ widget/ advanced/
core/src/main/kotlin/app/mintcal/core/
├── model/ agenda/ change/ recurrence/ todo/ time/ importer/ export/ sync/ jxfw/ parcel/ util/
script/                     # QuickJS 沙箱
server/internal/server/web/app/   # Web 客户端（js/views + app.css）
```

## 状态流与路由规则

- **无 ViewModel**：状态经 `LocalApp` / `LocalActions` / `LocalNav` CompositionLocal 与参数下传，屏幕保持纯函数。
- **写操作统一走变更集**：页面通过 `LocalActions.current.commit(cs, message, undoable)` 提交 `ChangeSet`，可撤销操作通过左下角撤销浮钮回滚，不可撤销操作退化为系统 Toast。
- **路由表**：`Route` 为 `@Serializable sealed interface`（`ui/Nav.kt`），压栈页统一注册在 `ui/MintCalRoot.kt` 的 `NavGraphBuilder.mintDestinations`。
- **Tab 层常驻**：`HOME` 目的地承载 `Tab.TODAY` / `Tab.TIMETABLE` / `Tab.CALENDAR` / `Tab.TODO` / `Tab.ME` 五个常驻 Tab，切换零重组，不参与压栈转场。

## 屏幕 ↔ 文件映射

| 屏幕 / 浮层 | Compose 入口 | 文件（`app/src/main/java/app/mintcal/ui/screens/`） |
|---|---|---|
| 今日 | `TodayScreen()`（`Tab.TODAY`） | `TodayScreen.kt` |
| 课表 | `TimetableScreen()`（`Tab.TIMETABLE`） | `TimetableScreen.kt`（含私有 `SlotSheet`/`CourseSheet`） |
| 日历 | `CalendarScreen()`（`Tab.CALENDAR`） | `CalendarScreen.kt` |
| 待办 | `TodoScreen()`（`Tab.TODO`） | `TodoScreen.kt` + `TodoRows.kt` + `TodoModel.kt` + `QuickAddBar.kt` |
| 我的（设置中心） | `SettingsScreen()`（`Tab.ME`） | `SettingsScreens.kt` |
| 待办编辑 | `TodoEditScreen(...)` | `TodoEditScreen.kt` |
| 时间面板 | `WhenPanel(...)` | `EventTimeSheet.kt` |
| 快速添加 | `QuickAddSheet(...)` | `QuickAddBar.kt` |
| 课程编辑 | `CourseEditScreen(...)` | `CourseEditScreen.kt` |
| 课表管理 / 节次 | `EditTimetableScreen()` / `PeriodsScreen()` | `EditTimetableScreen.kt` |
| 外观 / 权限 / 关于 | `AppearanceScreen` / `PermissionScreen` / `AboutScreen` | `SettingsScreens.kt` |
| 桌面小组件 | `WidgetsScreen()` | `WidgetsScreen.kt` |
| 账号与同步 / 教务系统 / 成绩与绩点 / 体测成绩 | `SyncScreen()` / `JxfwScreen()` / `GradesScreen()` / `PhysicalTestScreen()` | `SyncScreen.kt` / `JxfwScreen.kt` / `GradesScreen.kt` / `PhysicalTestScreen.kt` |
| 本地 MCP / 局域网管理 | `McpScreen()` / `LanWebScreen()` | `McpScreen.kt` / `LanWebScreen.kt` |
| 数据与备份 / 导入文件 | `DataScreen()` / `ImportScreen(...)` | `DataScreens.kt` |
| 快递取件 / 回收站 / 操作历史 | `ParcelScreen` / `TrashScreen` / `HistoryScreen` | `ManageScreens.kt`（短信规则表单 `RuleEditSheet` 在 `ParcelRules.kt`） |
| 提醒自检 | `ReliabilityScreen()` | `ReliabilityScreen.kt` |
| 改期弹窗 / 晨间弹窗 | `RescheduleSheet` / `MorningSheet` | `TodoSheets.kt` |
