# 薄荷日程 MintCal

开源、本地优先的 Android 日程软件：**课程表 + 日历 + 待办** 三合一，支持「高级修改」（一句话 / 代码批量修改日程，先 Dry Run 预览再应用）。

> 状态：M1–M5 已实现（本地课表/日历/待办/主题、导入导出分享、高级修改、AI 生成代码、提醒/小组件/快递识别）；自建同步服务（Go）与「账号与同步」已实现（S010）；Web 端（Go 服务端内嵌，手机 + PC 响应式，S011）已实现；教务系统同步（广工 jxfw：整学期课表 / 考试 / 每周日晚增量调课，S012）已实现待真机验证；待办 UX 改进（P1 紧凑列表 + 底部抽屉 + 快速添加高亮；P1.5 待办小组件 / 通知按钮 / 深链接 / 分享 / 快捷方式 / 磁贴；P2/P3 单一时间线 + 长按拖动改期 + 多选 + 逾期整理 + 搜索 + 批量/语音/剪贴板添加 + 课表小组件 + 提醒可靠性自检 + 空档推荐）已实现待真机验证，需注意与待调整项见 `docs/todo-ux-notes.md`；在线分享未开始。仅在 Robolectric 渲染测试中验证过 UI，尚未在真机上验证。设计文档在项目的 Notion 页面中维护，Spec 见 `docs/specs`。

## 特性（规划）

- 待办：一行快速添加（中文日期/优先级/清单/`/课程`，多行批量、粘贴、语音）、单一时间线（可折叠分区、长按拖动改期、多选批量、逾期整理、搜索）、子任务、附件（图片/文件/剪贴板，本地文件）、右滑完成左滑改期、「不做了」
- 待办画在**课表**上：按「排入日历」时间或截止时间落格，与课程块同构、颜色跟所属清单，点击进详情
- 课程表 / 日历 / 今日 / 待办 多视图，清新可爱 + Material 3 Expressive，默认动态取色，主题可选
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

## 自建同步服务

见 [server/README.md](server/README.md)：Go + SQLite，单二进制 / Docker，管理员后台创建用户，没有开放注册和邮箱。App 里「设置 → 账号与同步」填写服务器地址登录。端到端测试：`bash scripts/sync-e2e.sh`。

## 许可证

[AGPL-3.0](LICENSE)
