---
name: 薄荷日程 MintCal
description: 清新、无框、零说教的课程表 · 日历 · 待办 · 取件一体式效率日程工具
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
typography:
  headline-medium:
    fontFamily: "Noto Sans SC, sans-serif"
    fontSize: "26px"
    fontWeight: 600
    lineHeight: 1.23
    letterSpacing: "0em"
  title-large:
    fontFamily: "Noto Sans SC, sans-serif"
    fontSize: "22px"
    fontWeight: 600
    lineHeight: 1.27
    letterSpacing: "0em"
  title-medium:
    fontFamily: "Noto Sans SC, sans-serif"
    fontSize: "16px"
    fontWeight: 500
    lineHeight: 1.5
    letterSpacing: "0em"
  body-large:
    fontFamily: "Noto Sans SC, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "0em"
  body-medium:
    fontFamily: "Noto Sans SC, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.43
    letterSpacing: "0em"
  label-large:
    fontFamily: "Noto Sans SC, sans-serif"
    fontSize: "14px"
    fontWeight: 500
    lineHeight: 1.43
    letterSpacing: "0em"
  label-medium:
    fontFamily: "Noto Sans SC, sans-serif"
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
  flat-input:
    backgroundColor: "{colors.background}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-large}"
    padding: "6px 0px"
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

# MintCal 设计系统（DESIGN.md）

## Overview

**Creative North Star: "薄荷便签（Clean Mint Sheet）—— 无框呼吸、一行一事、简约可爱"**

MintCal 是一款面向学生与高效率人群的「课程表 + 日历 + 待办 + 快递取件」一体式工具。整个界面的视觉基准直接锚定在待办详情编辑页（`TodoEditScreen`）：文字与控件直接落在带有淡淡植物气息的薄荷宣纸底上，没有层层包裹的厚重白卡，没有描边输入框，也没有唠叨的使用说明。

「可爱」不靠堆砌卡通贴纸或高饱和渐变，而来自**圆润温润的几何比例与低饱和植物色阶**：全圆胶囊浮动底栏、24dp–28dp 的柔和弹层、12dp 课表马卡龙色块、取件码票据胶囊、以及按下时 0.96 倍的轻弹触感反馈。每一屏只呈现用户当下决策所需的最小信息集，扫一眼即懂，点一下即成。

**Key Characteristics:**

- **无框直落（Borderless Sheet）**：标题、备注、搜索与表单项直接铺在页面底色上，用字重、字色与留白建立层级，拒绝描边输入框与卡中卡。
- **一行合成（Single-Line Synthesis）**：日期、时间、重复、提醒等关联属性优先合成为单行可读摘要，展开才见细节选择器。
- **零说教文案（Zero Tutorial Copy）**：界面不写教程长句、不写空状态鸡汤、不在自明的操作行下挂冗余解释副标题。
- **温润轻弹（Tactile Softness）**：取消点击水波纹底色闪烁，交互反馈交给 0.96 缩放弹簧（阻尼 0.9 / 刚度 700）与清脆的触觉震动。
- **桌面同源（Home Screen Continuity）**：桌面小组件（今日日程、课表、待办、待取快递、下一件事）与主应用共享同一套薄荷语义色、胶囊徽标与一键勾选交互。

## Colors

色彩围绕「薄荷叶与温润纸面」展开，支持 Android 12+ 动态取色、6 套低饱和种子预设与深浅双模式。所有正文与弱化副文均经过对比度校核，确保在浅薄荷底与纯白容器上都清晰耐看。

- **品牌主色（Primary Mint）**：森林薄荷绿用于主操作、选中态文字、当前时刻高亮与进度环；浅薄荷容器（Primary Container）专供 FAB、取件码票据胶囊与当前正在进行的课表行高亮，柔和不刺眼。
- **纸面与容器（Neutral Sheet & Surface）**：浅色模式以极淡薄荷灰绿为页面底，纯白留给少量需要独立承载的浮岛与弹层；深色模式采用深松石夜色底配微提亮容器，避免纯黑眩光。弱化副文（On Surface Variant）在页面底上保持 4.91:1 对比度，在白底上保持 5.31:1。
- **课程与清单色板（8 Botanical Pastels）**：天青、薄荷、鸢尾、麦穗、蜜桃、鼠尾草、蔷薇、雾蓝 8 组配对底色与同色系深前景字色，深浅模式各自调校，课程块与清单标识同源取色。
- **状态与优先级语义色（Semantic Signals）**：低优先级用安静灰绿、中优先级用暖琥珀金、高优先级与逾期/删除共用克制的莓果红；完成态一律退灰并加删除线，把鲜明色彩让位给未完成事项。

