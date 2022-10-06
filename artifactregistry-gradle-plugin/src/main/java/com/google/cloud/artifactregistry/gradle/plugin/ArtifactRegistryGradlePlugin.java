/*
 * Copyright 2019 Google LLC
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

package com.google.cloud.artifactregistry.gradle.plugin;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.artifactregistry.auth.CredentialProvider;
import com.google.cloud.artifactregistry.auth.DefaultCredentialProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import javax.annotation.Nullable;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.tasks.Input;
import org.gradle.internal.authentication.DefaultBasicAuthentication;
import org.gradle.plugin.management.PluginManagementSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArtifactRegistryGradlePlugin implements Plugin<Object> {

  private static final Logger logger = LoggerFactory.getLogger(ArtifactRegistryGradlePlugin.class);

  static class ArtifactRegistryPasswordCredentials implements PasswordCredentials {
    private String username;
    private String password;

    ArtifactRegistryPasswordCredentials(String username, String password) {
      this.username = username;
      this.password = password;
    }

    @Input
    @Override
    public String getUsername() {
      return username;
    }

    @Input
    @Override
    public String getPassword() {
      return password;
    }

    @Override
    public void setUsername(String username) {
      this.username = username;
    }

    @Override
    public void setPassword(String password) {
      this.password = password;
    }
  }

  private final CredentialProvider credentialProvider = DefaultCredentialProvider.getInstance();

  @Override
  public void apply(Object o) {
    ArtifactRegistryPasswordCredentials crd = null;
    try {
      GoogleCredentials credentials = (GoogleCredentials)credentialProvider.getCredential();
      credentials.refreshIfExpired();
      AccessToken accessToken = credentials.getAccessToken();
      String token = accessToken.getTokenValue();
      crd = new ArtifactRegistryPasswordCredentials("oauth2accesstoken", token);
    } catch (IOException e) {
      logger.info("Failed to get access token from gcloud or Application Default Credentials", e);
    }

    if (o instanceof Project) {
      applyProject((Project) o, crd);
    } else if (o instanceof Gradle) {
      applyGradle((Gradle) o, crd);
    } else if (o instanceof Settings) {
      applySettings((Settings) o, crd);
    }
  }

  // The plugin for Gradle will apply Artifact Registry repo settings inside settings.gradle and build.gradle.
  private void applyGradle(Gradle gradle, @Nullable ArtifactRegistryPasswordCredentials crd) {
    gradle.settingsEvaluated(s -> modifySettings(s, crd));
    gradle.projectsLoaded(g -> g.allprojects(p -> modifyProjectBuildscript(p, crd)));
    gradle.projectsEvaluated(g -> g.allprojects(p -> modifyProject(p, crd)));
  }

  // The plugin for settings will apply Artifact Registry repo settings inside settings.gradle and build.gradle.
  private void applySettings(Settings settings, @Nullable ArtifactRegistryPasswordCredentials crd) {
    applyGradle(settings.getGradle(), crd);
  }

  // The plugin for projects will only apply Artifact Registry repo settings inside build.gradle.
  private void applyProject(Project project, @Nullable ArtifactRegistryPasswordCredentials crd) {
    project.afterEvaluate(p -> modifyProject(p, crd));
  }

  private void modifyProject(Project p, @Nullable ArtifactRegistryPasswordCredentials crd) {
    p.getRepositories().forEach(r -> configureArtifactRegistryRepository(r, crd));
    final PublishingExtension publishingExtension = p.getExtensions().findByType(PublishingExtension.class);
    if (publishingExtension != null) {
      publishingExtension.getRepositories().forEach(r -> configureArtifactRegistryRepository(r, crd));
    }
  }

  // Not sure this knows which repositories already exist, so register a callback to modify the repos
  // as they are added to the buildscript
  private void modifyProjectBuildscript(Project p, @Nullable ArtifactRegistryPasswordCredentials crd) {
    final ScriptHandler buildscript = p.getBuildscript();
    if (buildscript != null) {
      buildscript.getRepositories().whenObjectAdded(r -> configureArtifactRegistryRepository(r, crd));
    }
  }

  private void modifySettings(Settings s, @Nullable ArtifactRegistryPasswordCredentials crd) {
    final PluginManagementSpec pluginManagement = s.getPluginManagement();
    if (pluginManagement != null) {
      pluginManagement.getRepositories().forEach(r -> configureArtifactRegistryRepository(r, crd));
    }
  }

  private void configureArtifactRegistryRepository(
      ArtifactRepository repo, @Nullable ArtifactRegistryPasswordCredentials crd)
      throws ProjectConfigurationException, UncheckedIOException {
    if (!(repo instanceof DefaultMavenArtifactRepository)) {
      return;
    }
    final DefaultMavenArtifactRepository arRepo = (DefaultMavenArtifactRepository) repo;
    final URI u = arRepo.getUrl();
    if (u != null && u.getScheme() != null && u.getScheme().equals("artifactregistry")) {
      try {
        arRepo.setUrl(new URI("https", u.getHost(), u.getPath(), u.getFragment()));
      } catch (URISyntaxException e) {
        throw new ProjectConfigurationException(
            String.format("Invalid repository URL %s", u.toString()), e);
      }

      if (crd != null && shouldStoreCredentials(arRepo)) {
        arRepo.setConfiguredCredentials(crd);
        arRepo.authentication(authenticationContainer -> authenticationContainer
            .add(new DefaultBasicAuthentication("basic")));
      }
    }
  }

  // This is a shim to work around an incompatible API change in Gradle 6.6. Prior to that,
  // AbstractAuthenticationSupportedRepository#getConfiguredCredentials() returned a (possibly null)
  // Credentials object. In 6.6, it changed to return Property<Credentials>.
  //
  // Compiling this plugin against Gradle 6.5 results in a NoSuchMethodException if you run it under
  // Gradle 6.6. The same thing happens if you compile against 6.6 and run it in 6.5.
  //
  // So we have to use reflection to inspect the return type.
  private static boolean shouldStoreCredentials(DefaultMavenArtifactRepository repo) {

    try {
      Method getConfiguredCredentials = DefaultMavenArtifactRepository.class
          .getMethod("getConfiguredCredentials");

      // This is for Gradle < 6.6. Once we no longer support versions of Gradle before 6.6
      if (getConfiguredCredentials.getReturnType().equals(Credentials.class)) {
        Credentials existingCredentials = (Credentials) getConfiguredCredentials.invoke(repo);
        return existingCredentials == null;
      } else if (getConfiguredCredentials.getReturnType().equals(Property.class)) {
        Property<?> existingCredentials = (Property<?>) getConfiguredCredentials.invoke(repo);
        return !existingCredentials.isPresent();
      } else {
        logger.warn("Error determining Gradle credentials API; expect authentication errors");
        return false;
      }
    } catch (ReflectiveOperationException e) {
      logger.warn("Error determining Gradle credentials API; expect authentication errors", e);
      return false;
    }
  }
}
