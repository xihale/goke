# ADR-0004：几项默认选择

- **字体**：暂时使用系统默认字体（不打包字体文件），**待用户选定圆润标题字体后再接入**。候选方向：Resource Han Rounded（资源圆体，SIL OFL 1.1，简体中文圆体，需裁剪子集，Bold 子集约 1.5MB）、Nunito（OFL，仅拉丁字母，体积小，汉字回退系统字体）。接入方式：把字体放进 `app/src/main/res/font/`，在 `ui/theme/Theme.kt` 的 `MintTypography` 给 headline / title 字阶设置 `fontFamily`；字体许可证文本放入 `docs/third_party/`，并更新 `docs/DEPENDENCIES.md`。
- **测试框架**：纯 JVM 模块用 JUnit 5；`:app` 的 Robolectric 测试用 JUnit 4（Robolectric 要求）。
- **文案**：全部写在 `strings.xml`（默认简体中文），`scripts/check_strings.py` 检查代码引用的字符串是否都已定义；英文翻译预留（`values-en`）。
- **敏感凭据**：同步令牌与教务密码使用 Android Keystore 的 AES-GCM 加密后保存（`SecretStore`），本地 MCP 令牌存于 `McpTokenVault`；均不进系统备份、导出 JSON 或版本库。
- **提醒**：每次数据 / 设置变化后（防抖 0.8 秒）重排未来 7 天内最早的 40 条提醒；有「精确闹钟」权限用精确闹钟，否则退回非精确。
- **快递识别**：默认关闭，只读取通知文本，本地规则识别；不读取短信。
