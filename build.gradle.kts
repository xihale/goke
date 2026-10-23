plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

val ktlintOverrides = mapOf(
    "ktlint_standard_if-else-wrapping" to "disabled",
    "ktlint_standard_mixed-condition-operators" to "disabled",
)

// 格式化与静态检查：`./gradlew spotlessCheck` 检查，`./gradlew spotlessApply` 自动修复。规则见 .editorconfig。
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath("$rootDir/.editorconfig")
            .editorConfigOverride(ktlintOverrides)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath("$rootDir/.editorconfig")
            .editorConfigOverride(ktlintOverrides)
    }
}
