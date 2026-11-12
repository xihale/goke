# CLAUDE.md — 薄荷广工（MintGDUT）设计系统与工程规则

适用范围：仓库根。给 AI 助手改界面或设计系统时遵守。
设计哲学、视觉层级与页面准则见仓库根 `DESIGN.md`；需求与决策背景见 `docs/specs/`、`docs/adr/`；本文件写**代码级约束**。

## 0. 事实来源优先级

1. 代码：`ui/theme/Theme.kt`、`ui/components/`、`ui/screens/`
2. 设计标准：`DESIGN.md`（优雅 · 流畅 · 高效）
3. 文档：`docs/specs/*`、`docs/adr/*`、`docs/todo-ux-notes.md`

冲突时以代码为准。

## 1. 技术栈速查

| 层 | 技术 | 版本 | 位置 |
|---|---|---|---|
| UI | Jetpack Compose + Material 3 **Expressive** | composeBom 2026.09.00 / material3 1.5.0-beta02 | `app/src/main/java/app/mintcal/ui/` |
| 主题生成 | material-kolor（种子色 → 完整 M3 配色） | 5.0.1 | `ui/theme/Theme.kt` |
| 导航 | navigation-compose，type-safe `@Serializable` 路由 | 2.9.5 | `ui/Nav.kt`、`ui/MintCalRoot.kt` |
| 图标 | material-icons-extended（矢量，不落地文件） | BOM 管理 | 各组件 `Icons.*` |
| 构建 | Gradle KTS + AGP 9.3.2 + Kotlin 2.4.10 | minSdk 26 / target 36 / compile 37 | `app/build.gradle.kts`、`gradle/libs.versions.toml` |
| 格式 | Spotless + ktlint_official（Composable 用 PascalCase） | 8.10.4 / 1.8.0 | `.editorconfig` |
| 测试 | `:core` JUnit 5；`:app` Robolectric 4.16（JUnit 4） | — | `app/src/test/java/app/mintcal/ScreensSmokeTest.kt` |
| Web 端 | 原生 ES 模块，**无构建/无框架**（ADR-0006），单 CSS 文件，`go:embed` | — | `server/internal/server/web/app/` |
| 服务端 | Go + SQLite（ADR-0005） | — | `server/` |

模块：`:app`（Compose UI）、`:core`（纯 Kotlin 领域逻辑）、`:script`（QuickJS 沙箱）、`server/`（Go 同步 + Web + 自有 MCP）。

## 2. Design Token

### 2.1 Android —— 唯一真源

`app/src/main/java/app/mintcal/ui/theme/Theme.kt`（367 行）分五层：

| 层 | 符号 | 说明 |
|---|---|---|
| M3 语义色 | `brandLight()` / `brandDark()` | 薄荷手写值，29 个角色；深浅各一套 |
| 主题解析 | `resolveColorScheme(settings, dark)` | DYNAMIC(SDK≥31) → CUSTOM(seed) → PRESET(种子) → MINT 回退 |
| 扩展语义色 | `MintColors` / `mintColors(dark)` | 课程 8 组 bg/fg、added/modified/onModified/removed/conflictBg/success、priority[低中高] |
| 形状 | `MintShapes` | xs 4 / sm 12 / md 16 / lg 24 / xl 28 |
| 字阶 | `MintTypography` | M3 默认 + 下表的覆盖 |
| 动效 | `MintTheme.spec()` | M3 standard spatial spring（阻尼 0.9 / 刚度 700）；系统「移除动画」→ `snap()` |

```kotlin
// 主题色：薄荷是品牌主题，手写；其余预设由种子色生成
private fun brandLight() = lightColorScheme(
    primary = Color(0xFF2E776C),        onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBE3), onPrimaryContainer = Color(0xFF1B473F),
    background = Color(0xFFF2F7F5),     onBackground = Color(0xFF24312D),
    surface = Color(0xFFFFFFFF),        onSurface = Color(0xFF24312D),
    // 副文同时落在白卡和页面底上。#5E6F69 在 #F2F7F5 上 4.91:1，在白上 5.31:1
    onSurfaceVariant = Color(0xFF5E6F69),
    outline = Color(0xFF9AABA6),        outlineVariant = Color(0xFFE4ECE9),
    error = Color(0xFFB23E50),
)

val MintShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),  small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),     large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object MintTheme {
    val colors: MintColors @Composable @ReadOnlyComposable get() = LocalMintColors.current
    /** 普通内容统一使用低层容器，主色留给选中态与操作。 */
    val card: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerLow
    @Composable fun <T> spec(): FiniteAnimationSpec<T> =
        if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.defaultSpatialSpec()
}
```

字阶覆盖（其余沿用 M3 默认，**letterSpacing 全部 0**）：

