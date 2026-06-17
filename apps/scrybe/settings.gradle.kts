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
        maven { url = settingsDir.resolve(".gradle/local-maven").toPath().toUri() }
    }
}

includeBuild("../../shared") {
    dependencySubstitution {
        substitute(module("com.twobits.core:billing")).using(project(":billing"))
        substitute(module("com.twobits.core:common")).using(project(":common"))
        substitute(module("com.twobits.core:api-keys")).using(project(":api-keys"))
        substitute(module("com.twobits.core:network")).using(project(":network"))
        substitute(module("com.twobits.core:design")).using(project(":design"))
    }
}

rootProject.name = "scrybe-android"

include(":app")

// Core modules
include(":core:local-ai")
include(":core:base") // Renamed from :core:common to avoid confusion with shared :common
project(":core:base").projectDir = File(settingsDir, "core/common")

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
include(":feature:file-manager")
include(":feature:history")
include(":feature:profiles")
include(":feature:session-detail")
include(":feature:settings")
include(":feature:tasks")

// Service modules
include(":service:recording")

// Workers
include(":workers")
