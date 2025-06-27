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
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.GradleRunner
import java.io.File

class SingleProjectTest : StringSpec({

    val testProjectDir = tempdir()

    "plugin applied to build.gradle succeeds" {
        File(testProjectDir, "settings.gradle.kts").writeText("")
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { id("com.google.cloud.artifactregistry.gradle-plugin") }
            tasks.register("checkPlugin") {
                doLast {
                    println("Plugin applied: " + project.plugins.hasPlugin("com.google.cloud.artifactregistry.gradle-plugin"))
                }
            }
        """)
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("checkPlugin", "--stacktrace")
            .withPluginClasspath()
            .build()

        result.output shouldContain "Plugin applied: true"
    }

    "plugin applied to settings.gradle succeeds" {
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("com.google.cloud.artifactregistry.gradle-plugin")
            }
            println("Plugin applied: " + (settings.plugins.hasPlugin("com.google.cloud.artifactregistry.gradle-plugin")))
        """)
        File(testProjectDir, "build.gradle.kts").writeText("")
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("build", "--stacktrace")
            .withPluginClasspath()
            .build()

        result.output shouldContain "Plugin applied: true"
    }
})
