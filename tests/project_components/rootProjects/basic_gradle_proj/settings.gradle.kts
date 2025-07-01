pluginManagement {
    repositories {
        exclusiveContent {
            forRepository { maven { url = uri("../../ar-test-maven-repo") } }
            filter { includeGroupByRegex("com[.]google[.]cloud[.]artifactregistry.*") }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs { create("libs") { from(files("../libs.versions.toml")) } }
}

plugins { id("com.google.cloud.artifactregistry.gradle-plugin") version "+" }

rootProject.name = "basic_gradle_proj"

include("lib")

project(":lib").projectDir = File("../subProjects/lib")