| Style | 字号/行高/字重 |
|---|---|
| headlineMedium | 26 / 32 / SemiBold |
| titleLarge | 22 / 28 / SemiBold |
| titleMedium | 16 / 24 / Medium |
| bodyLarge | M3 默认 16，字距 0 |
| bodyMedium | 14，字距 0 |
| bodySmall / labelLarge / labelMedium / labelSmall | M3 默认，字距 0 |

间距：**没有 Token 对象**。硬编码 dp，实际是 4 的网格（4/8/12/16 占绝大多数，其次 6/10/24）。

**规则**
- 组件内 NEVER 写 `Color(0xFF…)`。例外仅 `Theme.kt` 与 `res/values/colors.xml`（启动窗口、小组件）。
- 新语义色必须同时进 `brandLight()` 与 `brandDark()`，并在注释里写对比度（现状：onSurfaceVariant 4.91:1 / 5.31:1）。
- 非 M3 语义（课程色、优先级、冲突、成功）加到 `MintColors`，用 `MintTheme.colors.xxx` 取用。
- 新增 token = 改两处：`Theme.kt` ↔ `web/app/js/theme.js`；薄荷的固定值两边必须一致。

### 2.2 Web 端

颜色不写在 CSS 里，由 `theme.js` 以 CSS 变量下发；`app.css :root` 只是首屏回退（薄荷浅色）。

```js
// server/internal/server/web/app/js/theme.js
export const PRESETS = [{ id:'mint', name:'薄荷', seed:'#2E776C' }, /* peach/lavender/sky/rose/lemon */];
export const COURSE_COLORS = { light: [[0xD9E8EF,0x3B6173], /* … 8 组 */], dark: [ /* … */ ] };
export function applyTheme(t, root = document.documentElement) { /* camelToKebab → root.style.setProperty */ }
```

变量名与 M3 角色同名：`--primary`、`--on-surface-variant`、`--surface-high`、`--primary-container`、`--error-container`；
另有 `--radius-s|m|l = 12/20/28`、`--shadow-sm|md`、`--ease: cubic-bezier(.2,.8,.2,1)`、`--nav-h: 68px`、课程色 `--c0..c7-bg|fg`。
薄荷的固定值必须与 Android `brandLight/brandDark` 一致；其它主题按种子推导，**不手调**。
Web 与 Android 浅色薄荷统一为 `primary #2E776C`、`onSurfaceVariant #5E6F69`、`outline #6E7F7A`、`error #B23E50`；深色统一为 `#7CCDBA` / `#93A39E` / `#FF8F84`。

## 3. 组件库

无 Storybook、无 Compose Preview 集合。**组件 = `ui/components/` 的顶层 `@Composable`**，预览靠 Robolectric 截图（见 §9）。

| Compose 符号 | 文件 | 关键规格 |
|---|---|---|
| `MintScreen` | `components/Kit.kt` | 顶栏 64dp，横向 padding 8，标题 titleLarge |
| `MintCard` | 同上 | radius large(24)，内 padding 16，默认 `MintTheme.card` |
| `SectionLabel` | 同上 | labelLarge + onSurfaceVariant，右侧可选 trailing |
| `Tag` | 同上 | 胶囊，padding 10/3，labelMedium |
| `MintCheck` | 同上 | 视觉 22dp，命中 48dp，radius xs |
| `SwitchRow` | 同上 | 标题 bodyLarge + 副标题 bodySmall，纵向 10 |
| `NavRow` | 同上 | 最小高 48，图标 20，尾部 chevron |
| `FloatingNavBar` | 同上 | 岛高 68、内缩 12、radius 全圆、阴影 3dp |
| `EmptyState` | 同上 | 56 圆底 primaryContainer + 28 图标 |
| `MintFab` | 同上 | primaryContainer 底，缩放 0.6 进出 |
| `ConflictCard` | 同上 | `MintTheme.colors.conflictBg` 底，radius 16，padding 14/10 |
| `ConfirmDialog` / `DatePickerDlg` / `TimePickerDlg` / `TimeWheels` | 同上 | 对话框 radius 28；滚轮行高 36×5 行 |
| `AgendaRow` / `TaskRow` | `components/Rows.kt` | 课程块软彩底（radius 16）；待办行直接落在纸面底色上，零分割线、纯留白呼吸，完成态 0.45 退灰 + 删除线 |
| `TonalField` / `FieldText` | `components/FormKit.kt` | 圆角微色阶输入（12dp `surfaceContainerHighest`），零底边横线，聚焦主色细描边，支持密码显隐 |
| `SegmentedPill` | 同上 | 2–4 项等宽互斥滑动胶囊，滑块走 `MintTheme.spec()` 弹簧 |
| `ChipChoice` | 同上 | 流式胶囊单选（5 项及以上），清晰底色包裹 |
| `ValueRow` | 同上 | 标签弱化 + 值主体 + chevron，最小高 48，按压 0.98 |
| `FormSection` | 同上 | 只有小标题，不套卡片 |
| `MonthPager` / `DialPickers` / `AttachmentEditor` / `ChangeList` / `QuickAddBar` | `components/*.kt` | — |

