# S013 待办 UX 改版（时间线 / 抽屉 / 快速添加 / 小组件）

## 背景与目标
把待办从「表单式」改为「录入零决策、原地修改、按时间分区、拖动即改期」，并和课表联动（下节课前、空档、课前提醒）。设计讨论见 Notion「待办 UX 改进方案（讨论稿）」；偏离、可调默认值、真机必测清单见 [../todo-ux-notes.md](../todo-ux-notes.md)。

## 范围（已实现，待真机验证）
- 核心（`:core`，有单测）：`Task.dropped`（「不做了」，仍记 `done=true`）；`TaskLogic.timeline/Bucket/dropDate`；`Edits.reschedule/dropTasks/makeSubtask/updateTasks`（批量合并为一个 ChangeSet，一次撤销）；`QuickAdd.parse` 返回片段区间、支持 `/课程`、`parseLines`（多行 + 缩进子任务）；`FreeGaps`、`NextUps`、`TodoSearch`（`#清单 @标签 !高`）。
- 待办页：时间线分区（逾期/今天/明天/本周/之后/收集箱/已完成，可折叠）、下一件事卡、逾期整理（≥2 条出现）、搜索、清单下拉、多选批量、左滑改期、右滑完成、行内子任务、长按拖动跨区改期 / 右半区变子任务。
- 录入：输入框高亮、快捷条、🎓 下节课前、📋 粘贴、🎤 语音、多行批量预览、分享文字入口（`Nav.pendingAddText`）。
- 修改：底部抽屉 `TaskSheet`，关闭时自动保存，一次编辑一条历史；「展开全部」进整页。
- 系统入口：待办/课表小组件（RemoteViews）、通知「完成/1 小时后再提醒」、深链接 `mintcal://task/{id}|add?text=|today|todo`、图标快捷方式、快捷设置磁贴、`PROCESS_TEXT`、提醒可靠性自检页（含测试提醒）、每日摘要（默认关，7:30）。
- 联动：今日页空档推荐；课程编辑页列出该课待办；上课前提醒带「课前要交」。

## 验收
- 时间线分区与分组由 `TaskLogic.timeline` 决定；拖到分区 = 设置对应截止日（收集箱 = 清除日期）。
- 批量操作、拖动、「不做了」都能一次撤销。
- 搜索语法与快速添加语法一致。
- `ScreensSmokeTest` 渲染待办页、抽屉、改期/整理抽屉、可靠性页不崩。

## 未做
同区手动排序、滑动动作自定义、系统日历写入、作业通知识别、上课静音、常驻快速添加通知、图片分享变附件、桌面角标、自动备份、Web 端跟进（Web 暂不识别 `dropped`）、周课表/倒计时/快速添加小组件。

## 测试
:core：`TodoV2Test`、`TodoSearchTest`、QuickAdd/FreeGaps/NextUps 单测；`ScreensSmokeTest`（Robolectric）。手势、小组件刷新、通知按钮、MIUI 后台限制需真机，见 todo-ux-notes。
