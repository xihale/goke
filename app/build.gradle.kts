import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.Properties
import javax.inject.Inject

// 通过 ValueSource 执行 git describe 以兼容 Gradle Configuration Cache。
abstract class GitDescribeSource : ValueSource<String, GitDescribeSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val workingDir: DirectoryProperty
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val result =
            execOperations.exec {
                workingDir = parameters.workingDir.asFile.get()
                commandLine("git", "describe", "--tags", "--match", "v[0-9]*", "--long", "--dirty", "--always")
                standardOutput = out
                errorOutput = err
                isIgnoreExitValue = true
            }
        return if (result.exitValue == 0) out.toString(Charsets.UTF_8).trim() else ""
    }
}

// 版本号由 git tag（v*）派生，支持 SemVer 预发布后缀（如 v0.0.1-beta.1）：
// versionName = git describe 去 v 前缀——精确打在 tag 上时为干净 semver（如 0.0.1-beta.1），
//   tag 之后形如 0.0.1-beta.1-1-g<sha>，工作区有未提交改动再加 -dirty；
// versionCode 严格单调递增：alpha < beta < rc < 正式版 < 下一 patch/minor/major。
fun computeVersionCode(
    major: Int,
    minor: Int,
    patch: Int,
    preRelease: String?,
    commitsAfter: Int,
): Int {
    val base = major * 100_000_000 + minor * 1_000_000 + patch * 10_000
    if (preRelease.isNullOrBlank()) {
        return base + 9_000 + commitsAfter.coerceIn(0, 999)
    }
    val preMatch = Regex("""^(alpha|beta|rc)[.-]?(\d+)?""", RegexOption.IGNORE_CASE).find(preRelease)
    val stage =
        when (preMatch?.groupValues?.getOrNull(1)?.lowercase()) {
            "alpha" -> 1
            "beta" -> 2
            "rc" -> 3
            else -> 4
        }
    val preNum = (preMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0).coerceIn(0, 99)
    return base + stage * 1_000 + preNum * 10 + commitsAfter.coerceIn(0, 9)
}

// 日常 debug / 单测固定静态版本，避免每次提交或 clean/dirty 切换使 BuildConfig/Manifest/Kotlin 缓存失效；
// release / benchmark 或传入 -PgitVersion=true 时再由 git tag 派生完整 SemVer。
val useGitVersion =
    providers.gradleProperty("gitVersion").orNull == "true" ||
        gradle.startParameter.taskNames.any { name ->
            val short = name.substringAfterLast(':')
            name.contains("Release", ignoreCase = true) ||
                name.contains("Benchmark", ignoreCase = true) ||
                short == "assemble" ||
                short == "build"
        }

val appVersion: Pair<String, Int> =
    if (!useGitVersion) {
        "0.0.1" to 1
    } else {
        val describe =
            providers
                .of(GitDescribeSource::class) {
                    parameters.workingDir.set(layout.projectDirectory.dir(".."))
                }.get()
        val match =
            Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+?))?(?:-(\d+)-g[0-9a-f]+)?(-dirty)?$""")
                .find(describe)
        if (match == null) {
            logger.info("未找到 v* SemVer git tag（describe=$describe），使用初始版本 0.0.1-beta.1")
            "0.0.1-beta.1" to computeVersionCode(0, 0, 1, "beta.1", 0)
        } else {
            val major = match.groupValues[1].toInt()
            val minor = match.groupValues[2].toInt()
            val patch = match.groupValues[3].toInt()
            val pre = match.groupValues[4].takeIf { it.isNotBlank() }
            val commitsAfter = match.groupValues[5].toIntOrNull() ?: 0
            // 精确在 tag 上时去掉 --long 的 -0-g<sha> 尾巴，发布产物拿到干净 semver
            val name =
                describe.removePrefix("v").let {
                    if (commitsAfter == 0) it.replace(Regex("""-0-g[0-9a-f]+"""), "") else it
                }
            name to computeVersionCode(major, minor, patch, pre, commitsAfter)
        }
    }

// release 签名：优先读环境变量（CI），其次读根目录 keystore.properties（本地）；均缺失时 release 构建显式报错，不回退 debug 签名
val releaseProps =
    Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) FileInputStream(f).use { load(it) }
    }
val signingEnvVars =
    listOf(
        "SIGNING_KEYSTORE_FILE",
        "SIGNING_KEYSTORE_PASSWORD",
        "SIGNING_KEY_ALIAS",
        "SIGNING_KEY_PASSWORD",
    )
val hasSigningEnv = signingEnvVars.all { !System.getenv(it).isNullOrBlank() }
val releaseStoreFile: File? =
    when {
        hasSigningEnv -> file(System.getenv("SIGNING_KEYSTORE_FILE"))
        !releaseProps.getProperty("storeFile").isNullOrBlank() -> rootProject.file(releaseProps.getProperty("storeFile"))
        else -> null
    }
val releaseStorePassword: String? =
    if (hasSigningEnv) System.getenv("SIGNING_KEYSTORE_PASSWORD") else releaseProps.getProperty("storePassword")
val releaseKeyAlias: String? =
    if (hasSigningEnv) System.getenv("SIGNING_KEY_ALIAS") else releaseProps.getProperty("keyAlias")
val releaseKeyPassword: String? =
    if (hasSigningEnv) System.getenv("SIGNING_KEY_PASSWORD") else releaseProps.getProperty("keyPassword")
val hasReleaseSigning =
    releaseStoreFile != null &&
        releaseStoreFile.exists() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.goke"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.goke"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersion.second
        versionName = appVersion.first
        // benchmark 变体置 true：只用来在首启时塞一份可滚动数据（BenchSeed），release/debug 下被常量折叠
        buildConfigField("boolean", "BENCHMARK", "false")
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // R8 全量收缩 + 资源收缩：release 才是真实流畅度（无 JIT / 无调试开销）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            ndk {
                // 仅分发 ARMv8（arm64-v8a）包体，其余架构由开发者自行编译
                abiFilters += "arm64-v8a"
            }
        }
        debug {
            // 包名后缀区分：debug 装在 app.goke.debug，与 release 并存
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // 默认只保留 arm64-v8a，避免 CLI debug 每次打入全部 4 套 native .so；
            // Android Studio 注入 android.injected.build.abi 时自动让路，也可用 -PdebugAbi=x86_64 或 -PdebugAbi=all 覆盖
            val injectedAbi = providers.gradleProperty("android.injected.build.abi").orNull
            val debugAbi = providers.gradleProperty("debugAbi").orNull
            if (injectedAbi.isNullOrBlank() && debugAbi != "all") {
                val abis =
                    (debugAbi ?: "arm64-v8a")
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                if (abis.isNotEmpty()) {
                    ndk { abiFilters += abis }
                }
            }
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

// release 变体缺少签名配置时直接失败，避免静默产出无签名或 debug 签名包
val appProjectPath = project.path
gradle.taskGraph.whenReady {
    if (hasReleaseSigning) return@whenReady
    val releaseTask =
        allTasks.firstOrNull { task ->
            task.path.startsWith("$appProjectPath:") &&
                task.name.contains("Release") &&
                listOf("assemble", "bundle", "package", "sign").any { task.name.startsWith(it) }
        }
    if (releaseTask != null) {
        throw GradleException(
            "缺少 release 签名配置：请设置环境变量 SIGNING_KEYSTORE_FILE / SIGNING_KEYSTORE_PASSWORD / " +
                "SIGNING_KEY_ALIAS / SIGNING_KEY_PASSWORD，或在根目录提供 keystore.properties。",
        )
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
    implementation(libs.androidx.material.icons.core)
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