**架构规则**
- 默认主题锁定为 `ThemeSource.PRESET`（`preset = "mint"`），开箱即 `#F2F7F5` 薄荷纸底 + `#2E776C` 森林薄荷绿；跟随壁纸由用户在外观页主动开启。
- 无 ViewModel；状态经 `LocalApp` / `LocalActions` / `LocalNav` CompositionLocal + 参数下传，屏幕是纯函数。
- 所有自绘可点区域统一走 `Modifier.mintClickable` / `mintPressable`（严禁裸 `.clickable` 无视觉反馈）：通栏列表行 `pressedScale = 0.98f`，紧凑按钮/胶囊/色块 `pressedScale = 0.96f`，配触觉反馈，`indication = null`。
- 全局 `LocalIndication` 被替换为 `FocusRingIndication`（**只画键盘/无障碍焦点环**）。
- 命中区 ≥ 48dp；`MintCheck` 是 22dp 视觉 / 48dp 命中。
- 尺寸常量放组件文件顶部，不散落：`NavIslandHeight = 68.dp`、`NavIslandPadding = 12.dp`、`NavClearance = 92.dp`、`NavListInset = NavClearance + 84.dp`。

```kotlin
// 典型用法：卡片 + 分组标题 + 行
MintCard {
    SectionLabel(stringResource(R.string.section_appearance))
    NavRow(title = …, subtitle = …, icon = Icons.Rounded.Palette, trailing = "薄荷") { nav.open(Route.Appearance) }
    SwitchRow(title = …, checked = …, onChange = { … })
}
```

## 4. 资源管理

- **没有位图资产**。`res/drawable/` 只有启动/通知/小组件底：`splash_icon_transparent.xml`、`ic_launcher_foreground.xml`、`ic_notification.xml`、`widget_bg.xml`、`widget_row_bg.xml`、`widget_row_now_bg.xml`。
- 小组件是 **RemoteViews + XML 布局**（`res/layout/widget_*.xml`）+ `values-night/colors.xml`，**不吃 Compose 主题**；改主题色时别忘了它（`widget_accent #2E776C`、`widget_bg #F2F7F5`）。
- 需要新插图：优先 vector drawable（24×24 网格、`tint`/`currentColor`），许可证记入 `docs/DEPENDENCIES.md`。
- 无 CDN、无外链字体、无外部图片；Web CSP 为 `script-src 'self'; style-src 'self'`，禁止内联样式与拼 HTML。

## 5. 图标系统

- Android：`androidx.compose.material.icons`（extended 包）。**Rounded 变体**为默认；方向性图标必须 `AutoMirrored`（`ArrowBack`、`KeyboardArrowRight/Left`、`Undo`）以支持 RTL。
- 尺寸阶梯：**24** 导航/工具栏 · **20** 行内 · **16** 标签与冲突卡。
- Web：`dom.js` 内 `ICONS` 映射（24×24 viewBox、stroke 1.8、round cap/join），`icon(name, size = 22)`；名字是短语义名：`sun grid calendar check user settings plus left right edit trash sync cloudOff x clock pin repeat flag list download logout external coffee sparkle more block`。
- 新增图标先查：Compose 是否已有同名 Material 图标 → Web `ICONS` 是否有 → 再决定是否自绘 vector。

## 6. 样式方法

- **Compose 主题化**：`MaterialTheme.colorScheme/typography/shapes` + `MintTheme.colors/card/spec()`。没有 CSS-in-JS、没有样式表。
- **文案**：736 条全在 `res/values/strings.xml`（简体中文默认，`values-en` 预留）。代码里不写死中文；检查 `python3 scripts/check_strings.py --unused`。
- **响应式**：Android 只做手机（基准 `w411dp-h891dp`，状态栏 24dp，底栏净空 92dp）。Web 手机优先，断点 700 / 900px（底栏 ↔ 侧栏、抽屉 ↔ 居中弹窗、单栏 ↔ 双栏）。无平板专用布局。
- **动效**：统一 `MintTheme.spec()`；进度环、撤销圆钮用 Canvas 自绘；`prefers-reduced-motion` 与系统「移除动画」→ 直接切换。

## 7. 工程结构

