pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "VanFieldGuide"
include(":app")
// Optional isolated hardware experiment; never changes the field-guide APK.
if (providers.gradleProperty("npuProbe").isPresent) include(":npu-probe")
