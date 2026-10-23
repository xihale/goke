# ADR-0004：几项默认选择

- **字体**：使用系统默认字体（不打包字体文件），减小体积，也避免字体授权问题。
- **测试框架**：纯 JVM 模块用 JUnit 5；`:app` 的 Robolectric 测试用 JUnit 4（Robolectric 要求）。
- **文案**：全部写在 `strings.xml`（默认简体中文），`scripts/check_strings.py` 检查代码引用的字符串是否都已定义；英文翻译预留（`values-en`）。
- **AI Key**：只存在本机，用 Android Keystore 的 AES-GCM 加密后写入 `SharedPreferences`；不进备份、不进导出文件、不进仓库。
- **提醒**：每次数据 / 设置变化后（防抖 0.8 秒）重排未来 7 天内最早的 40 条提醒；有「精确闹钟」权限用精确闹钟，否则退回非精确。
- **快递识别**：默认关闭，只读取通知文本，本地规则识别；不读取短信。
