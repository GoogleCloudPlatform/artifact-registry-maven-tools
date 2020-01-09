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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.AccessToken;
import com.google.cloud.artifactregistry.auth.CredentialProvider;
import com.google.cloud.artifactregistry.auth.DefaultCredentialProvider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.authentication.DefaultBasicAuthentication;
import org.gradle.plugin.management.PluginManagementSpec;

public class ArtifactRegistryGradlePlugin implements Plugin<Object> {

  static class ArtifactRegistryPasswordCredentials implements PasswordCredentials {
    private String username;
    private String password;

    ArtifactRegistryPasswordCredentials(String username, String password) {
      this.username = username;
      this.password = password;
    }

    @Override
    public String getUsername() {
      return username;
    }

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

  private CredentialProvider credentialProvider = new DefaultCredentialProvider();

  @Override
  public void apply(Object o) {
    if (o instanceof Project) {
      applyProject((Project) o);
    } else if (o instanceof Gradle) {
      applyGradle((Gradle) o);
    } else if (o instanceof Settings) {
      applySettings((Settings) o);
    }
  }

  // The plugin for Gradle will apply CBA repo settings inside settings.gradle and build.gradle.
  private void applyGradle(Gradle gradle) {
    gradle.settingsEvaluated​(s -> modifySettings(s));
    gradle.projectsEvaluated(g -> g.allprojects(p -> modifyProject(p)));
  }

  // The plugin for settings will apply CBA repo settings inside settings.gradle and build.gradle.
  private void applySettings(Settings settings) {
    applyGradle(settings.getGradle());
  }

  // The plugin for projects will only apply CBA repo settings inside build.gradle.
  private void applyProject(Project project) {
    project.afterEvaluate(p -> modifyProject(p));
  }

  private void modifyProject(Project p) {
    p.getRepositories().all(this::configureArtifactRegistryRepository);
    final PublishingExtension publishingExtension = p.getExtensions().findByType(PublishingExtension.class);
    if (publishingExtension != null) {
      publishingExtension.getRepositories().all(this::configureArtifactRegistryRepository);
    }
  }

  private void modifySettings(Settings s) {
    final PluginManagementSpec pluginManagement = s.getPluginManagement();
    if (pluginManagement != null) {
      pluginManagement.getRepositories().all(this::configureArtifactRegistryRepository);
    }
  }

  private void configureArtifactRegistryRepository(ArtifactRepository repo)
      throws ProjectConfigurationException, UncheckedIOException
      {
        if (!(repo instanceof DefaultMavenArtifactRepository)) {
          return;
        }
        final DefaultMavenArtifactRepository cbaRepo = (DefaultMavenArtifactRepository) repo;
        final URI u = cbaRepo.getUrl();
        if (u != null && u.getScheme() != null && u.getScheme().equals("artifactregistry")) {
          try {
            cbaRepo.setUrl(new URI("https", u.getHost(), u.getPath(), u.getFragment()));
          } catch (URISyntaxException e) {
            throw new ProjectConfigurationException(String.format("Invalid repository URL %s", u.toString()), e);
          }

          if (cbaRepo.getConfiguredCredentials() == null) {
            try {
              GoogleCredentials credentials = (GoogleCredentials)credentialProvider.getCredential();
              credentials.refreshIfExpired();
              AccessToken accessToken = credentials.getAccessToken();
              String token = accessToken.getTokenValue();
              ArtifactRegistryPasswordCredentials crd = new ArtifactRegistryPasswordCredentials("oauth2accesstoken", token);
              cbaRepo.setConfiguredCredentials(crd);
              cbaRepo.authentication(authenticationContainer -> authenticationContainer.add(new DefaultBasicAuthentication("basic")));
            } catch (IOException e) {
              throw new UncheckedIOException("Failed to get access token from gcloud or Application Default Credentials", e);
            }
          }
        }
      }
}
