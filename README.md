# 薄荷日程 MintCal

开源、本地优先的 Android 日程软件：**课程表 + 日历 + 待办** 三合一，支持「高级修改」（一句话 / 代码批量修改日程，先 Dry Run 预览再应用）。

> 状态：M1–M5、自建同步（S010）、Web（S011）及教务同步（S012）已实现；在线分享未开始。今日、日历、待办主流程已在 Android 10 真机走查，提醒、小组件、教务等功能仍需专项验证。实际验证范围与待调整项见 `docs/todo-ux-notes.md`；设计文档在项目的 Notion 页面中维护，Spec 见 `docs/specs`。

## 特性（规划）

- 待办：一行快速添加（中文日期/优先级/清单/`/课程`，多行批量、粘贴、语音）、单一时间线（可折叠分区、长按拖动改期、多选批量、逾期整理、搜索）、子任务、附件（图片/文件/剪贴板，本地文件）、右滑完成左滑改期、「不做了」
- 待办画在**课表**上：按「排入日历」时间或截止时间落格，与课程块同构、颜色跟所属清单，点击进详情
- 在课表上直接调时间：长按时间块拖动=改开始时间，拖上下边=改时长（15 分钟一档）；空白处框选可横跨几天、纵跨几节，一次建多条，重复的时间块每周都画
- 时间块和课程 / 别的待办撞了，编辑页顶部直接列出来，点一下跳到那天的课表（先保存再看，不丢改动）
- 课程表 / 日历 / 今日 / 待办 多视图，清新可爱 + Material 3 Expressive，默认动态取色，主题可选
- 新建和修改共用编辑页：日期、提醒、重复、清单和优先级直接可改，快速添加的文字可带入「详细编辑」。标题里写下「明天 14:30 开会」，日期与时间就地高亮（点一下表示认错了）；有时间就是日程、只有日期就是待办。半小时网格适合点选 / 框选，开始与结束也能精确输入到分钟，时长沿用设置；日历左右滑动翻月翻周
- 日历日期下的短线表示日程数量，实心 / 空心圆点区分未完成 / 已完成待办；长按日期可原地预览、勾选或进入编辑
- 设置保留常用预设，也支持自定义时间块时长、课程提醒提前量、每日摘要时刻与备份保留份数；自定义主题可调色相 / 饱和度 / 明度或输入 HEX 颜色
- 手动编辑 + 导入（Excel、ICS（含 WakeUp 课程表导出），教务系统后续）
- 高级修改：自然语言（自带 OpenAI 兼容 API）或 JavaScript 代码（QuickJS 沙箱），Dry Run 预览变更后再应用，可撤销
- 完全本地可用，不依赖后端；可选自建同步服务（Go，见 `server/`）：管理员创建账号，登录后多设备同步，不登录就是本地模式
- 快递提醒（可选，通知使用权识别取件码）

## 技术栈

Kotlin · Jetpack Compose · Material 3 Expressive · 手写 SQLite（ADR-0002） · QuickJS (quickjs-kt)。核心逻辑放在不依赖 Android 的纯 Kotlin 模块，以便后端复用。最低 Android 8.0（API 26），Android 12+ 启用动态取色。

## 构建与测试

```
./gradlew :core:test :script:test        # 纯 JVM 单测
./gradlew :app:testDebugUnitTest         # Robolectric 渲染冒烟测试（截图输出到 app/build/shots）
./gradlew :app:assembleDebug             # 生成 debug APK
python3 scripts/check_strings.py --unused  # 字符串资源检查
./gradlew spotlessCheck                  # ktlint 格式检查；spotlessApply 自动格式化（规则见 .editorconfig）
```

`scripts/xbuild.sh <任务>` 用于在远程构建机后台运行 Gradle 并写日志。字体目前使用系统默认，圆润标题字体待定（见 `docs/adr/0004-defaults.md`）。AI 功能需在「设置 → AI」自行填写 API Key，Key 仅保存在本机。

今日、日历与待办的细节精修已在 Android 10 真机上走查，包含新增读回、日期导航、搜索 / 多选返回、浅深色与放大字体。具体改动、验证范围及现有 Lint 问题见 [待办 UX 记录](docs/todo-ux-notes.md#7-2026-10-25-真机细节精修)。

## 自建同步服务

见 [server/README.md](server/README.md)：Go + SQLite，单二进制 / Docker，管理员后台创建用户，没有开放注册和邮箱。App 里「设置 → 账号与同步」填写服务器地址登录。端到端测试：`bash scripts/sync-e2e.sh`。

## 许可证

[AGPL-3.0](LICENSE)
