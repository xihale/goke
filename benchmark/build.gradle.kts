plugins {
    alias(libs.plugins.android.test)
}

// Macrobenchmark：独立的 com.android.test 模块，targetProjectPath 指向 :app 的 benchmark 变体。
// 目标 APK 是 release 优化（R8 + 资源收缩）+ debug 签名 + app.mintcal.bench 后缀，
// 不会覆盖设备上已装的 release / debug 包。
android {
    namespace = "app.mintcal.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    // macrobenchmark 必须让测试 APK 独立进程运行，才能 force-stop / 编译 / 拉起目标进程
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            // 目标 APK 开了 R8，AGP 要求测试模块同样收缩，否则拒绝安装
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro"))
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    // 目标 APK 开了 R8，测试 APK 也必须收缩；这几个是 benchmark / lifecycle / tracing 的
    // 可选依赖，补上让 R8 的 missing-class 检查通过（测试进程不会执行它们）。
    implementation("androidx.arch.core:core-runtime:2.2.0")
    implementation("androidx.tracing:tracing:1.3.0")
    // uiautomator 传递的 core:1.9.0 还带着 FingerprintManagerCompat（API 37 已删平台类），抬到与应用一致
    implementation("androidx.core:core:1.19.1")
    // 测试进程（androidx.test）直接引用 kotlin.LazyKt；目标侧保留 kotlin.** 原名见 app/benchmark-rules.pro
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.startup:startup-runtime:1.2.0")
    implementation("com.google.errorprone:error_prone_annotations:2.50.0")
}
