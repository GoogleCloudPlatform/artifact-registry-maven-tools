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

package com.google.cloud.buildartifacts.gradle.plugin;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.AccessToken;
import com.google.cloud.buildartifacts.auth.CredentialProvider;
import com.google.cloud.buildartifacts.auth.DefaultCredentialProvider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.Action;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.authentication.DefaultBasicAuthentication;
import org.gradle.plugin.management.PluginManagementSpec;

public class BuildArtifactsGradlePlugin implements Plugin<Object> {

  static class BuildArtifactsPasswordCredentials implements PasswordCredentials {
    private String username;
    private String password;

    public BuildArtifactsPasswordCredentials(String username, String password) {
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

  public void apply(Object o) {
    if (o instanceof Project) {
      applyProject((Project) o);
    } else if (o instanceof Gradle) {
      applyGradle((Gradle) o);
    } else {
      throw new IllegalArgumentException(o.getClass().getName());
    }
  }

  public void applyProject(Project project) {
    project.afterEvaluate(p -> {
      project.getRepositories().all(this::configureBuildArtifactsRepositories);
      final PublishingExtension publishingExtension = project.getExtensions().findByType(PublishingExtension.class);
      if (publishingExtension != null) {
        publishingExtension.getRepositories().all(this::configureBuildArtifactsRepositories);
      }
    });
  }

  public void applyGradle(Gradle gradle) {
    gradle.settingsEvaluated​(s -> {
      final PluginManagementSpec pluginManagement = s.getPluginManagement();
      if (pluginManagement != null) {
        pluginManagement.getRepositories().all(this::configureBuildArtifactsRepositories);
      }
    });

    gradle.projectsEvaluated(g -> {
      g.allprojects(p -> {
        p.getRepositories().all(this::configureBuildArtifactsRepositories);
        final PublishingExtension publishingExtension = p.getExtensions().findByType(PublishingExtension.class);
        if (publishingExtension != null) {
          publishingExtension.getRepositories().all(this::configureBuildArtifactsRepositories);
        }
      });
    });
  }

  public void configureBuildArtifactsRepositories(ArtifactRepository repo)
      throws ProjectConfigurationException, UncheckedIOException
      {
        if (!(repo instanceof DefaultMavenArtifactRepository)) {
          return;
        }
        final DefaultMavenArtifactRepository cbaRepo = (DefaultMavenArtifactRepository) repo;
        final URI u = cbaRepo.getUrl();
        if (u != null && u.getScheme() != null && u.getScheme().equals("buildartifacts")) {
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
              BuildArtifactsPasswordCredentials crd = new BuildArtifactsPasswordCredentials("oauth2accesstoken", token);
              cbaRepo.setConfiguredCredentials((Credentials)crd);
              cbaRepo.authentication(new Action<AuthenticationContainer>() {
                @Override
                public void execute(AuthenticationContainer authenticationContainer) {
                  authenticationContainer.add(new DefaultBasicAuthentication("basic"));
                }
              });
            } catch (IOException e) {
              throw new UncheckedIOException("Failed to get access token from gcloud or Application Default Credentials", e);
            }
          }
        }
      }
}
