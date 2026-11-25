# R8 收缩规则。依赖自带 consumer 规则的（AGP 自动合并）：
#   - kotlinx-serialization（1.5+ 自带，覆盖生成的 serializer）
#   - quickjs-kt（自带 -keep com.dokar.quickjs.** JNI 边界）
#   - Compose / coroutines / AndroidX
# 项目代码无反射、无 JNI，故只需极少量补充。
#
# 崩溃堆栈保留行号配合 mapping.txt 还原，同时抹除真实源文件名并拍平包层级
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-repackageclasses 'o'

# Release 剥离系统日志调用及其附带的明文调试/协议描述字符串
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Room 的 *Database_Impl 由 Class.forName + 反射构造（WorkManager 内置 WorkDatabase 走这条路）。
# 2.10.0 自带的 consumer 规则在全量模式下不覆盖无参构造，缺失会在 Application 启动即崩溃。
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
