plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}

rootProject.name = "artifactregistry-maven-tools"
include("artifactregistry-maven-wagon")
include("artifactregistry-auth-common")
include("artifactregistry-gradle-plugin")
