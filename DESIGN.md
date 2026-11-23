---
name: Goke (工课)
description: 清新、无框、一屏直达的广工教务课表 · 日历 · 待办 · 校园取件一体式效率工具
colors:
  primary: "#2E776C"
  on-primary: "#FFFFFF"
  primary-container: "#CDEBE3"
  on-primary-container: "#1B473F"
  secondary: "#B4694B"
  secondary-container: "#FBE3D6"
  on-secondary-container: "#7A4630"
  tertiary: "#6C63B5"
  tertiary-container: "#E4E0F6"
  on-tertiary-container: "#3D3873"
  background: "#F2F7F5"
  on-background: "#24312D"
  surface: "#FFFFFF"
  surface-container-low: "#FFFFFF"
  surface-container-high: "#F7FAF9"
  surface-container-highest: "#E9F1EE"
  on-surface: "#24312D"
  on-surface-variant: "#5E6F69"
  outline: "#6E7F7A"
  outline-variant: "#E4ECE9"
  error: "#B23E50"
  error-container: "#FCE9E7"
  success: "#46A171"
  priority-medium: "#B07F1E"
  dark-primary: "#7CCDBA"
  dark-primary-container: "#24413B"
  dark-background: "#141A19"
  dark-surface: "#1E2624"
  dark-on-surface: "#E6EEEB"
  dark-on-surface-variant: "#93A39E"
  course-sky-bg: "#D9E8EF"
  course-sky-fg: "#3B6173"
  course-mint-bg: "#BFDCD5"
  course-mint-fg: "#2C5B53"
  course-iris-bg: "#DAD8EE"
  course-iris-fg: "#4F4D7C"
  course-wheat-bg: "#F3EBCF"
  course-wheat-fg: "#75662F"
  course-peach-bg: "#F7DFD2"
  course-peach-fg: "#85503A"
  course-sage-bg: "#DFE8DA"
  course-sage-fg: "#4B5F49"
  course-rose-bg: "#F4D9E0"
  course-rose-fg: "#83465A"
  course-mist-bg: "#D2D8EA"
  course-mist-fg: "#454F7A"
typography:
  headline-medium:
    fontFamily: "sans-serif"
    fontSize: "26px"
    fontWeight: 600
    lineHeight: 1.23
    letterSpacing: "0em"
  title-large:
    fontFamily: "sans-serif"
    fontSize: "22px"
    fontWeight: 600
    lineHeight: 1.27
    letterSpacing: "0em"
  title-medium:
    fontFamily: "sans-serif"
    fontSize: "16px"
    fontWeight: 500
    lineHeight: 1.5
    letterSpacing: "0em"
  body-large:
    fontFamily: "sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "0em"
  body-medium:
    fontFamily: "sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.43
    letterSpacing: "0em"
  label-large:
    fontFamily: "sans-serif"
    fontSize: "14px"
    fontWeight: 500
    lineHeight: 1.43
    letterSpacing: "0em"
  label-medium:
    fontFamily: "sans-serif"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: 1.33
    letterSpacing: "0em"
rounded:
  xs: "4px"
  sm: "12px"
  md: "16px"
  lg: "24px"
  xl: "28px"
  pill: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
components:
  paper-task-row:
    backgroundColor: "{colors.background}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-large}"
    padding: "8px 12px"
  flat-input:
    backgroundColor: "{colors.background}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-large}"
    padding: "6px 0px"
  tonal-field:
    backgroundColor: "{colors.surface-container-highest}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-large}"
    rounded: "{rounded.sm}"
    padding: "12px 14px"
  segmented-pill:
    backgroundColor: "{colors.surface-container-highest}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.label-large}"
    rounded: "{rounded.pill}"
    padding: "4px"
  tag-pill:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    typography: "{typography.label-medium}"
    rounded: "{rounded.pill}"
    padding: "3px 10px"
  tonal-card:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.lg}"
    padding: "16px"
  floating-nav:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface-variant}"
    rounded: "{rounded.pill}"
    height: "68px"
    padding: "4px"
  mint-check:
    textColor: "{colors.outline}"
    rounded: "{rounded.xs}"
    size: "22px"
  fab-mint:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    rounded: "{rounded.md}"
    size: "56px"
---

