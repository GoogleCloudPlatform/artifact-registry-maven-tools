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
import com.google.cloud.buildartifacts.wagon.DefaultCredentialProvider;
import com.google.cloud.buildartifacts.wagon.CredentialProvider;
import java.io.IOException;
import java.net.URI;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;

public class BuildArtifactsGradlePlugin implements Plugin<Project> {

	private CredentialProvider credentialProvider = new DefaultCredentialProvider();

    @Override
    public void apply(Project project) {
        project.afterEvaluate(p -> {
            project.getRepositories().all(this::configureBuildArtifactsRepositories);
            final PublishingExtension publishingExtension = project.getExtensions().findByType(PublishingExtension.class);
            if (publishingExtension != null) {
                publishingExtension.getRepositories().all(this::configureBuildArtifactsRepositories);
            }
        });
    }

    public void configureBuildArtifactsRepositories(ArtifactRepository repo)
    {
        try {
            GoogleCredentials credentials = (GoogleCredentials)credentialProvider.getCredential();
            AccessToken accessToken = credentials.getAccessToken();
            String token = accessToken.getTokenValue();
            
            if (!(repo instanceof DefaultMavenArtifactRepository)) {
                return;
            }
            final DefaultMavenArtifactRepository cbaRepo = (DefaultMavenArtifactRepository) repo;
            final URI u = cbaRepo.getUrl(); 
            if (u != null && u.getScheme() != null && u.getScheme().equals("buildartifacts")) {
                String httpURL = u.toString().replaceFirst("buildartifacts://", "https://");
                cbaRepo.setUrl(URI.create(httpURL));
                BuildArtifactsPasswordCredentials crd = new BuildArtifactsPasswordCredentials("oauth2accesstoken", token);
                cbaRepo.setConfiguredCredentials((Credentials)crd);
            }
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }
}

class BuildArtifactsPasswordCredentials implements PasswordCredentials {
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