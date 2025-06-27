plugins {
    `java-gradle-plugin`
    alias(libs.plugins.gradle.plugin.publish)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
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
}