# Design System: Goke (工课)

## Overview

**Creative North Star: "素纸 Goke（Clean Paper Grid）—— 宣纸留白、轻弹指尖、广工一屏直达"**

「Goke」是专为广东工业大学师生打造的「教务课表 + 日历 + 待办 + 校园取件」一体式效率工具。全应用围绕 **优雅、流畅、高效** 三大原点构建：文字与待办像写在温润轻盈的青瓷素纸底上，不堆砌厚重的灰白盒子，不用传统描边或底边横线切割视线，更不把底层技术语法（如短信匹配模板、全周次重影网格）直接甩给用户。

界面的校园温度来自**对广工真实日常的懂行裁剪**——打开即对齐广工统一认证（`jxfw`）课表、校历周次与全校统一作息，自动把冗长的实验机房串浓缩成干净短句，把校园驿站的取件码提炼成抬手可点的票据胶囊。每一次触碰都有跟手的轻弹缩放；触觉只留给长按、拖动、滑动提交这类真正的手势，点击不振动。每一屏只呈现当下决策所需的最小信息集。

**Key Characteristics:**

- **宣纸分层（Paper & Pastel Hierarchy）**：待办与设置行无框直落在薄荷宣纸底上，纯靠纵向留白呼吸；仅固定占用时间的课程与日程穿着低饱和植物色块，一眼区分「时间块」与「可勾选项」。
- **广工原生（GDUT-Native Defaults）**：教务同步是一等公民，内置广工统一作息节次，多机房/实验室地点智能折叠，校园取件开箱即用。
- **指尖轻弹（Tactile Fluidity）**：全局移除点击水波纹闪烁，所有可交互元素按尺寸分层接入物理弹簧（通栏大行 `0.98x`、紧凑控件 `0.96x`），绝无「死水点击」；点击不振动，触觉只出现在长按 / 拖动吸附 / 滑动提交。
- **一屏一事（Single-Screen Focus）**：五个常驻核心入口（今日 / 课表 / 日历 / 待办 / 我的）各司其职，不跨页重复堆叠「下一件事」大卡，不在自明操作下写教程长句。
- **桌面同源（Home Screen Continuity）**：桌面小组件（今日日程、今日课表、待办清单、待取快递、下一件事）与主应用默认同享一套薄荷语义色与一键完成体验。

## Colors

色彩围绕「森林薄荷叶与温润宣纸」展开，默认启用手写调校的薄荷品牌色板，同时提供 6 套低饱和植物预设、自定义种子色与 Android 12+ 动态壁纸取色（深浅双模式同步校核）。

### Primary
- **森林薄荷绿（Forest Mint）**：用于核心主按钮、当前选中态锚点、今日日期圆标与完成进度环。
- **薄荷嫩芽容器（Soft Mint Container）**：专供右下角悬浮新建按钮（FAB）、取件码票据胶囊、课表列顶柔性徽标与小组件正在进行的课程高亮，温润醒目而不刺眼。

### Secondary
- **暖陶土棕（Warm Terracotta）**：用于次级温暖强调、调停课提示与部分暖调状态徽标，平衡冷绿基调。

### Tertiary
- **鸢尾雾紫（Iris Mist）**：用于考试、倒数日等特殊日程标记及辅助分类胶囊。

### Neutral & Botanical Pastels
- **薄荷宣纸底与纯白浮岛（Mint Sheet & Pure Surface）**：浅色模式以极淡薄荷灰绿为全局纸面底，纯白仅留给底部导航浮岛、底部抽屉与极少数跨模块聚合摘要卡；深色模式采用深松石夜色底配微提亮容器，避免纯黑眩光。弱化副文（On Surface Variant）在宣纸底上保持 `4.91:1` 对比度，在纯白容器上保持 `5.31:1`。
- **8 组植物课表色（Botanical Course Pairs）**：天青、薄荷、鸢尾、麦穗、蜜桃、鼠尾草、蔷薇、雾蓝 8 组同色系浅底配深字（深浅模式独立调校），专供课表网格与时间轴课程块使用。
- **语义信号色（Semantic Signals）**：低优先级用安静灰绿、中优先级用暖琥珀金、高优先级与逾期/删除共用克制的莓果红；新增/成功状态用清透翠绿。

