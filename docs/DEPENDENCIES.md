# 依赖清单

新增依赖需在此登记：用途、许可证、为什么不用更轻的方案。版本以 [gradle/libs.versions.toml](../gradle/libs.versions.toml) 与 [server/go.mod](../server/go.mod) 为准。

| 依赖 | 用于 | 许可证 | 说明 |
|---|---|---|---|
| Kotlin / `kotlinx-serialization` / `kotlinx-coroutines` | 全部 Kotlin 模块 | Apache-2.0 | 语言、JSON 序列化与协程 |
| AndroidX（`core-ktx`、`lifecycle`、`activity-compose`） | `:app` | Apache-2.0 | Android 基础；`activity-compose` 1.13+ 提供 `PredictiveBackHandler` |
| Jetpack Compose（BOM、`ui`、`material3`、`material-icons-extended`） | `:app` | Apache-2.0 | UI 与官方动画（`material3` 1.5.0-beta02 起含 Expressive API） |
| `navigation-compose` | `:app` | Apache-2.0 | `@Serializable` 类型安全路由与分方向预测性返回转场 |
| `core-splashscreen` | `:app` | Apache-2.0 | Android 12+ 规范启动画面，冷启动数据加载期间避免白屏 |
| `work-runtime-ktx` | `:app` | Apache-2.0 | 登录后 15 分钟后台周期同步与系统日历对账 |
| `profileinstaller` | `:app` | Apache-2.0 | 安装 Baseline Profile，配合 Macrobenchmark 测量 AOT 启动收益 |
| `material-kolor` | `:app` | MIT | 由种子色生成预设与自定义主题的 M3 配色 |
| `quickjs-kt`（`io.github.dokar3`） | `:script`、`:app` | MIT | 内嵌 QuickJS 引擎运行「高级修改」脚本（支持超时、内存/栈限制，无 IO） |
| JUnit 5（`junit-jupiter`） | `:core`、`:script` 测试 | EPL-2.0 | 纯 JVM 单元测试 |
| JUnit 4 + Robolectric + `compose ui-test-junit4` | `:app` 测试 | EPL-1.0 / MIT / Apache-2.0 | JVM 上渲染全屏冒烟测试并产出截图 |
| `androidx.benchmark` + `uiautomator` | `:benchmark` | Apache-2.0 | 真机 Macrobenchmark 启动与帧耗时基准测试 |
| Spotless Gradle 插件 + ktlint | 构建期工具（不进 APK） | Apache-2.0 / MIT | 格式化与静态检查（规则见 `.editorconfig`） |
| `modernc.org/sqlite` | `server/` | BSD-3-Clause | 纯 Go SQLite 驱动（无需 cgo） |
| `github.com/modelcontextprotocol/go-sdk` | `server/` | MIT | 服务端 `POST /mcp` 的 Streamable HTTP 协议与 Schema 生成 |

## 刻意不引入的依赖

- **Room / KSP**：数据采用「一条记录一行 JSON」双表结构，手写 `SQLiteOpenHelper` 更少依赖与构建步骤，见 [ADR-0002](adr/0002-handwritten-sqlite.md)。
- **DataStore**：设置项少，`SharedPreferences` + `StateFlow` 足够。
- **OkHttp / Retrofit / Ktor**：教务登录（`JxfwClient`）与同步请求用 `HttpURLConnection`；App 本地 MCP 服务用 `ServerSocket` 轻量 HTTP 实现。
- **第三方图表 / 图片渲染库**：课表分享图用 `Canvas` 直接绘制。
- **前端框架 / 打包器 / npm 依赖**：Web 端采用原生 ES 模块 `go:embed` 进服务端，测试用 Node 内置 `node:test`，见 [ADR-0006](adr/0006-web-client-vanilla-esm.md)。
