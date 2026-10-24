# R8 收缩规则。依赖自带 consumer 规则的（AGP 自动合并）：
#   - kotlinx-serialization（1.5+ 自带，覆盖生成的 serializer）
#   - quickjs-kt（自带 -keep com.dokar.quickjs.** JNI 边界）
#   - Compose / coroutines / AndroidX
# 项目代码无反射、无 JNI，故只需极少量补充。
#
# 崩溃堆栈还原行号（发布构建建议保留 mapping.txt 随版本归档）
-keepattributes SourceFile,LineNumberTable