**The Mint-First Default Rule.** 新装默认锁定「预设 · 薄荷」品牌色板，确保主应用开箱即呈现薄荷宣纸气质并与桌面小组件完全同源；「跟随壁纸」保留在外观设置中由用户主动开启。

**The Calm-Surface Rule.** 森林薄荷主色在单屏内的着色面积不超过 10%，严禁大面积铺满普通列表卡片或顶栏背景。

**The Gray-Out & Strikethrough Completion Rule.** 已完成的待办、已取走的包裹必须整行平滑退灰（`0.45` 透明度）并同时为标题添加删除线；已下课的今日课程淡化背景至 `0.45` 透明度，把视觉焦点全部让位给未完成事项。

## Typography

全局采用系统默认无衬线字族（按 [ADR-0004](docs/adr/0004-defaults.md) 暂不内置打包字体文件，待后续选定圆润标题字体后再接入），所有字阶的字距（`letterSpacing`）统一归零（`0em` / `0sp`）以收紧中文排版；时间列与取件码读数保持紧凑对齐。

- **无框大标题与主读数（Headline Medium 26px / Title Large 22px, SemiBold）**：用于待办与课程编辑页首行无框标题、今日日期主标题及二级页顶栏标题，靠字重而非边框建立锚点。
- **行内主体与课程名（Title Medium 16px Medium / Body Large 16px Regular）**：用于课程块标题、无框待办行标题、表单值与设置主行，常规单行或最多两行截断。
- **时间轴、地点与胶囊标签（Body Medium 14px / Label Large 14px / Label Medium 12px）**：用于左侧 40dp 时间槽、教室/驿站副文、分组小标题与全圆胶囊 `Tag`。

**The One-Number Rule.** 同一屏内同一组统计（如今日待办进度、分区未完成数、学期总门数）只用一种形式表达一次，绝不在标题、副标题与圆环中心重复报数。

**The No-Colon-Stack & Smart-Fold Rule.** 属性展示统一采用「左弱化标签 + 右对齐值」或「`周一 1–2节 · 1–14周 · 教3-204`」中点连接，禁止堆砌多行「教室：xxx / 教师：xxx」；教务导入的多机房/多实验室长串（超过 2 处）必须自动折叠为 `实4-401 等 8 处`，点击展开详情。

## Layout

页面遵循手机单手友好的流式单栏栅格（最大内容宽 `680dp` 居中），严格以 **4dp 基准网格**（`4 / 8 / 12 / 16 / 24dp`）控制留白节奏。

- **五常驻核心入口（5 Core Tabs）**：底部胶囊浮岛固定承载 **今日 · 课表 · 日历 · 待办 · 我的** 五个一级视图（废除易与日历周模式混淆的「周历」旧称）。
- **统一顶栏秩序（64dp Unified Top Bar）**：五个一级 Tab 与所有二级子页共享统一的 `64dp` 顶栏基线与 `16dp` 标题左轴（二级页含返回键时紧凑对齐），右侧操作图标不超过 2 个（`48dp` 热区）。编辑页顶栏隐去文字标题，把第一视觉焦点让给正文首行大字输入框。
- **浮动底栏净空（Floating Island Clearance）**：胶囊底栏高 `68dp`、外边距 `12dp`（总净空 `92dp`）；一级长列表底部统一留白 `NavListInset`（`176dp`），确保末行内容可完全滚出底栏与右下角 FAB 之上。
- **左轴对齐律（Two-Axis Discipline）**：同一列表内最多存在两条纵轴——左侧 `48dp` 槽位（容纳 `40dp` 时间文本或 `22dp` 勾选框居中）与右侧正文起点完全对齐，混排课程与待办时文字左沿严丝合缝。

**The Paper-Row vs. Time-Block Rule.** 普通待办行与设置行一律「无框直落」在页面宣纸底上，**纯靠纵向留白呼吸区分（零行间分隔线、零独立圆角底盒）**；仅固定时段的课程块、日程块使用植物色圆角填充，仅跨模块概览（如今日顶部摘要、教务同步状态卡）使用单层纯白 `MintCard`。