```
app/src/main/java/app/mintcal/
├── MintCalApp.kt / MainActivity.kt / DeepLink.kt
├── ui/
│   ├── MintCalRoot.kt      # Tabs（4 个常驻 Tab）+ mintDestinations 路由表
│   ├── Nav.kt              # Route sealed interface、Nav 门面、Actions（变更集）
│   ├── Common.kt           # LocalTabVisible / gated / liveSnapshot / rememberNow
│   ├── Format.kt
│   ├── theme/Theme.kt      # ★ 全部设计 token
│   ├── components/         # ★ 可复用组件（Kit / Rows / FormKit / MonthPager / DialPickers …）
│   └── screens/            # 按功能分文件，一屏一文件，大屏 700~1400 行
├── data/  (Repository / Settings / Attachments / SecretStore …)
core/src/main/kotlin/app/mintcal/core/   # 领域逻辑（无 Android 依赖）
├── model/ agenda/ change/ recurrence/ todo/ time/ importer/ export/ sync/ jxfw/ parcel/
script/                                   # QuickJS 沙箱（高级修改）
server/internal/server/web/app/           # Web 客户端（js/views + app.css）
```

屏幕 ↔ 文件：

| 屏幕 | Compose 入口 | 文件 |
|---|---|---|
| 今日 | `TodayScreen()`（Tab.TODAY） | `screens/TodayScreen.kt` |
| 周历 | `TimetableScreen()`（Tab.TIMETABLE） | `screens/TimetableScreen.kt`（含私有 `SlotSheet`/`CourseSheet`） |
| 日历 | `CalendarScreen()` | `screens/CalendarScreen.kt` |
| 待办 | `TodoScreen()` | `screens/TodoScreen.kt` + `TodoRows.kt` + `QuickAddBar.kt` |
| 待办编辑 | `TodoEditScreen(...)` | `screens/TodoEditScreen.kt` |
| 时间面板 | `WhenPanel(...)` | `screens/EventTimeSheet.kt` |
| 快速添加 | `QuickAddSheet(...)` | `screens/QuickAddBar.kt` |
| 课程编辑 | `CourseEditScreen(id)` | `screens/CourseEditScreen.kt` |
| 课表管理 / 节次 | `EditTimetableScreen()` / `PeriodsScreen()` | `screens/EditTimetableScreen.kt` |
| 设置 / 外观 / 权限 / 关于 | `SettingsScreen` / `AppearanceScreen` / `PermissionScreen` / `AboutScreen` | `screens/SettingsScreens.kt` |
| 账号与同步 / 教务系统 | `SyncScreen()` / `JxfwScreen()` | `screens/SyncScreen.kt` / `JxfwScreen.kt` |
| 数据与备份 / 导入文件 | `DataScreen()` / `ImportScreen(uri)` | `screens/DataScreens.kt` |
| 快递取件 / 回收站 / 操作历史 | `ParcelScreen` / `TrashScreen` / `HistoryScreen` | `screens/ManageScreens.kt`（规则表单在 `ParcelRules.kt`） |
| 提醒自检 | `ReliabilityScreen()` | `screens/ReliabilityScreen.kt` |
| 改期弹窗 / 晨间弹窗 | `RescheduleSheet` / `MorningSheet` | `screens/TodoSheets.kt` |
| 高级修改 | `AdvancedFullScreen()` / `AdvancedSheet()` | `screens/AdvancedUi.kt` |

路由：`Route` 是 `@Serializable sealed interface`（`Nav.kt`），压栈页注册在 `MintCalRoot.kt` 的 `NavGraphBuilder.mintDestinations`。Tab 层常驻，切换零重组。

## 8. 无障碍与质量门槛

- 触控目标 ≥ 48dp；文字对比 ≥ 4.5:1（正文 onSurfaceVariant 已按 4.91:1 校核）。
- 状态不能只靠颜色（完成态 = 退灰 + 删除线；优先级 = 色 + 粗细/文字）。
- 图标按钮必须有 `contentDescription`（Compose）或 `aria-label`（Web）。

## 9. 常用命令

```bash
./gradlew :app:testDebugUnitTest     # Robolectric 渲染冒烟 + 截图 app/build/shots
./gradlew :app:assembleDebug         # debug APK（包名 app.mintcal.debug）
./gradlew spotlessCheck              # ktlint（spotlessApply 自动修）
python3 scripts/check_strings.py --unused
bash scripts/sync-e2e.sh             # 同步端到端
```

## 10. 改设计前的检查清单

1. 目标屏对应哪个 Compose 文件（见 §7 表）？用到的组件是否已存在（§3 表）？存在就复用，不存在先建组件再落屏。
2. 用到的颜色/圆角/字阶是否已存在？不存在 → 按 §2 改两处（`Theme.kt` ↔ `web/app/js/theme.js`），禁止硬编码。
3. 状态栏/底栏净空是否守住 24 / 92？
4. 触控 48dp、对比 4.5:1、状态不只靠颜色、图标有可访问名（§8）？
5. 改完是否跑过 `:app:testDebugUnitTest` 并肉眼比对 `app/build/shots/*.png`（深浅主题都查）？
