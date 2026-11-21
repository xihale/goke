# 测试类由 instrumentation 按类名反射加载（R8 默认会删掉没有静态引用的 JUnit 类）
-keep class app.mintcal.benchmark.** { *; }
-keepnames class app.mintcal.benchmark.**

# benchmark / lifecycle / tracing 的可选依赖，测试 APK 里没有也不需要
-dontwarn androidx.arch.core.**
-dontwarn androidx.profileinstaller.**
-dontwarn androidx.startup.**
-dontwarn com.google.errorprone.annotations.**