**The Clean-Grid Rule.** 课表节次主网格只渲染真正占用专注时间的课程与短时间块（`≤ 3 小时`）；跨度超过 3 小时的柔性时间窗（如 `14:00–22:00` 默认顺路取件时段）严禁拉成通天色柱或挤压出无字细条，一律收折为当日列头的紧凑胶囊徽标。

**The Single-Home Ownership Rule.** 「下一件事」摘要卡归「今日」页独占；「待办」页顶栏下方只保留精简筛选条，首屏（`w411×h891dp`）必须完整露出至少 **6 条**有效待办事项。

## Elevation & Depth

Goke 追求贴近纸面的轻盈质感，靠色调层级（Tonal Surface）与物理微缩反馈替代传统投影与水波纹。

- **单处轻浮岛（3dp Island Shadow）**：全应用仅底部胶囊导航栏（`FloatingNavBar`）与右下角 `MintFab` 携带 `3dp` 柔和阴影，轻浮于宣纸背景之上；其余卡片、输入框与抽屉一律零阴影，靠纯白（`surfaceContainerLow`）或微深薄荷灰（`surfaceContainerHighest`）色阶区分层次。
- **极细分隔线（0.5dp Hairline）**：仅在固定工具栏/抽屉底栏与滚动内容交界处使用 `45%` 透明度的 `outlineVariant` `0.5dp` 细线；待办与课程列表行之间一律零分隔线。

**The Tactile Press Rule.** 全局禁用点击底色水波纹（`FocusRingIndication` 仅绘制键盘/无障碍 `2dp` 焦点环），因此**所有可点击元素**必须 100% 接入 `mintPressable` / `mintClickable`：按下瞬间做分层几何回缩——**通栏列表大行采用克制的 `0.98x` 轻缩，胶囊/按钮/色块/图标等紧凑控件采用 `0.96x` 轻缩**——动画走 `MintTheme.press()`（M3 fast spatial：阻尼 `1` / 刚度 `1400`，约 150ms 到位，无回弹尾巴），且动画值只在 draw 阶段读（`mintPressScale`），不逐帧重组内容；系统开启「移除动画」时自动切换为 `snap()` 直切。**点击不振动**：触觉只给长按启动拖动、拖动到吸附档、滑动提交、时间轮/刻度吸附；长按的触觉由 `combinedClickable` 自己发，不再手写叠加。严禁在业务代码中遗留无视觉回执的裸 `.clickable`。

## Shapes

圆角阶梯遵循「外大内小」的同心曲率协调，用圆润几何传递亲和力：

- **微圆角勾选框（4dp Extra Small）**：`MintCheck`（`22dp` 视觉框）采用微圆角方形，与圆形徽标形成明确语义区分——**方形代表可勾选完成，全圆胶囊代表属性或状态**。
- **课表块与微底托输入框（12dp Small / 16dp Medium）**：课表网格课程块、二级页微底托输入框（`TonalField`）、时间轴课程行与 `MintFab` 采用 `12dp–16dp` 圆角，利落不松散。
- **概览大卡与底部抽屉（24dp Large / 28dp Extra Large）**：`MintCard`、模态对话框、底部操作抽屉（`ModalBottomSheet`）与桌面小组件外框统一采用 `24dp–28dp` 大圆角，贴合现代手机屏幕物理 R 角。
- **全圆胶囊（999dp Pill）**：底部导航浮岛、滑动分段器（`SegmentedPill`）、筛选芯片与课表列顶柔性徽标统一使用全圆胶囊。

**The Rhythm-Safe Inline Rule.** 行内可点子元素（如 `ParcelCodeHeadline`）严禁通过内边距把文本容器自身的布局高度撑到 `48dp`，从而避免在主标题与副标题之间撕裂出异常行距；独立 `40–48dp` 触控热区交给行尾专用操作图标（如 `ParcelCodeCopyButton`）。

## Components

