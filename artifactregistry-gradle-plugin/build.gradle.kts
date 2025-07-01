import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-gradle-plugin`
    `jvm-test-suite`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.gradle.plugin.publish)
}

// java {
//     toolchain {
//         // This sets the base JVM version for the project, including the functional tests
//         // TODO: Consider moving to the test suite configuration
//         // Note: Do not increase above Java 21, as Gradle versions 8-8.7 were not compatible with Java 22+
//         languageVersion = JavaLanguageVersion.of(17)
//     }
// }

tasks.withType<JavaCompile>().configureEach {
   options.release.set(8) // Continue to compile the plugin for Java 8.
}

kotlin {
   compilerOptions {
       jvmTarget.set(JvmTarget.JVM_1_8) // Needs to match the JavaCompile task's JVM release
   }
}

dependencies {
    implementation(gradleApi())
    implementation(libs.google.auth.library.oauth2.http)
    // implementation(libs.jackson.core)
}

// configurations.all {
//     resolutionStrategy {
//         force(libs.jackson.core)
//     }
// }

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
        register<JvmTestSuite>("functionalTest") {
            sources {
                resources.srcDir(rootProject.file("tests"))
            }
            dependencies {
                implementation(project())
                implementation(libs.kotest.runner.junit5)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.framework.datatest)
                implementation(gradleTestKit())
            }
        }
    }
}
tasks.named<Test>("functionalTest") {

    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }

    testLogging {
        events("passed", "skipped", "failed")
        showStackTraces = true
        showExceptions = true
        exceptionFormat = TestExceptionFormat.FULL
    }

    // Load versions from text file and set as system property for functional tests
    val versionsFile = rootProject.file("tests/tested-gradle-versions.txt")
    if (versionsFile.exists()) {
        val gradleVersions =
            versionsFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.joinToString(",")
        systemProperties(mapOf("tested.gradle.versions" to gradleVersions))
    }
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
    testSourceSets(sourceSets.named("functionalTest").get())

}

publishing {
    repositories {
        mavenLocal()
        maven {
            name = "testMavenRepo"
            url = rootProject.layout.buildDirectory.dir("testMavenRepo").get().asFile.toURI()
        }
    }
}
