# 构建、测试与性能基准

## 常用开发命令

```bash
./gradlew :core:test :script:test          # 纯 JVM 单元测试（JUnit 5）
./gradlew :app:testDebugUnitTest           # Robolectric 渲染冒烟 + 截图输出 app/build/shots/
./gradlew :app:assembleDebug               # Debug APK（包名 app.goke.debug）
./gradlew spotlessCheck                    # ktlint 格式检查（spotlessApply 自动修复）
python3 scripts/check_strings.py --unused  # 检查缺失与未使用的 strings.xml 资源
```

## 端到端与远程构建脚本

```bash
bash scripts/sync-e2e.sh                   # Go 同步服务端到端测试（含双模拟设备对齐）
bash scripts/web-e2e.sh                    # 启动临时服务端并运行 Web / MCP 端到端测试（需 Go 与 Node ≥ 20）
bash scripts/xbuild.sh <gradle-task>       # 在远程构建机拉取代码并执行 Gradle，日志写入 build/xbuild.log
```

## Macrobenchmark（`:benchmark`，需真机）

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest   # 全量基准（启动 / 切页 / 交互）
# 只跑单一用例，避免每次全量：
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.goke.benchmark.TabSwitchBenchmark
```

- **目标包名 `app.goke.bench`**：`:app` 的 `benchmark` 变体开启 R8/资源收缩与 `profileable`，使用 debug 签名，不会覆盖 `app.goke` 或 `app.goke.debug`。
- **测试数据**：首次启动由 `BenchSeed`（`BuildConfig.BENCHMARK` 分支）写入可滚动的课表与待办；`release` / `debug` 下该分支被常量折叠并在 R8 阶段移除。
- **用例清单（`benchmark/src/main/java/app/goke/benchmark/`）**：
  - `StartupBenchmark`：冷/温/热启动及无 Baseline Profile 对照（`StartupTimingMetric`）
  - `TabSwitchBenchmark`：底栏 4 Tab 切换（`FrameTimingMetric`）
  - `NavigationBenchmark`：设置 / 外观 / 数据 / 课程与待办编辑页压栈转场（`FrameTimingMetric`）
  - `TodayBenchmark`：今日页时间线滚动、已完成折叠区与快速添加开关
  - `TimetableBenchmark`：课表左右翻周、网格滚动与课程详情弹窗
  - `CalendarBenchmark`：月网格翻页、日期点选与月/周切换
  - `TodoBenchmark`：待办列表滚动、勾选、筛选搜索与快速添加输入
- **指标与报告**：关注 `frameDurationCpuMs`（P50/P90/P99）、`frameOverrunMs`，启动关注 `timeToInitialDisplayMs` / `timeToFullDisplayMs`。报告输出在 `benchmark/build/outputs/androidTest-results/connected/`。
- **模拟器不可用**：Macrobenchmark 依赖 Perfetto，在模拟器上会报 `did not detect tracing`；需使用解锁且屏幕常亮的真机。

## 真机专项验证清单

Robolectric 与 JVM 单测无法覆盖的系统能力，发版或大改手势/后台逻辑后需在真机复验：

- **提醒可靠性与后台限制**：进入「设置 → 提醒可靠性自检」逐项确认绿勾；点击测试提醒后立即锁屏并划掉应用，确认闹钟按时弹出（小米 / OPPO / vivo / 华为等 ROM 需按页内指引开启自启动与无限制省电）。
- **教务同步**：验证真实学号 CAS 登录（含首次/错误提示）、周日增量闹钟与重启追赶。
- **手势与 ROM 边缘冲突**：待办长按拖动跨区/右拖变子任务、左右滑完成/改期，以及课表网格拖动改时段/框选，在全面屏边缘返回手势下的触发边界。
- **小组件与系统入口**：5 款桌面小组件勾选/刷新的延迟、通知栏「完成 / 1 小时后再提醒」、快捷设置磁贴、外部分享文本与 `PROCESS_TEXT` 划词添加。
- **系统日历双向同步**：授权日历权限后开启「系统日历同步」，确认新建本地日历账本及双向防抖对账无回声循环。
