# ADR-0001：`:script` 做成纯 JVM 模块

- 状态：已采纳
- 背景：「高级修改」的 QuickJS 沙箱要有严格的单元测试（超时、内存炸弹、沙箱无 IO、撤销一致性）。开发机（xtop）没有可用的 Android 设备 / 模拟器。
- 决定：`:script` 是 `kotlin.jvm` 模块，依赖 `:core` 与 quickjs-kt（JVM 目标）；`:app` 依赖它并直接声明 quickjs-kt，让 Gradle 在 Android 上选 Android 变体。
- 后果：脚本引擎的行为可以在 JVM 上完整测试（17 个用例）；SDK 脚本 `goke/sdk.js` 与类型声明 `goke.d.ts` 作为 jar 资源打包，运行时由 `ScriptRunner` 读取。Android 上的真机行为（原生库加载）需要真机验证。
