# 依赖清单

新增依赖需在此登记：用途、许可证、为什么不用更轻的方案。版本以 `gradle/libs.versions.toml` 为准。

| 依赖 | 用于 | 许可证 | 说明 |
|---|---|---|---|
| Kotlin / kotlinx-serialization / kotlinx-coroutines | 全部模块 | Apache-2.0 | 语言与基础库 |
| AndroidX（core-ktx、lifecycle、activity-compose） | :app | Apache-2.0 | Android 基础 |
| Jetpack Compose（BOM、ui、material3、material-icons-extended） | :app | Apache-2.0 | UI；Material 3 Expressive 需 material3 alpha 版 |
| material-kolor | :app | MIT | 用种子色生成预设 / 自定义主题的 M3 配色（动态取色之外的主题选项） |
| quickjs-kt（io.github.dokar3） | :script、:app | MIT | 内嵌 QuickJS 引擎，运行「高级修改」脚本；支持超时、内存与栈限制，沙箱内无 IO |
| JUnit 5（jupiter） | :core、:script 测试 | EPL-2.0 | 纯 JVM 单元测试 |
| JUnit 4 + Robolectric + compose ui-test-junit4 | :app 测试 | EPL-1.0 / MIT / Apache-2.0 | 在 JVM 上渲染页面做冒烟测试（无设备时也能发现崩溃） |
| Spotless Gradle 插件 + ktlint 1.8.0 | 构建期工具（不进 APK） | Apache-2.0 / MIT | 格式化与静态检查：`spotlessCheck` / `spotlessApply`，规则见 `.editorconfig`，关闭了 if-else-wrapping 与 mixed-condition-operators 两条 |
| modernc.org/sqlite（Go，server/） | 同步服务端 | BSD-3-Clause | 纯 Go 的 SQLite 驱动，无需 cgo |

## 没有引入的依赖（及原因）

- **Room / KSP**：数据是「按记录存 JSON」的单表结构，手写 `SQLiteOpenHelper` 更少依赖、更少构建步骤，见 [ADR-0002](adr/0002-handwritten-sqlite.md)。
- **DataStore**：设置项很少，`SharedPreferences` + `StateFlow` 足够。
- **OkHttp / Retrofit**：AI 请求只有一个 POST，用 `HttpURLConnection` 即可。
- **图片 / 动画库**：课表图片用 `Canvas` 直接绘制。
- **前端框架 / 打包器 / npm 依赖（Web 端）**：Web 端是原生 ES 模块，`go:embed` 进服务端；测试只用 Node 内置的 `node:test`，见 [ADR-0006](adr/0006-web-client-vanilla-esm.md)。
