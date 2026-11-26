import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

val ktlintOverrides =
    mapOf(
        "ktlint_standard_if-else-wrapping" to "disabled",
        "ktlint_standard_mixed-condition-operators" to "disabled",
    )

// 格式化与静态检查：`./gradlew spotlessCheck` 检查，`./gradlew spotlessApply` 自动修复。规则见 .editorconfig。
spotless {
    kotlin {
        target(
            "app/src/**/*.kt",
            "core/src/**/*.kt",
            "script/src/**/*.kt",
            "benchmark/src/**/*.kt",
        )
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath("$rootDir/.editorconfig")
            .editorConfigOverride(ktlintOverrides)
    }
    kotlinGradle {
        target(
            "*.gradle.kts",
            "app/*.gradle.kts",
            "core/*.gradle.kts",
            "script/*.gradle.kts",
            "benchmark/*.gradle.kts",
        )
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath("$rootDir/.editorconfig")
            .editorConfigOverride(ktlintOverrides)
    }
}

// 由 Gradle 解析并缓存 Robolectric 所需的 Android SDK（对应 libs.robolectric 4.16 + sdk=35），
// 避免测试 JVM 运行时通过 MavenArtifactFetcher 直连 repo1.maven.org 触发瞬态网络失败。
val robolectricSdk =
    configurations.create("robolectricSdk") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
    }

dependencies {
    robolectricSdk("org.robolectric:android-all-instrumented:15-robolectric-13954326-i7")
}

val robolectricDepsDir = layout.buildDirectory.dir("robolectric-deps")
val syncRobolectricDeps =
    tasks.register<Sync>("syncRobolectricDeps") {
        from(robolectricSdk)
        into(robolectricDepsDir)
    }

subprojects {
    if (name == "app" || name == "adapters") {
        val depsPath = robolectricDepsDir.get().asFile.absolutePath
        tasks.withType<Test>().configureEach {
            dependsOn(syncRobolectricDeps)
            systemProperty("robolectric.dependency.dir", depsPath)
            systemProperty("robolectric.offline", "true")
        }
    }
}
