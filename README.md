# Cloud Build Artifacts Wagon

The Cloud Build Artifacts Wagon is an implementation of the
[Maven Wagon API](https://maven.apache.org/wagon/) which allows you to configure Maven to interact
with Maven repositories stored in Cloud Build Artifacts.

The wagon will authenticate to Cloud Build Artifacts using credentials from the environment. It will
search for credentials in the following order:
1. From the `gcloud` SDK. (i.e., the access token printed via `gcloud config config-helper --format='value(credential.access_token)'`)
    * Hint: You can see which account is active with the command `gcloud config config-helper --format='value(configuration.properties.core.account)'`
1. [Google Application Default Credentials](https://developers.google.com/accounts/docs/application-default-credentials).


To enable the wagon, add the following configuration to the Maven pom.xml in your project root:

```xml
    <extensions>
        <extension>
            <groupId>com.google.cloud.buildartifacts</groupId>
            <artifactId>buildartifacts-maven-wagon</artifactId>
            <version>1.0.0</version>
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
