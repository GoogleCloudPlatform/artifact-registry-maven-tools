plugins {
    `java-gradle-plugin`
    `jvm-test-suite`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.gradle.plugin.publish)
}

kotlin { jvmToolchain(21) }

testing {
    suites {
        val test by getting (JvmTestSuite::class) {
            useJUnitJupiter()
        }
        register<JvmTestSuite>("functionalTest") {
            dependencies {
                implementation(project())
                implementation(libs.kotest.runner.junit5)
                implementation(libs.kotest.assertions.core)
                implementation(gradleTestKit())
            }
        }
    }
}

dependencies {
    implementation(gradleApi())
    implementation(libs.google.auth.library.oauth2.http)
}

gradlePlugin {
    website.set("https://github.com/GoogleCloudPlatform/artifact-registry-maven-tools")
    vcsUrl.set("https://github.com/GoogleCloudPlatform/artifact-registry-maven-tools.git")
    plugins {
        create("artifactRegistryPlugin") {
            id = "com.google.cloud.artifactregistry.gradle-plugin"
            displayName = "Artifact Registry Gradle Plugin"
            description = "A Gradle plugin used to connect to Artifact Registry Maven repositories."
            tags.set(listOf("artifactregistry", "gcp"))
            implementationClass = "com.google.cloud.artifactregistry.gradle.plugin.ArtifactRegistryGradlePlugin"
        }
    }
    testSourceSets( sourceSets.named("functionalTest").get())
}
