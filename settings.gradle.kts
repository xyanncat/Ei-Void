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
        google()
        mavenCentral()
    }
}

rootProject.name = "Void"

include(
    ":app",
    ":core:common",
    ":core:security",
    ":core:database",
    ":core:git",
    ":core:workspace",
    ":core:ai",
    ":feature:auth",
    ":feature:repos",
    ":feature:workspace",
    ":feature:settings",
    ":designsystem",
)
