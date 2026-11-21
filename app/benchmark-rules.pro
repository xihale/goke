# Macrobenchmark 测试 APK 不打包 Kotlin stdlib（com.android.test 把它当作目标进程提供的依赖），
# 测试侧（androidx.test / uiautomator）会按原始类名引用 kotlin.*。这里保留原始名字，
# 避免目标 R8 重命名/删除后测试进程 ClassNotFound。仅 benchmark 变体生效。
-keep class kotlin.** { *; }
