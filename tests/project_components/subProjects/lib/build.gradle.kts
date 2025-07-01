
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}
dependencies {
    api(libs.commons.math3)
    implementation(libs.guava)
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
