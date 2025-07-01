package com.google.cloud.artifactregistry.gradle.test.util

fun versionsFromProperties(): List<String> {
    val versions = System.getProperty("tested.gradle.versions", "8.5")
    return versions.split(",").map { it.trim() }
}