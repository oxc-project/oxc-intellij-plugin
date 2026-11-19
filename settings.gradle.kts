@file:Suppress("UnstableApiUsage")

import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        // Hosts the rpc Gradle plugin.
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/")
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.20.0"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "oxc-intellij-plugin"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

include("shared")
include("frontend")
include("backend")
