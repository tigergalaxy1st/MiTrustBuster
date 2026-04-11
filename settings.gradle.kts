pluginManagement {
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
        maven { url = uri("https://api.github.com/repos/rovo89/XposedBridge/releases//download/82/") }
    }
}

rootProject.name = "MiTrustBuster"
include(":app")
