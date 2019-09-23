# Cloud Build Artifacts Maven Tools

This repository contains tools to help with interacting with Maven repositories hosted on Cloud
Build Artifacts.

## Authentication

Requests to Cloud Build Artifacts will be authenticated using credentials from the environment. The
tools described below search the environment for credentials in the following order:
1. From the `gcloud` SDK. (i.e., the access token printed via `gcloud config config-helper --format='value(credential.access_token)'`)
    * Hint: You can see which account is active with the command `gcloud config config-helper --format='value(configuration.properties.core.account)'`
1. [Google Application Default Credentials](https://developers.google.com/accounts/docs/application-default-credentials).

## Maven Setup

The Cloud Build Artifacts Wagon is an implementation of the
[Maven Wagon API](https://maven.apache.org/wagon/) which allows you to configure Maven to interact
with repositories stored in Cloud Build Artifacts.

To enable the wagon, add the following configuration to the `pom.xml` in your project root:

```xml
    <extensions>
        <extension>
            <groupId>com.google.cloud.buildartifacts</groupId>
            <artifactId>buildartifacts-maven-wagon</artifactId>
            <version>1.1.3</version>
        </extension>
    </extensions>
```

You can then configure repositories by using the "buildartifacts://" prefix:

```xml
  <repositories>
    <repository>
      <id>my-repository</id>
      <url>buildartifacts://maven.pkg.dev/PROJECT_ID/REPOSITORY_ID</url>
      <releases>
        <enabled>true</enabled>
        <updatePolicy>always</updatePolicy>
      </releases>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
    </repository>
  </repositories>
```

Where
* **PROJECT_ID** is the ID of the project.
* **REPOSITORY_ID** is the ID of the repository.

## Gradle Setup

To use Cloud Build Artifacts repositories with gradle, add the following configuration to the
`build.gradle` file in your project.

```gradle
plugins {
  id "com.google.cloud.buildartifacts.gradle-plugin" version "1.1.3"
}

repositories {
    maven {
      url 'buildartifacts://maven.pkg.dev/PROJECT_ID/REPOSITORY_ID'
    }
}

publishing {
    repositories {
        maven {
          url 'buildartifacts://maven.pkg.dev/PROJECT_ID/REPOSITORY_ID'
        }
    }
}
```

Where
* **PROJECT_ID** is the ID of the project.
* **REPOSITORY_ID** is the ID of the repository.

### Alternatives

If you need to use BuildArtifacts repositories inside your `init.gradle` or `settings.gradle`, please instead use `com.google.cloud.buildartifacts.gradle-init-plugin` by adding the following configuration to the `~/.gradle/init.gradle` file.

```gradle
initscript {
  dependencies {
    classpath "com.google.cloud.buildartifacts:buildartifacts-gradle-init-plugin:1.1.4"            
  }
}
apply plugin: com.google.cloud.buildartifacts.gradle.init.plugin.BuildArtifactsGradleInitPlugin

```
