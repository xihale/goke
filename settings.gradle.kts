pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
                }
            }
            filter {
                includeModule("org.robolectric", "android-all-instrumented")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "goke"
include(":core")
include(":script")
include(":app")
// benchmark 模块可能被临时移除；存在才挂载，避免整个构建无法配置
if (file("benchmark").exists()) include(":benchmark")
// adapters 为私有协议适配器模块（半开源边界）；存在时自动挂载，缺失时自动回退至开源桩实现
if (file("adapters/build.gradle.kts").exists()) include(":adapters")
