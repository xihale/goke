# 构建、测试与性能基准

## 常用开发命令

```bash
./gradlew :core:test :script:test          # 纯 JVM 单元测试（JUnit 5）
./gradlew :app:testDebugUnitTest           # Robolectric 渲染冒烟 + 截图输出 app/build/shots/
./gradlew :app:assembleDebug               # Debug APK（包名 app.mintcal.debug）
./gradlew spotlessCheck                    # ktlint 格式检查（spotlessApply 自动修复）
python3 scripts/check_strings.py --unused  # 检查缺失与未使用的 strings.xml 资源
```

## 端到端与远程构建脚本

```bash
bash scripts/sync-e2e.sh                   # Go 同步服务端到端测试
bash scripts/web-e2e.sh                    # 启动临时服务端并运行 Web E2E（需要 Go 与 Node ≥ 20）
bash scripts/xbuild.sh <gradle-task>       # 在远程构建机拉取代码并执行 Gradle，日志写入 build/xbuild.log
```

## Macrobenchmark（`:benchmark`，需真机）

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest   # 启动 / 切页 / 滚动 / 勾选，10 次迭代
# 只跑单一用例，避免每次全量：
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.mintcal.benchmark.TabSwitchBenchmark
```

- **目标包名 `app.mintcal.bench`**：`:app` 的 `benchmark` 变体开启 R8/资源收缩与 `profileable`，使用 debug 签名，不会覆盖 `app.mintcal` 或 `app.mintcal.debug`。
- **测试数据**：首次启动由 `BenchSeed`（`BuildConfig.BENCHMARK` 分支）写入可滚动的课表与待办；`release` / `debug` 下该分支被常量折叠并在 R8 阶段移除。
- **用例**：
  - `StartupBenchmark`：冷/温启动（`StartupTimingMetric`）
  - `TabSwitchBenchmark`：底栏切页（`FrameTimingMetric`）
  - `TodoBenchmark`：列表滚动与勾选（`FrameTimingMetric`）
- **指标与报告**：关注 `frameDurationCpuMs`（P50/P90/P99）、`frameOverrunMs`，启动关注 `timeToInitialDisplayMs` / `timeToFullDisplayMs`。报告输出在 `benchmark/build/outputs/androidTest-results/connected/`。
- **模拟器不可用**：Macrobenchmark 依赖 Perfetto，在模拟器上会报 `did not detect tracing`；需使用解锁且屏幕常亮的真机。