- **无框待办行与植物色日程块（`TaskRow` & `AgendaRow`）**：待办行（含待取快递）透明直落宣纸底，纯靠留白呼吸，左置 `MintCheck`（支持优先级染圈），右置单行/双行标题与精简时间副文；课程与固定日程块采用 `16dp` 植物色底，左侧 `40dp` 粗体时间槽与待办勾选框同轴对齐。
- **双态无框输入体系（`FlatField` & `TonalField`）**：
  - *正文编辑页*（待办编辑、课程编辑、快速添加）：使用完全透明的 `FlatField`；主标题行内实时高亮日期/优先级词（点按高亮词即可消解），不再在同屏底部重复罗列相同含义的胶囊芯片。
  - *结构化表单子页*（广工教务登录、自建同步服务器、学期名称）：统一使用 `12dp` 圆角、无描边的微底托 `TonalField`（`surfaceContainerHighest`），彻底废除老式底边单横线输入框。
- **胶囊滑动分段器（`SegmentedPill`）**：用于日历「月 / 周」切换、外观「跟随壁纸 / 预设 / 自定义」及「跟随系统 / 浅色 / 深色」等 2–4 项互斥切换；带纯白圆角滑块沿 `MintTheme.spec()` 平滑位移，彻底取代无底托、像普通正文一样散落的纯文字单选。
- **结构化课程编辑器与节次设置（`CourseEditScreen` & `PeriodsScreen`）**：
  - 课程编辑采用「无框课名 + 8 色植物圆点 + **排课时段列表（Time-Slot Rows）** + 教师/提醒/备注」布局；每条排课时段支持独立设置星期、节次、周次（全部/单周/双周/自定义）与独立教室（完美兼容广工理论课与实验课异地排课），点击行弹出清爽的底部抽屉编辑。**严禁在纵向滚动表单内嵌套微缩全量课表画笔**；看格子选时间直接在主「课表」Tab 点击空白节次格触发。
  - 节次设置页默认采用广工统一作息（`08:30 / 13:50 / 18:30`），保留单节微调列表及右上角恢复默认入口。
- **广工教务同步卡（`JxfwSyncCard`）**：作为空课表首屏第一引导与「课表管理」页顶部核心卡片，展示当前绑定的学号、学期（如 `2026-2027 学年第一学期`）与上次同步时间，支持一键刷新课表与考试安排；课程管理列表按课程名聚合展示，不把同一门课的多条排课拆散平铺。
- **校园取件中心与无框取件行（`ParcelScreen` & `ParcelCodeHeadline`）**：
  - 列表行去掉灰底盒与前置胶囊，与普通待办严守同一左轴：首行用森林薄荷色半粗等宽数字放大展示取件码（如 **`4-2-1086`**），次行收拢驿站、快递公司与截止时间（`东门菜鸟驿站 · 顺丰 · 今天 22:00`），行尾附安静复制图标；轻触取件码或复制图标均一键复制，复制结果走 Toast，不振动，勾选即完成取件。
  - 取件设置子页首屏只呈现「自动识别通知开关 + 默认顺路取件时间窗 + 粘贴短信测试」；底层 `{code}` / `{place}` 匹配规则模板全部收折进二级「短信识别规则」页面。
- **浮动胶囊底栏（`FloatingNavBar`）**：5 个常驻入口（今日 / 课表 / 日历 / 待办 / 我的），选中胶囊滑块在 Canvas 层平滑滑动，零多余重组。
- **桌面小组件家族（RemoteViews Suite）**：今日日程、今日课表、待办清单、待取快递、下一件事（`4×1` 单行紧凑展示眼下最重要的一件事与快速添加）五款小组件，统一 `24dp` 外圆角、`14–16dp` 内边距与薄荷宣纸语义色。

## Do's and Don'ts

**Do:**

- 新增或重构二级子页（设置、教务、同步、备份、取件、自检）时，统一使用 `MintScreen` + `FormSection` + 无框行（`ValueRow` / `SwitchRow` / `NavRow`）或微底托 `TonalField`，保持全应用如出一辙的纸面呼吸感。
- 空状态只保留「`56dp` 柔和圆底图标 + 一句 8 字内短标题 + 最多 2 个清晰动作按钮」（例如空课表直接给「广工教务导课」与「手动创建学期」）。
- 提醒可靠性自检页（`ReliabilityScreen`）自动识别当前设备厂商（小米 / OPPO / vivo / 华为等），只呈现本机对应的极简 3 步开启卡片，不铺陈无关品牌的说明书长文。
- 所有图标按钮与自绘点击区必须提供准确的 `contentDescription` 并守住 `48dp` 最小无障碍触控尺寸。
- 修改任何颜色、圆角或组件规范时，同步对齐 Android `Theme.kt`、小组件 `colors.xml`（含 `values-night`）与 Web 端 `theme.js`，并用 Robolectric 在**薄荷主题深浅双模式 + 完整底栏壳层**下出图验视。

