import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp" && requested.version == "2.3.12") {
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12")
            }
        }
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "TMFM"
include(":app")
