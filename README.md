# 薄荷日程 MintCal

开源、本地优先的 Android 日程软件：**课程表 + 日历 + 待办** 三合一，支持「高级修改」（一句话 / 代码批量修改日程，先 Dry Run 预览再应用）。

> 状态：M0 设计完成，M1 开发中。设计文档与界面原型在项目的 Notion 页面中维护。

## 特性（规划）

- 课程表 / 日历 / 今日 / 待办 多视图，清新可爱 + Material 3 Expressive，默认动态取色，主题可选
- 手动编辑 + 导入（Excel、ICS（含 WakeUp 课程表导出），教务系统后续）
- 高级修改：自然语言（自带 OpenAI 兼容 API）或 JavaScript 代码（QuickJS 沙箱），Dry Run 预览变更后再应用，可撤销
- 完全本地可用，不依赖后端；后端（Ktor）与同步、在线分享后续提供
- 快递提醒（可选，识别取件通知，规划中）

## 技术栈

Kotlin · Jetpack Compose · Material 3 Expressive · Room (SQLite) · QuickJS (quickjs-kt)。核心逻辑放在不依赖 Android 的纯 Kotlin 模块，以便后端复用。最低 Android 8.0（API 26），Android 12+ 启用动态取色。

## 许可证

[AGPL-3.0](LICENSE)
