# 薄荷广工 MintGDUT（MintCal）

开源、本地优先的 Android 日程工具：**课表 + 日历 + 待办 + 校园取件** 一体化管理。内置广工教务（`jxfw`）课表/考试同步与 QuickJS「高级修改」沙箱，并通过 **本地 MCP** 与 **自建同步服务（含 Web 端与云端 MCP）** 接入外部 AI 助手。

> 工程入口与硬红线见 [CLAUDE.md](CLAUDE.md)，视觉与组件规范见 [DESIGN.md](DESIGN.md)，模块架构见 [docs/architecture.md](docs/architecture.md)，功能规格索引见 [docs/specs/README.md](docs/specs/README.md)。

## 核心特性

- **四核心视图**：底部胶囊浮岛常驻 **今日 · 课表 · 日历 · 待办**，Material 3 Expressive 风格，默认薄荷宣纸主题，支持 6 套植物预设、自定义种子色与 Android 12+ 动态取色。
- **广工教务同步（S012）**：CAS 统一认证一键导入整学期课表、开学日与考试安排，周日自动增量更新调停课与补课，内置四校区节次预设与多机房地点折叠。
- **课表与编辑**：周视图支持长按拖动改时间、拖上下边调时长、空白格框选批量新建，冲突时段自动叠放并支持弹层切换；课程编辑采用结构化多时段行，支持独立教室与提醒。
- **待办与日历**：一行快速添加（自然语言日期/优先级/清单/`/课程`/多行批量/语音/外部分享），时间线分区、长按拖动跨区或设子任务、多选批量与可撤销删除；待办可排入日历与课表。
- **快递取件与系统联动**：本地规则引擎（用户可增改规则与噪声词）识别取件码通知并排入每日顺路取件时段；支持 5 款桌面小组件、上课自动勿扰、系统日历双向同步与提醒可靠性自检。
- **高级修改与双模式 MCP**：QuickJS 沙箱支持脚本批量修改与 Dry Run 预览（可连续撤销）；不内置 AI，可通过 App **本地 MCP**（含脚本工具，[S016](docs/specs/016-local-mcp.md)）或 **自建同步服务云端 MCP**（[S015](docs/specs/015-mcp.md)）连接外部 AI 助手。
- **完全本地可用 & 可选同步**：不登录即纯本地 SQLite 模式；可选部署 Go 自建同步服务（见 [server/README.md](server/README.md)），自带原生 ES 模块 Web 端，支持多端按记录同步。

## 技术栈

Kotlin · Jetpack Compose · Material 3 Expressive · 手写 SQLite（[ADR-0002](docs/adr/0002-handwritten-sqlite.md)） · QuickJS (`quickjs-kt`) · Go + SQLite 服务端。最低支持 Android 8.0（API 26）。

## 构建与测试

```bash
./gradlew :core:test :script:test          # 纯 JVM 单元测试
./gradlew :app:testDebugUnitTest           # Robolectric 冒烟与截图（输出 app/build/shots/）
./gradlew :app:assembleDebug               # 构建 Debug APK
./gradlew spotlessCheck                    # ktlint 格式检查（spotlessApply 自动格式化）
python3 scripts/check_strings.py --unused  # 字符串资源检查
```

更多测试（`sync-e2e.sh`、`web-e2e.sh`、Macrobenchmark 与真机专项清单）见 [docs/testing.md](docs/testing.md)。

## 自建同步服务

见 [server/README.md](server/README.md)：Go + SQLite 单二进制 / Docker 部署，管理员创建账号，App 登录后每 15 分钟后台同步一次。

## 许可证

[AGPL-3.0](LICENSE)
