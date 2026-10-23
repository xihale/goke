# S001 工程骨架与设计 Token

## 背景与目标
多模块工程（:core 纯 Kotlin、:script QuickJS、:app Compose）；MintCalTheme（M3 Expressive 配色、动态取色、深浅色、自选主题色）、形状/间距 Token、MintTheme.card。

## 验收
App 启动展示底部导航（今日/课表/日历/待办/我的）；深色模式文字与卡片对比正常。

## 测试
Robolectric ScreensSmokeTest 渲染全部页面（含深色）无异常。
