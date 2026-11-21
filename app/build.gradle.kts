import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties
import javax.inject.Inject

// release 签名（keystore.properties 不入库；缺文件时 assembleRelease 会明确失败）
val releaseProps =
    Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) FileInputStream(f).use { load(it) }
    }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.mintcal"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.mintcal"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // benchmark 变体置 true：只用来在首启时塞一份可滚动数据（BenchSeed），release/debug 下被常量折叠
        buildConfigField("boolean", "BENCHMARK", "false")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(releaseProps.getProperty("storeFile", "keystore/mintcal-release.jks"))
            storePassword = releaseProps.getProperty("storePassword")
            keyAlias = releaseProps.getProperty("keyAlias", "mintcal")
            keyPassword = releaseProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // R8 全量收缩 + 资源收缩：release 才是真实流畅度（无 JIT / 无调试开销）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // 包名后缀区分：debug 装在 app.mintcal.debug，与 release 并存
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        create("benchmark") {
            // Macrobenchmark 目标：release 的 R8 / 资源收缩（真实流畅度）+ debug 签名（可直接安装）。
            // 包名再加后缀，避免覆盖用户已装的 release 包与真实数据。
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".bench"
            versionNameSuffix = "-bench"
            isDebuggable = false
            buildConfigField("boolean", "BENCHMARK", "true")
            proguardFiles("benchmark-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.jvmArgs("-Xmx2g") }
        }
    }
    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.animation.ExperimentalAnimationApi",
        )
    }
}

// :core 是纯 Kotlin 模块，Compose 编译器跨模块推断不到稳定性，其模型一律按 unstable 处理。
// 这份配置把不可变模型声明为 stable，strong skipping 才能按值跳过重组。
composeCompiler {
    stabilityConfigurationFiles.set(listOf(layout.projectDirectory.file("../compose_stability.conf")))
}

dependencies {
    implementation(project(":core"))
    implementation(project(":script"))
    implementation(libs.quickjs.kt)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // 手写 baseline profile（baseline-prof.txt）在装包时 AOT 生效的载体
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.materialkolor)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.quickjs.kt.jvm)
}

// Robolectric 跑在宿主机 JVM 上，无法加载 Android AAR 内的 Bionic libquickjs.so；单测配置切换为宿主 JVM 变体。
configurations.matching { it.name.contains("UnitTest") }.configureEach {
    exclude(group = "io.github.dokar3", module = "quickjs-kt")
    exclude(group = "io.github.dokar3", module = "quickjs-kt-android")
}

// 局域网电脑端网页资源以 server/internal/server/web/app 为唯一真源，通过 Variant API 同步进各变体 assets/lanweb/app。
abstract class SyncLanWebAssetsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun syncAssets() {
        fs.sync {
            from(sourceDir) {
                into("lanweb/app")
            }
            into(outputDir.get().asFile)
        }
    }
}

androidComponents {
    onVariants { variant ->
        val taskName = "sync${variant.name.replaceFirstChar { it.uppercase() }}LanWebAssets"
        val syncTask =
            tasks.register<SyncLanWebAssetsTask>(taskName) {
                sourceDir.set(layout.projectDirectory.dir("../server/internal/server/web/app"))
            }
        variant.sources.assets?.addGeneratedSourceDirectory(
            syncTask,
            SyncLanWebAssetsTask::outputDir,
        )
    }
}