**The Calm-Surface Rule.** 主色只用于「当前选中、正在进行、核心召唤按钮」，绝不大面积铺满普通列表卡片或顶栏背景。

**The Gray-Out Completion Rule.** 已完成的待办、已取走的快递、已结束的课次必须整体退为弱化灰绿，勾选框不再保留彩色填充，防止已完成项抢夺注意力。

## Typography

全局采用 **Noto Sans SC** 无衬线字族（计时器数字使用等宽字体保持跳秒稳定），**所有字阶的字距（letterSpacing）统一归零（0sp）**，消除中文排版松散感。

- **页面与输入主标题（Headline Medium 26sp / Title Large 22sp，SemiBold）**：用于编辑页无框大标题、专注倒计时读数与顶栏单行标题，字重撑起视觉锚点。
- **分组与行内重点（Title Medium 16sp Medium / Body Large 16sp Regular）**：用于待办标题、表单当前值、设置主行与取件码主读数，单行截断干净利落。
- **次要元信息与胶囊标签（Body Medium 14sp / Label Large 14sp / Label Medium 12sp）**：用于时间列、教室地点、分区折叠标题与胶囊 Tag。

**The One-Number Rule.** 同一屏内同一组统计（如今日待办进度、分区未完成数、日历事项数）只用一种形式表达一次，绝不在标题、副标题、圆环中心重复报数。

**The No-Colon-Stack Rule.** 属性展示优先使用「左弱化标签 + 右对齐值」（`ValueRow`）或「`08:30 · 教3-201`」中点连接，禁止连续堆砌多行「教室：xxx / 教师：xxx / 周次：xxx」全角冒号文本。

## Layout

页面遵循手机单手友好的流式单栏栅格（最大内容宽 680dp 居中），以 **4dp 基准网格**（4 / 8 / 12 / 16 / 24dp）控制呼吸节奏。

- **顶栏净空（64dp Top Bar）**：横向内边距 8dp；编辑页顶栏只放「返回」与右侧动作（勾选 / 更多 / 确定），把标题位置直接让给正文第一行输入框。
- **浮动底栏避让（Floating Island Clearance）**：胶囊底栏高 68dp、上下外边距 12dp（总净空 92dp）；长列表底部统一留白 `NavListInset`（176dp），保证末行内容可完整滚出浮岛与右下角 FAB 之上。
- **扁平分区（Flat Sections）**：设置页、同步页、规则页使用 `FormSection`（顶部 16dp 留白 + 小字号 `SectionLabel`）直接组织行项，组内紧凑（0–4dp），组间留白分明（16–24dp）。
- **桌面小组件栅格（Widget Grid）**：小组件统一 14dp–16dp 内边距，表头浓缩为「精简标题/周次胶囊 + 右上角单图标动作」，列表区零多余说明，支持自由缩放（3×2、4×2、4×3、4×1）。

**The No-Nested-Card Rule.** 页面内禁止卡片套卡片；普通列表与表单行直接落在页面背景上，仅在需要跨模块聚合焦点（如今日进度摘要、时间冲突提醒卡）时使用单层圆角容器。

**The Self-Evident Row Rule.** 导航行（`NavRow`）标题已自明时不写解释性副标题；把动态状态浓缩为右侧 2–6 字的 `trailing` 短状态（如「薄荷」「已开启」「未登录」）。

## Elevation & Depth

MintCal 追求贴近纸面的轻盈感，几乎不使用传统投影。

- **零水波压感（Scale-Only Press）**：全局移除点击底色闪烁，仅保留键盘/无障碍 2dp 主题色焦点环；所有可点元素按下时缩放至 0.96 并触发轻触觉反馈。
- **单处轻浮岛（3dp Island Shadow）**：全应用仅底部胶囊导航栏（`FloatingNavBar`）与悬浮按钮携带极轻柔和阴影，使其轻浮于滚动内容之上；其余容器一律靠色调差（Tonal Surface）而非阴影分层。
- **极细分隔线（0.5dp Hairline）**：仅在顶底固定工具栏与滚动区交界处使用 45% 透明度的 `outlineVariant` 0.5dp 细线，列表行间优先靠纵向留白区分。

**The Quiet-Press Rule.** 触摸屏上的按下态只做轻微几何回缩（0.96x）与触感回执，不叠加灰色或主色半透明遮罩。

