pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Perilog"
include(":app")
// Standalone launcher host for minified APK/process-death regressions; never shipped.
if (providers.gradleProperty("widgetHost").isPresent) include(":widget-test-host")
