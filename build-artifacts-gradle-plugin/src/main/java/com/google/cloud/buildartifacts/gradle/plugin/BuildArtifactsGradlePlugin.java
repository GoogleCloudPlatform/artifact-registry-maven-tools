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
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class BuildArtifactsGradlePlugin implements Plugin<Project> {

	private CredentialProvider credentialProvider = new DefaultCredentialProvider();

    //@Override
    public void apply(Project project) {
        try {
            GoogleCredentials credentials = (GoogleCredentials)credentialProvider.getCredential();
            AccessToken accessToken = credentials.getAccessToken();
            String token = accessToken.getTokenValue();
            project.getExtensions().getExtraProperties().set("oauth2accesstoken", token);
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }
}