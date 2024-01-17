# Artifact Registry Maven Tools

This repository contains tools to help with interacting with Maven repositories hosted on Artifact Registry.

## Authentication

Requests to Artifact Registry will be authenticated using credentials from the environment. The
tools described below search the environment for credentials in the following order:
1. [Google Application Default Credentials](https://developers.google.com/accounts/docs/application-default-credentials).
    * Note: It is possible to set Application Default Credentials for a user account via `gcloud auth login --update-adc` or `gcloud auth application-default login`
1. From the `gcloud` SDK. (i.e., the access token printed via `gcloud config config-helper --format='value(credential.access_token)'`)
    * Hint: You can see which account is active with the command `gcloud config config-helper --format='value(configuration.properties.core.account)'`

## Maven Setup

The Artifact Registry Wagon is an implementation of the
[Maven Wagon API](https://maven.apache.org/wagon/) which allows you to configure Maven to interact
with repositories stored in Artifact Registry.

To enable the wagon, add the following configuration to the `pom.xml` in your project root:

```xml
    <extensions>
        <extension>
            <groupId>com.google.cloud.artifactregistry</groupId>
            <artifactId>artifactregistry-maven-wagon</artifactId>
            <version>2.2.1</version>
        </extension>
    </extensions>
```

You can then configure repositories by using the "artifactregistry://" prefix.
In this example the repository is in 'us-west1', you should use the correct
location for your repository.

```xml
  <repositories>
    <repository>
      <id>my-repository</id>
      <url>artifactregistry://us-west1-maven.pkg.dev/PROJECT_ID/REPOSITORY_ID</url>
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

### Parent Pom Usage

To use a parent pom definition hosted at an Artifact Registry repository, some extra configuration is needed.
These can be found
at [Authenticating with a credential helper, step 6](https://cloud.google.com/artifact-registry/docs/java/authentication#auth-helper)

## Gradle Setup

To use Artifact Registry repositories with gradle, add the following configuration to the
`build.gradle` file in your project. In this example the repository is in 'us-west1',
you should use the correct location for your repository.

```gradle
plugins {
  id "com.google.cloud.artifactregistry.gradle-plugin" version "2.2.1"
}

repositories {
    maven {
      url 'artifactregistry://us-west1-maven.pkg.dev/PROJECT_ID/REPOSITORY_ID'
    }
}

publishing {
    repositories {
        maven {
          url 'artifactregistry://us-west1-maven.pkg.dev/PROJECT_ID/REPOSITORY_ID'
        }
    }
}
```

Where
* **PROJECT_ID** is the ID of the project.
* **REPOSITORY_ID** is the ID of the repository.

### Alternatives

If you need to use Artifact Registry repositories inside your `init.gradle` or `settings.gradle`, the plugin can also be used inside `init.gradle` or `settings.gradle` files.

* To use plugin inside `init.gradle` file:

```gradle
initscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "gradle.plugin.com.google.cloud.artifactregistry:artifactregistry-gradle-plugin:2.2.1"
  }
}

apply plugin: com.google.cloud.artifactregistry.gradle.plugin.ArtifactRegistryGradlePlugin
```

* To use plugin inside `settings.gradle` file:

```gradle
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "gradle.plugin.com.google.cloud.artifactregistry:artifactregistry-gradle-plugin:2.2.1"
  }
}

apply plugin: "com.google.cloud.artifactregistry.gradle-plugin"
```
