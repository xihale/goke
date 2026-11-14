# S009 提醒、小组件、快递入日程与系统日历

## 背景与目标

打通 App 与 Android 系统能力：按时提醒、桌面直览与快捷勾选、快递通知自动变待办、上课自动勿扰，以及将课程与日程双向写入系统日历。

## 行为与能力

- **提醒与自检**（`notify/ReminderScheduler.kt`、`ui/screens/ReliabilityScreen.kt`）：
  - 数据或设置变更后防抖 0.8s 重排未来 7 天内最早 40 条提醒；优先精确闹钟，无权限时回退非精确闹钟；开机由 `BootReceiver` 恢复。
  - 上课前提醒自动附带「课前要交」关联待办。
  - 支持每日摘要通知（默认关闭，时间可配置）与通知栏快捷按钮（「完成」「1 小时后再提醒」）。
  - 提醒可靠性自检页自动识别厂商并展示对应权限/省电引导，支持发送 60 秒后测试提醒。
- **上课自动勿扰**（`notify/MuteReceiver.kt`）：开启 `Settings.autoMute` 且授予勿扰权限后，按课表时段自动切换勿扰并在下课恢复。
- **桌面小组件**（`widget/`，RemoteViews）：
  - `TodayWidgetProvider`（今日安排）、`ScheduleWidgetProvider`（今日课表，当前课高亮）、`TodoWidgetProvider`（待办清单，支持勾选与新建）、`ParcelWidgetProvider`（待取快递）、`NextWidgetProvider`（下一件事）。
  - 统一使用薄荷语义色与圆角 XML；不引入 Glance 依赖。
- **快递通知识别入日程**（`notify/ParcelListenerService.kt`、`notify/ParcelTasks.kt`、`core/parcel/ParcelWindow.kt`）：
  - 通知使用权仅在本地过滤快递取件文本，匹配引擎见 [S014](014-parcel-rule-engine.md)。
  - 识别出的取件待办自动排入可配置的每日顺路取件时段（默认 `14:00–22:00`，已过结束时刻则排至次日），驿站截止时间保留为 `due` 并参与提醒；课表视图中超过 3 小时的柔性取件窗折叠为列头胶囊徽标。
- **系统日历双向同步**（`calendar/SystemCalendarSync.kt`、`core/sync/CalendarBridge.kt`）：
  - 授权读写日历权限后，可关联系统已有日历账本或一键创建本地日历账本，供系统桌面插件与智能手表读取。
  - 监听快照与 `ContentObserver` 进行防抖对账（15 分钟周期任务同步兜底），避免回声循环，不污染用户撤销栈。

## 验收与测试

- `:core`：`ParcelRuleTest`、`ParcelWindowTest`、`CalendarBridgeTest`。
- `:app`：`WidgetsAndCalendarSyncTest`、`ScreensSmokeTest`。
- 真机专项验证见 [testing.md](../testing.md#真机专项验证清单)。
