import org.gradle.api.publish.maven.MavenPom

plugins {
    `maven-publish`
    signing
}

description = "Artifact Registry Maven Tools"
val project_version by extra("2.2.6-SNAPSHOT")
val isReleaseVersion by extra(!project_version.endsWith("SNAPSHOT"))

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    group = "com.google.cloud.artifactregistry"
    version = project_version

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    repositories {
        mavenCentral()
    }

    tasks.register("ensureNoGuavaAndroid") {
        doLast {
            configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                val dependency = artifact.moduleVersion.id
                if (dependency.name == "guava" && dependency.version.contains("android")) {
                    throw GradleException("unexpected guava android in $project, found '$dependency'")
                }
            }
        }
    }
    tasks.compileJava.get().dependsOn(tasks.named("ensureNoGuavaAndroid"))
}

subprojects {
    val sourcesJar by tasks.registering(Jar::class) {
        dependsOn(tasks.classes)
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    val javadocJar by tasks.registering(Jar::class) {
        dependsOn(tasks.javadoc)
        archiveClassifier.set("javadoc")
        from(tasks.javadoc.get().destinationDir)
    }

    artifacts {
        add("archives", sourcesJar)
        add("archives", javadocJar)
    }
}

fun commonPomAttributes(pom: MavenPom) {
    pom.url.set("https://github.com/GoogleCloudPlatform/build-artifacts-maven-tools")
    pom.developers {
        developer {
            organization.set("Google LLC")
            organizationUrl.set("http://www.google.com")
        }
    }
    pom.licenses {
        license {
            name.set("The Apache License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }
    pom.scm {
        connection.set("scm:git:https://github.com/GoogleCloudPlatform/artifact-registry-maven-tools.git")
        developerConnection.set("scm:git:https://github.com/GoogleCloudPlatform/artifact-registry-maven-tools.git")
        url.set("https://github.com/GoogleCloudPlatform/artifact-registry-maven-tools")
    }
}

publishing {
    publications {
        create<MavenPublication>("wagon") {
            artifactId = "artifactregistry-maven-wagon"
            val project = project(":artifactregistry-maven-wagon")
            from(project.components["java"])
            artifact(project.tasks.getByName("sourcesJar")) {
                classifier = "sources"
            }
            artifact(project.tasks.getByName("javadocJar")) {
                classifier = "javadoc"
            }
            pom.withXml {
                val pomNode = asNode()
                commonPomAttributes(pom)
                pomNode.appendNode("name", "Artifact Registry Maven Wagon")
                pomNode.appendNode("description", "A Maven wagon used to connect to Artifact Registry Maven repositories.")
            }
        }
        create<MavenPublication>("authCommon") {
            artifactId = "artifactregistry-auth-common"
            val project = project(":artifactregistry-auth-common")
            from(project.components["java"])
            artifact(project.tasks.getByName("sourcesJar")) {
                classifier = "sources"
            }
            artifact(project.tasks.getByName("javadocJar")) {
                classifier = "javadoc"
            }
            pom.withXml {
                val pomNode = asNode()
                commonPomAttributes(pom)
                pomNode.appendNode("name", "Artifact Registry common authentication library")
                pomNode.appendNode("description", "Common authentication library for connecting to Artifact Registry.")
            }
        }
    }
    repositories {
        mavenLocal()
    }
}

signing {
    isRequired.set(isReleaseVersion && gradle.taskGraph.hasTask("publish"))
    sign(publishing.publications["wagon"])
    sign(publishing.publications["authCommon"])
}

project(":artifactregistry-maven-wagon") {
    dependencies {
        "implementation"(project(":artifactregistry-auth-common"))
    }
}

project(":artifactregistry-gradle-plugin") {
    dependencies {
        "implementation"(project(":artifactregistry-auth-common"))
    }
}
