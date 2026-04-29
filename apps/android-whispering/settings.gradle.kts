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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "scrybe-android"

include(":app")

// Core modules
include(":core:local-ai")
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:audio")
include(":core:network")
include(":core:transcription")
include(":core:transforms")
include(":core:export")

// Feature modules
include(":feature:capture")
include(":feature:history")
include(":feature:session-detail")
include(":feature:profiles")
include(":feature:settings")

// Service modules
include(":service:recording")

// Workers
include(":workers")
