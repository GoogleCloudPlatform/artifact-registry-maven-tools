# Cloud Build Artifacts Wagon

The Cloud Build Artifacts Wagon is an implementation of the
[Maven Wagon API](https://maven.apache.org/wagon/) which allows you to configure Maven to interact
with Maven repositories stored in Cloud Build Artifacts.

The wagon will authenticate to Cloud Build Artifacts using
[Google Application Default Credentials](https://developers.google.com/accounts/docs/application-default-credentials).


To enable the wagon, add the following configuration to the Maven pom.xml in your project root:

```xml
    <extensions>
        <extension>
            <groupId>com.google.cloud.buildartifacts</groupId>
            <artifactId>buildartifacts-wagon</artifactId>
            <version>1.0-0</version>
        </extension>
    </extensions>
```

You can then configure repositories using the repository's resource name.

```xml
  <repositories>
    <repository>
      <id>my-repository</id>
      <url>buildartifacts://project/PROJECT_ID/repositories/REPOSITORY_ID</url>
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
