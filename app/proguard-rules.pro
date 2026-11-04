# R8 收缩规则。依赖自带 consumer 规则的（AGP 自动合并）：
#   - kotlinx-serialization（1.5+ 自带，覆盖生成的 serializer）
#   - quickjs-kt（自带 -keep com.dokar.quickjs.** JNI 边界）
#   - Compose / coroutines / AndroidX
# 项目代码无反射、无 JNI，故只需极少量补充。
#
# 崩溃堆栈还原行号（发布构建建议保留 mapping.txt 随版本归档）
-keepattributes SourceFile,LineNumberTable

# Room 的 *Database_Impl 由 Class.forName + 反射构造（WorkManager 内置 WorkDatabase 走这条路）。
# 2.10.0 自带的 consumer 规则在全量模式下不覆盖无参构造，缺失会在 Application 启动即崩溃。
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
