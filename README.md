# 薄荷日程 MintCal

开源、本地优先的 Android 日程软件：**课程表 + 日历 + 待办** 三合一，支持「高级修改」（一句话 / 代码批量修改日程，先 Dry Run 预览再应用）。

> 状态：M1–M5 已实现（本地课表/日历/待办/主题、导入导出分享、高级修改、AI 生成代码、提醒/小组件/快递识别）；M6 后端、M7 Web 未开始。仅在 Robolectric 渲染测试中验证过 UI，尚未在真机上验证。设计文档在项目的 Notion 页面中维护，Spec 见 `docs/specs`。

## 特性（规划）

- 课程表 / 日历 / 今日 / 待办 多视图，清新可爱 + Material 3 Expressive，默认动态取色，主题可选
- 手动编辑 + 导入（Excel、ICS（含 WakeUp 课程表导出），教务系统后续）
- 高级修改：自然语言（自带 OpenAI 兼容 API）或 JavaScript 代码（QuickJS 沙箱），Dry Run 预览变更后再应用，可撤销
- 完全本地可用，不依赖后端；后端（Ktor）与同步、在线分享后续提供
- 快递提醒（可选，通知使用权识别取件码）

## 技术栈

Kotlin · Jetpack Compose · Material 3 Expressive · 手写 SQLite（ADR-0002） · QuickJS (quickjs-kt)。核心逻辑放在不依赖 Android 的纯 Kotlin 模块，以便后端复用。最低 Android 8.0（API 26），Android 12+ 启用动态取色。

## 构建与测试

```
./gradlew :core:test :script:test        # 纯 JVM 单测
./gradlew :app:testDebugUnitTest         # Robolectric 渲染冒烟测试（截图输出到 app/build/shots）
./gradlew :app:assembleDebug             # 生成 debug APK
python3 scripts/check_strings.py --unused  # 字符串资源检查
```

`scripts/xbuild.sh <任务>` 用于在远程构建机后台运行 Gradle 并写日志。AI 功能需在「设置 → AI」自行填写 API Key，Key 仅保存在本机。

## 许可证

[AGPL-3.0](LICENSE)