## Shapes

圆角阶梯传递亲和、可爱的触感，且外层容器与内层色块保持同心曲率协调：

- **微圆角勾选框（4dp Extra Small）**：`MintCheck`（22dp 视觉框 / 48dp 触控区）采用微圆角方形，与圆形胶囊徽标形成清晰语义区分（方形可勾选、胶囊表属性）。
- **课表块与行内圆角（12dp Small / 16dp Medium）**：课表课程块、时间块、冲突提示卡与小组件正在进行的高亮行采用 12dp–16dp 圆角，紧凑不呆板。
- **卡片与大弹窗（24dp Large / 28dp Extra Large）**：底部抽屉（BottomSheet）、确认对话框与桌面小组件外框采用 24dp–28dp 大圆角，与现代 Android 系统桌面圆角自然融合。
- **全圆胶囊（999dp Pill）**：底部导航岛、内部滑动选中块、清单/优先级/取件码 `Tag` 统一使用全圆胶囊。

## Components

- **无框输入（`SmartTitle` / `FlatField` / `FieldText`）**：无边框、无底色填充；编辑页主标题支持日期时间词实时高亮（点击高亮词即可取消识别），搜索栏同样采用无描边柔和样式。
- **柔和勾选框（`MintCheck`）**：22dp 微圆角描边方框，48dp 无障碍触控热区；支持按清单色或优先级加粗染色，勾选瞬间带触觉反馈并平滑退灰。
- **取件码票据胶囊（Parcel Code Tag & Ticket）**：针对识别出的快递待办，在列表行、今日页与「待取快递」桌面小组件中以醒目胶囊展示取件码（如 `4-2-1086`）与驿站简称，点击胶囊可复制取件码，点击勾选即刻完成取件。
- **单选文字片（`ChipChoice`）与胶囊标签（`Tag`）**：表单内单选直接通过主色与 Medium 字重表达选中，不堆叠厚重底框；分类与状态标记使用紧凑全圆 `Tag`。
- **浮动胶囊底栏（`FloatingNavBar`）**：5 个常驻核心入口（今日 / 周历 / 日历 / 待办 / 专注），选中滑块在 Canvas 层平滑位移，零多余重组。
- **桌面小组件家族（RemoteViews Suite）**：
  - **今日日程小组件**：日期 + 周次胶囊 + 快捷新建按钮，可滚动串联课程、日程块、待办与快递，支持行内直接完成。
  - **今日课表小组件**：精简日期与周次徽标，正在进行的课程以浅薄荷底高亮，无课时留白清爽。
  - **待办清单小组件**：微圆角勾选框 + 逾期莓果红时间提示 + 取件码内联徽章，勾选毫秒级即时回执。
  - **待取快递小组件**：票据式大号取件码 + 驿站位置 + 截止时刻 + 一键已取，取件站前抬手即看。
  - **下一件事 · 专注小组件（4×1）**：单行紧凑展示眼下最重要的一件事，右侧直达番茄专注与快速添加。

## Do's and Don'ts

**Do:**

- 保持编辑页式的「无框直落」气质：新增设置页、同步页或抽屉时，优先用 `FormSection` + `ValueRow` / `SwitchRow` / `FieldText` 组合。
- 空状态只保留「柔和圆底小图标 + 一句 6 字内短标题 + 可选单个召唤按钮」，干净温和。
- 快递、课表、日程在同一时间轴出现时，用 3dp 圆角语义色条与精简胶囊区分类型，不写冗余前缀词。
- 所有图标按钮与自绘点击区必须提供准确的 `contentDescription` 并守住 48dp 最小触控尺寸。
- 新增或修改任何颜色 Token 时，同步维护 Android `Theme.kt`、小组件 `colors.xml`（含 `values-night`）与 Web `theme.js`。

**Don't:**

- **禁止文字堆砌与说教**：不要在页面顶部、底部或空状态写两三句话的功能介绍、操作指南（如「长按日期预览」「逐条决定改到今天…」）或隐私免责声明长段。
- **禁止描边输入框与嵌套卡片**：不要混用 `OutlinedTextField`，不要在 `MintCard` 内部再套带背景色的子卡片。
- **禁止组件内硬编码色值**：业务屏幕内严禁出现 `Color(0xFF...)` 或硬编码中文文案。
- **禁止双向同步污染撤销链**：系统日历等外部增量同步后台写回时不得制造多余的用户历史记录或破坏本地 `canUndo` 连续撤销体验。
