rootProject.name = "SnapADB"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://www.jetbrains.com/intellij-repository/releases")
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")

include(":core:common")
include(":core:adb")
include(":core:database")
include(":core:navigation")
include(":core:theme")
include(":core:ui")

include(":feature:dashboard")
include(":feature:appmanager")
include(":feature:fileexplorer")
include(":feature:shell")
include(":feature:screenmirror")
include(":feature:devicecontrols")
include(":feature:performance")
include(":feature:multidevice")
include(":feature:deeplink")
include(":feature:notification")
include(":feature:appdata")

include(":lib:mirror")
