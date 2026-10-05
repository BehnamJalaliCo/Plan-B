pluginManagement {
    includeBuild("build-logic")
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
        // Cafe Bazaar's in-app billing library (Poolakey) is published only on JitPack. The
        // content filter keeps every other dependency from ever resolving there.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") { name = "JitPack" } }
            filter { includeGroup("com.github.cafebazaar.Poolakey") }
        }
    }
}

rootProject.name = "Plan-B"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":wear")
include(":core:common")
include(":core:model")
include(":core:datetime")
include(":core:nlp")
include(":core:designsystem")
include(":core:testing")
include(":core:ui")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:notifications")
include(":core:backup")
include(":core:billing")
include(":core:ai")
include(":feature:today")
include(":feature:tasks")
include(":feature:calendar")
include(":feature:projects")
include(":feature:notebooks")
include(":feature:habits")
include(":feature:goals")
include(":feature:focus")
include(":feature:search")
include(":feature:templates")
include(":feature:review")
include(":feature:settings")
include(":feature:pro")
include(":feature:security")
include(":feature:reports")
include(":baselineprofile")
include(":benchmark")
