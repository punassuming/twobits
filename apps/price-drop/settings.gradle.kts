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
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
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

rootProject.name = "pricedrop"
include(":app")