**Don't:**

- **禁止每行套盒与卡中卡**：不要给普通待办列表行单独套 `MintCard` / 圆角灰底或画行间分隔线，也不要在 `MintCard` 内部再嵌套带背景色的子卡片。
- **禁止残留底边线或描边输入框**：严禁混用 Material 默认 `OutlinedTextField` 或老式底边 `HorizontalDivider` 输入框。
- **禁止无按压回弹的裸点击**：在全局无水波纹前提下，业务代码中严禁对可交互块使用未带缩放反馈的裸 `.clickable`；同样不要给点击加振动（触觉只属于手势）。
- **禁止底层语法直出首屏**：不要在取件页首屏直铺 `{code}` 模板代码，不要在快速添加框同时展示行内高亮块与底部重复芯片。
- **禁止文字堆砌、旧名残留与硬编码**：界面文案与 `strings.xml` 中彻底清理「薄荷日程」「专注计时」等历史遗留词，业务屏幕内严禁硬编码 `Color(0xFF...)` 或中文字符串。
- **禁止外部同步污染撤销链**：系统日历与教务后台增量同步写回时，不得制造多余的用户历史记录或破坏本地 `canUndo` 连续撤销体验。

## Implementation & Cross-Platform Mapping

### Token 真源与跨端同步

- **Android（唯一真源）**：`app/src/main/java/app/goke/ui/theme/Theme.kt`
  - 品牌主题色：`brandLight()` / `brandDark()`（薄荷为手写品牌 Token；其余预设与自定义由种子色通过 `material-kolor` 生成）。
  - 主题解析链 `resolveColorScheme(settings, dark)`：`DYNAMIC`（SDK ≥ 31）→ `CUSTOM`（seed）→ `PRESET`（非 `MINT` 种子）→ `MINT` 回退。
  - 扩展语义色 `MintColors`（`MintTheme.colors`）：8 组课程 `courseBg`/`courseFg`、`added`/`modified`/`onModified`/`removed`/`conflictBg`/`success`、`priority`（低/中/高）。
  - 形状 `MintShapes`（4 / 12 / 16 / 24 / 28dp）、字阶 `MintTypography`（所有 `letterSpacing` 归零）、动效 `MintTheme.spec()`（M3 standard spatial spring）与 `MintTheme.press()`（M3 fast spatial spring；系统「移除动画」时均退化为 `snap()`）。
- **Web 端**：`server/internal/server/web/app/js/theme.js` 通过 `applyTheme` 下发 CSS 变量（`--primary`、`--on-surface-variant`、`--surface-high`、`--primary-container`、`--error-container` 等）；`app.css` 的 `:root` 仅保留薄荷浅色首屏回退。Web 专有变量包括 `--radius-s|m|l`（`12/20/28`）、`--shadow-sm|md`、`--ease: cubic-bezier(.2,.8,.2,1)`、`--nav-h: 68px` 与课程色 `--c0..c7-bg|fg`。
- **桌面小组件**：RemoteViews + XML 布局（`app/src/main/res/layout/widget_*.xml`）不吃 Compose 主题，修改品牌主题色时必须同步更新 `app/src/main/res/values/colors.xml` 与 `values-night/colors.xml`。
- **同步约束**：新增或修改 Design Token 须同时对齐 Android `Theme.kt` ↔ Web `theme.js` ↔ 小组件 `colors.xml`；薄荷预设固定值两端保持一致，其它预设按种子推导，不单独手调。

### 组件库索引（`ui/components/`）

无 Storybook，预览靠 Robolectric 截图（`./gradlew :app:testDebugUnitTest` → `app/build/shots/`）。常用尺寸常量统一定义在 `Kit.kt` 顶部：`NavIslandHeight`（`68.dp`）、`NavIslandPadding`（`12.dp`）、`NavClearance`、`NavListInset`。

