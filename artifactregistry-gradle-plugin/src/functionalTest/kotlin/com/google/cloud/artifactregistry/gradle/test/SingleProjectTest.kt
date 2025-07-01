/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.artifactregistry.gradle.test

import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.GradleRunner
import java.io.File


private fun getGradleVersions(): List<String> {
    val versions = System.getProperty("tested.gradle.versions")
    return versions?.split(",")?.filter { it.isNotBlank()} ?: emptyList()
}

class SingleProjectTest : StringSpec({
    val pluginId = "com.google.cloud.artifactregistry.gradle-plugin"
    val gradleVersions = getGradleVersions()

    withData(
        nameFn = { gradleVersion -> "plugin applied to project with Gradle $gradleVersion succeeds" },
        gradleVersions
    ) { gradleVersion ->
        val testProjectDir = tempdir()
        File(testProjectDir, "settings.gradle.kts").writeText("")
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            repositories { mavenCentral() }
            plugins { id("$pluginId") }
            tasks.register("checkPlugin") {
                doLast {
                    println("Plugin applied: " + project.plugins.hasPlugin("$pluginId"))
                }
            }
        """
        )
        val result = runner(testProjectDir, gradleVersion, "checkPlugin", "--stacktrace").build()
        result.output shouldContain "Plugin applied: true"
    }

    withData(
        nameFn = { gradleVersion -> "plugin applied to settings with Gradle $gradleVersion succeeds" },
        gradleVersions
    ) { gradleVersion ->
        val testProjectDir = tempdir()
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    mavenCentral()
                }
            }
            plugins {
                id("$pluginId")
            }
            println("Plugin applied: " + settings.plugins.hasPlugin("$pluginId"))
        """
        )
        File(testProjectDir, "build.gradle.kts").writeText("")
        val result = runner(testProjectDir, gradleVersion, "build", "--stacktrace").build()
        result.output shouldContain "Plugin applied: true"
    }
})

private fun runner(
    projectDir: File,
    gradleVersion: String,
    vararg arguments: String
): GradleRunner =
    GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .withGradleVersion(gradleVersion)
