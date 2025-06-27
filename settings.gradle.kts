plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.10.0")
}

rootProject.name = "artifactregistry-maven-tools"
include("artifactregistry-maven-wagon")
include("artifactregistry-auth-common")
include("artifactregistry-gradle-plugin")
