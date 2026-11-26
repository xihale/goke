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
app/src/main/java/app/goke/
├── GokeApp.kt / MainActivity.kt / DeepLink.kt / BenchSeed.kt
├── ui/
│   ├── GokeRoot.kt      # Tabs（5 个常驻 Tab）+ RouteStackHost（状态驱动返回栈）+ 撤销浮钮
│   ├── Nav.kt              # Route sealed interface、BackEntry / Nav 返回栈、Actions（变更集）
│   ├── Common.kt           # LocalTabVisible / gated / liveSnapshot / rememberNow
│   ├── Format.kt
│   ├── theme/Theme.kt      # ★ 全部设计 Token（详见 DESIGN.md）
│   ├── components/         # ★ 可复用 UI 组件（Kit / Rows / FormKit / MonthPager / DialPickers …）
│   └── screens/            # 按功能分文件，一屏一文件
├── data/                   # Repository / Settings / Attachments / SecretStore …
├── calendar/ jxfw/ lanweb/ links/ mcp/ notify/ share/ sync/ widget/ advanced/
core/src/main/kotlin/app/goke/core/
├── model/ agenda/ change/ recurrence/ todo/ time/ importer/ export/ sync/ jxfw/ parcel/ util/
script/                     # QuickJS 沙箱
server/internal/server/web/app/   # Web 客户端（js/views + app.css）
```

## 状态流与路由规则

- **无 ViewModel**：状态经 `LocalApp` / `LocalActions` / `LocalNav` CompositionLocal 与参数下传，屏幕保持纯函数。
- **写操作统一走变更集**：页面通过 `LocalActions.current.commit(cs, message, undoable)` 提交 `ChangeSet`，可撤销操作通过左下角撤销浮钮回滚，不可撤销操作退化为系统 Toast。
- **路由表与返回栈**：`Route` 为 `@Serializable sealed interface`（`ui/Nav.kt`），`Nav.stack`（`SnapshotStateList<BackEntry>`）作为唯一返回栈真源；压栈页统一经 `ui/GokeRoot.kt` 的 `RouteStackHost`（状态驱动转场 + `PredictiveBackHandler` 预测性返回 + `rememberSaveableStateHolder` + 穷尽 `when (route)`）渲染，新增路由缺漏即编译期报错。
- **Tab 层常驻**：底层 `Tabs` 常驻承载 `Tab.TODAY` / `Tab.TIMETABLE` / `Tab.CALENDAR` / `Tab.TODO` / `Tab.ME` 五个 Tab，切换零重组，不参与压栈转场；压栈页覆盖其上，弹栈后底层 Tab 状态零重建。
- **返回导航规范（Back-Navigation Best Practices）**：
  1. **单一真源**：所有二级页必须通过 `Route` + `RouteStackHost` 进出栈，禁止再引入 `NavHost`、`ModalHost` 或额外的根级 `BackHandler`；窗口内抽屉须挂载在所属 Tab/Route 子树内。
  2. **结构性分发隔离**：`Tabs` 的每个 Tab 与 `RouteHost` 均使用 `rememberNavigationEventDispatcherOwner(enabled = isActive, parent = rootOwner)` 隔离返回分发，隐藏态 Tab 与退场/被覆盖的二级页在架构上无法截获系统返回。
  3. **子页 `BackHandler` 约束**：仅用于页内瞬态（如抽屉、筛选、搜索、未保存编辑确认），必须显式绑定 `enabled = <状态条件>`；编辑页校验失败时仍须提供「放弃修改」退出路径，严禁仅弹 Toast 导致用户被困。
  4. **防重入弹栈与测试守卫**：顶栏与系统返回统一走 `nav.pop(entry.id)` 防退出期误弹下层页；新增 `Route` 须同步加入 `AllRoutesNavigationTest`（反射校验 1:1 覆盖全部 sealed 子类）。

## 标准组件与自定义例外规范（Ponytail 原则）

**默认原则**：优先使用 Material 3 / AndroidX 官方标准组件及项目统一薄封装，禁止在各页面复制脚手架或发明重复容器；确实需要自定义组件时，**必须**在组件 KDoc 与下表写明为何不用官方默认方案：

- **统一容器（必须复用）**：
  - 二级页骨架：`MintScreen`（状态栏/IME 避让 + 64dp 顶栏 + 680dp 平板居中）。
  - 底部抽屉：`MintBottomSheet`（`FormKit.kt`，统一 28dp 顶角与跳过半展开态）。
  - 确认弹窗：`ConfirmDialog` / M3 `AlertDialog`。
- **经审计保留的自定义组件与明确理由**：
  - `RouteStackHost` / `Nav`（`GokeRoot.kt` / `Nav.kt`）：替代 `NavHost`，避免 `navigation-compose` 内置 `PredictiveBackHandler` 与多 Tab/手势撤销组合导致返回锁死，且本工程无 ViewModel / SavedStateHandle。
  - `InWindowSheet` / `QuickAddSheet` / `WidgetOverlayRoot`（复用 `FormKit.kt` 的 `MintSheetOverlay`）：课表快速预览、快速添加及桌面悬浮层共用同一套窗口内物理抽屉容器，避免 `DialogWindow` Binder 阻塞与输入法抢焦点，同时统一弹簧入退场、1:1 下拉关闭与预测性返回手势。
  - `DatePickerDlg` + `MonthPager`（`Kit.kt` / `MonthPager.kt`）：复用 M3 `DatePickerDialog`，仅替换内容为 `MonthPager` 以保留「今天/明天」直达及农历/教学周标记（官方 `DatePicker` 高度过大且不支持教学周）。
  - `TimePickerDlg` + `TimeWheels` / `DialDialog`（`Kit.kt` / `DialPickers.kt`）：分别用双列滚轮与 24h 双环表盘替代 M3 时钟，优化手机单手选分钟与快速时长输入手感。
  - `MintCheck` & `mintClickable` / `mintPressable` & `FloatingNavBar`（`Kit.kt`）：基于 `Modifier.Node` / `Canvas` 自绘，保证 48dp 最小命中区、点击零振动、静止零协程及列表/底栏滑动零多余重组。

## 屏幕 ↔ 文件映射

| 屏幕 / 浮层 | Compose 入口 | 文件（`app/src/main/java/app/goke/ui/screens/`） |
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
| 外观 / 显示与交互 / 提醒与通知 / 权限 / 关于 | `AppearanceScreen` / `PreferencesScreen` / `RemindersScreen` / `PermissionScreen` / `AboutScreen` | `SettingsScreens.kt` |
| 桌面小组件 | `WidgetsScreen()` | `WidgetsScreen.kt` |
| 账号与同步 / 系统日历同步 / 教务系统 / 成绩与绩点 / 体测成绩 | `SyncScreen()` / `CalendarSyncScreen()` / `JxfwScreen()` / `GradesScreen()` / `PhysicalTestScreen()` | `SyncScreen.kt` / `JxfwScreen.kt` / `GradesScreen.kt` / `PhysicalTestScreen.kt` |
| 本地 MCP / 局域网管理 | `McpScreen()` / `LanWebScreen()` | `McpScreen.kt` / `LanWebScreen.kt` |
| 数据与备份 / 导入文件 | `DataScreen()` / `ImportScreen(...)` | `DataScreens.kt` |
| 快递取件 / 回收站 / 操作历史 | `ParcelScreen` / `TrashScreen` / `HistoryScreen` | `ManageScreens.kt`（短信规则表单 `RuleEditSheet` 在 `ParcelRules.kt`） |
| 提醒自检 | `ReliabilityScreen()` | `ReliabilityScreen.kt` |
| 改期弹窗 / 晨间弹窗 | `RescheduleSheet` / `MorningSheet` | `TodoSheets.kt` |
