# S013 待办 UX（时间线 / 快速添加 / 显式确认 / 课表联动）

## 背景与目标

把待办从传统表单改为「录入零决策、按时间分区、拖动改期、课表联动」。视觉与无框行规范详见 [DESIGN.md](../../DESIGN.md)。

## 核心设计

- **单一时间线**（`TaskLogic.timeline`、`TodoScreen.kt`）：
  - 分区：逾期 / 今天 / 明天 / 本周 / 之后 / 收集箱 / 已完成，支持折叠（默认折叠 `LATER,DONE`），已完成区最多展示 100 条。
  - 清单与优先级顶部筛选条；搜索支持 `#清单 @标签 !高` 与关键词（`TodoSearch`）；支持右上角进入多选批量操作（改期 / 清单 / 优先级 / 完成 / 不做了 / 删除），批量操作合并为单条可撤销 `ChangeSet`。
  - 行手势：右滑完成、左滑改期（可在设置中 `swapSwipe` 对调）；长按拖动跨区改期，拖到行右侧可设为一层子任务（自身已有子任务时不嵌套）。
  - 「不做了」（`Task.dropped = true`）：仍记 `done = true` 但不触发重复任务的下一次实例；Web 端与 MCP 同步识别该状态。
- **快速添加与显式确认**（`QuickAddSheet`、`TodoEditScreen`）：
  - 自然语言行内实时高亮日期 / 时间 / 优先级 / 清单 / 标签 / `/课程`（点按高亮词可消解），支持「🎓 下节课前」、📋 粘贴、🎤 语音、多行缩进批量预览与外部分享文字接入（`Nav.pendingAddText`）。
  - **新建必须显式确认**：`QuickAddSheet` 点工具条提交按钮或键盘 `Done` 才落库，点遮罩空白或系统返回丢弃草稿；`TodoEditScreen` 新建模式需点顶栏「确定」保存，返回或确认放弃时丢弃草稿；编辑已有待办时有未保存改动返回前提示确认。
  - 重复规则与开始/截止、提醒、清单、优先级均可在编辑页或快速添加时间面板直接配置。
- **课表与今日联动**：
  - 「今日」页保持极简留白（裁剪 `FreeGaps` 独立空档推荐卡），仅在顶部今日摘要卡内升级接入 `NextUps.find`（优先展示今日下一条未结束课程 / 日程 / 时间块，无今日安排时回退未来 7 天下节课，并附带关联该课的未完成待办）。
  - 逾期整理卡在逾期 ≥ 2 条时出现（`MorningSheet`）。
  - 课程编辑页反向列出关联该课的待办；上课前提醒附带「课前要交」待办。

## 关键默认值与约束

| 项 | 默认 / 约束 | 位置 |
|---|---|---|
| 默认折叠分区 | 之后、已完成（`LATER,DONE`） | `TodoScreen.kt` |
| 已完成展示上限 | 100 条 | `TodoScreen.kt` |
| 逾期整理触发条件 | 逾期 ≥ 2 条 | `TodoScreen.kt` |
| 拖到「本周 / 之后」落点 | 本周日（不足 2 天不可落）/ 今天 + 7 天 | `TaskLogic.dropDate` |
| 子任务层级 | 仅支持一层 | `Edits.makeSubtask` / `TodoScreen.kt` |
| 每日摘要默认时刻 | 07:30（开关默认关，可在设置调整） | `Settings.kt` / `ReminderScheduler.kt` |

## 验收与测试

- `:core`：`TodoV2Test`、`TodoSearchTest`、`QuickAddSlashDateTest`、`EditsTest`。
- `:app`：`ScreensSmokeTest`（含 `quickAddDraftTimeStaysTodo` 等交互与渲染冒烟）。
- 真机专项检查（手势边缘冲突、通知与小组件）见 [testing.md](../testing.md#真机专项验证清单)。
