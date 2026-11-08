# S016 新建确认键 + 番茄专注计时

## 背景与目标
两件事：1) 新建待办不再「返回即保存」——只有点「确定」才落库，否则丢弃（避免误触返回多出半成品）；2) 番茄钟：正/倒计时、可挂某条待办，事后能看到做这条待办时在哪些时段专注了、一共专注多久。

## 新建确认键（已实现）
- `TodoEditScreen`（新建/修改同一页）：
  - 修改：返回即保存（有改动就落盘），行为不变。
  - 新建：顶栏右侧「确定」（标题为空置灰）落库；返回 / 手势 / 冲突卡「去查看」一律丢弃草稿，不产生记录。
- `QuickAddSheet`（三个 Tab 共用）：
  - 工具条右端新增实心圆形 ✓（`parsed == null` 时置灰）；点 ✓ 或键盘完成键（IME Done）= 落库。
  - 点遮罩空白 / 系统返回（`BackHandler(enabled = visible)`）= 丢弃草稿并收起，清空文本、备注与面板状态。
  - 多行粘贴的批量预览对话框本来就是显式确认，不变。
- 行内子任务添加（编辑已有待办时）仍是「添加」按钮显式提交，不变。

## 专注计时（已实现）
- 数据：新记录类型 `FocusSession`（`_t = "focusSession"`，`kind = "FocusSession"`）：`taskId?`（null = 自由专注）、`startedAt/endedAt`（UnixMillis 墙上时钟）、`pausedMillis`（暂停不计入时长）、`plannedMinutes?`（倒计时目标）、`completed`（倒计时是否跑满）。派生属性 `focusMillis = endedAt - startedAt - pausedMillis`。
- 快照与同步：`Snapshot` 末尾追加 `focusSessions`（带默认值，历史调用点按位置传参不受影响）；`allRecords/put/purge/live/hasDeleted/isPristine` 全部接入，因此 oplog 撤销、回收站、自动备份、记录级同步、服务器存储（`kind TEXT`）与 MCP（正例清单外直接忽略）零改动直通。Web 端 `liveView` 对未知 `_t` 跳过，不渲染不报错。
- 计时运行时（`app/mintcal/focus/`）：
  - `FocusRun` 只存 SharedPreferences（`focus` 库），不进记录表；计时全按墙上时钟推算，进程被杀后按时间差恢复（无需前台服务）。
  - `FocusController`（`MintCalApp.focus`）：start/pause/resume/stop；倒计时用精确闹钟（`SCHEDULE_EXACT_ALARM` 已有），到点由 `FocusReceiver` 补记 `completed=true` 的会话并通知（前台时不弹通知，由页面 toast；暂停会取消闹钟，恢复重排）。
  - 手动结束不足 1 分钟不落记录（toast 提示），防误触污染统计。
  - 倒计时到点的「专注完成」提示走 `FocusController.events`（SharedFlow），与手动结束的提示互不竞态。
- UI：
  - 专注是第五个 Tab（`Tab.FOCUS`，岛右侧计时器图标）：与四个 Tab 同一套常驻挂载 + 瞬切机制，底栏色块滑过去，不压栈。岛内不再有动作入口（`FloatingNavBar(tab, onTab)`，全条目均为 Tab）。
  - `Route.Focus(taskId)` 仍保留为压栈页：仅任务编辑页「开始专注 / 查看全部」使用（带 taskId、带返回键）；Tab 层挂载的是 `FocusScreen(taskId = null, inTab = true)`（无返回键，底部让位给岛）。
  - `FocusScreen`：模式（正/倒计时，`ChipChoice`）、时长（`MinutesDialSheet`，默认 25）；进行中大表读数 250ms 刷新（数值按墙上时钟推算，走 LocalTabVisible 门控）；下方「专注记录」按天分组（今天/昨天/M月d日 + 当日合计），行 = 任务名 · 起止 · 时长（跑满的倒计时时长为主题色）。
  - 任务编辑页新增「专注」区：累计时长与次数 + 最近 8 段起止 + 开始专注 / 查看全部。
- 统计纯函数在 `core/focus/FocusLogic.kt`：`sorted/ofTask/totalMillis/byDay/byHour`，时区由调用方传入。

## 课程编辑 v2（周次画笔 + 真课表，2026-11-05 按用户反馈重做）
- 用户否决了「时间模式列表 + 迷你周历行」的设计，改为：**先选周，再在真课表上直接画**。
- 画笔：`WeekPicker` 周次多选 chips（全部周/单周/双周/清空预设），画笔周 = 编辑作用域；画笔只选一周时网格星期头显示真实日期。
- `EditorGrid`：与周历同一套几何（34dp 时间列、60dp 节次行、4/8 节组间 14dp、12dp 圆角色块、同配色 `MintTheme.colors.bg/fg`）；别人的课 α0.4 打底，自己的时间实心可点，同天互压分道。
- 手势（`pointerInput` 挂容器，同周历）：按住空白拖动 = 给画笔周画 (dow, sp..ep)；按住已有色块拖动 = 整段时间跟随手指平移，只搬画笔周；点色块 = `BlockSheet`（周次/画笔周次、提醒预设、从选中周移除、删除此时间）。
- 周次代数抽成纯函数 `PatternOps`（draw/move/removeWeeks/remove），`CourseGridOpsTest` 9 例覆盖并入、拆分、整搬、清空保护。
- 旧的 PatternLegend/WeekGridView/PatternSheet/WeekSheet 与 course_pattern_* 等死字符串已删；停课/调课的编辑退回周历的 CourseSheet（编辑器只管排课规则本身，挂起停课不再有）；周次挪走时悬空调课/停课记录仍在保存时一并清掉。

## 验收
- 新建待办/快速添加：返回不留记录，「确定」/键盘完成才入库（`ScreensSmokeTest.quickAddDraftTimeStaysTodo` 覆盖返回丢弃 + 确定落库两段）。
- 专注会话走变更集：可撤销（历史里「专注 · 时长」）、多端同步（`SyncTest` wire 往返 + `isPristine` 不误判）。
- 真机已验：倒计时暂停/继续、正计时跨进程恢复、不足 1 分钟不记录、自由/挂任务两类会话落库并在编辑器与专注页正确聚合。

## 未做
前台服务常驻通知（当前按墙上时钟 + 闹钟已覆盖进程死亡）、休息番茄建议、专注时段直方图渲染（`FocusLogic.byHour` 数据已备）、Web 端展示、小组件。