| 符号 | 文件 | 职责与关键规格 |
|---|---|---|
| `MintScreen` / `MintCard` / `SectionLabel` / `Tag` | `Kit.kt` | 64dp 顶栏骨架；24dp 圆角纯白概览卡（`MintTheme.card`）；分组标题；全圆胶囊标签 |
| `MintCheck` / `SwitchRow` / `NavRow` | `Kit.kt` | 22dp 视觉 / 48dp 命中勾选框；设置开关行；导航行（≥ 48dp） |
| `FloatingNavBar` / `MintFab` / `EmptyState` / `ConflictCard` | `Kit.kt` | 68dp 浮动胶囊底栏（3dp 阴影）；primaryContainer 悬浮新建键；空状态；冲突提示卡 |
| `ConfirmDialog` / `DatePickerDlg` / `TimePickerDlg` / `TimeWheels` | `Kit.kt` | 28dp 圆角对话框；滚轮时间选择（行高 36dp × 5 行） |
| `mintPressable` / `mintPressScale` / `mintClickable` | `Kit.kt` | 统一按压缩放手势反馈（大行 `0.98f`、紧凑控件 `0.96f`，只在 draw 阶段读动画值） |
| `AgendaRow` / `TaskRow` / `EventDetailSheet` / `ParcelCodeHeadline` / `ParcelCodeCopyButton` | `Rows.kt` | 16dp 植物色日程块（新建统一收口至 `Task` 时间块）；无框宣纸待办行；历史 `Event` 详情抽屉（支持「转为待办编辑」与删除）；取件码主标题与行尾独立复制按钮 |
| `TonalField` / `FieldText` / `SegmentedPill` / `ChipChoice` / `ValueRow` / `FormSection` | `FormKit.kt` | 12dp 微底托输入框；无框输入；2–4 项胶囊分段器；流式多选芯片；属性行；表单分组 |
| `MonthPager` / `DialPickers` / `AttachmentEditor` / `ChangeList` | 同名文件 | 月视图翻页、拨盘选择器、附件编辑器、变更集预览列表 |

### 资产、图标与多端约束

- **无位图资产**：`app/src/main/res/drawable/` 仅包含启动/通知/小组件图标与 RemoteViews 圆角底 XML。需要新插图时优先 24×24 网格 vector drawable（`tint` / `currentColor`），许可证登记至 `docs/DEPENDENCIES.md`。
- **图标系统**：
  - Android：使用 `material-icons-core` 与 [GokeIcons.kt](app/src/main/java/app/goke/ui/icons/GokeIcons.kt)（按需抽取 Material Icons 矢量路径，避免引入 84 MB 的 `material-icons-extended`），默认 **Rounded** 变体；方向性图标（`ArrowBack`、`KeyboardArrowRight/Left`、`Undo`）必须使用 `AutoMirrored`。尺寸阶梯：**24dp** 导航/工具栏 · **20dp** 行内 · **16dp** 标签与冲突卡。
  - Web：`server/internal/server/web/app/js/dom.js` 内 `ICONS` 映射（24×24 viewBox、stroke 1.8、round cap/join），经 `icon(name, size)` 渲染；新增图标先查 Material Icons 与 Web `ICONS`。
- **Web 样式与 CSP**：无 CDN、无外链字体/图片；CSP 为 `script-src 'self'; style-src 'self'`，禁止内联样式与字符串拼 HTML。
- **响应式**：Android 只做手机（基准 `w411dp-h891dp`，状态栏 `24dp`、底栏净空 `NavClearance`）；Web 手机优先，CSS 断点为 `700px` / `900px` / `1100px`（`app.css`）。

### 改设计前的检查清单

1. 确认目标屏所属文件（见 `docs/architecture.md`）与可复用组件（见上表），优先复用再新建。
2. 检查颜色、圆角、字阶是否已在 Token 中定义；新增需按「Token 真源与跨端同步」改齐多端，禁止硬编码。
3. 核对状态栏避让与底栏净空（`NavClearance` / `NavListInset`）、左轴对齐与无框规则。
4. 核对无障碍：触控 ≥ 48dp、正文对比 ≥ 4.5:1、状态不只靠颜色、图标具备 `contentDescription` / `aria-label`。
5. 修改后运行 `./gradlew :app:testDebugUnitTest`，在 `app/build/shots/` 肉眼检查薄荷主题深浅双模式截图。
