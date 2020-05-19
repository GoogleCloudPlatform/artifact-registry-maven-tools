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

package com.google.cloud.artifactregistry.auth;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// DefaultCredentialProvider fetches credentials from gcloud and falls back to Application Default
// Credentials if that fails.
public class DefaultCredentialProvider implements CredentialProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(GcloudCredentials.class.getName());

  private static String[] SCOPES = {"https://www.googleapis.com/auth/cloud-platform",
      "https://www.googleapis.com/auth/cloud-platform.read-only"};

  @Override
  public Credentials getCredential() throws IOException {
    LOGGER.info("ArtifactRegistry Maven Wagon: Retrieving credentials...");
    Credentials credentials;

    LOGGER.info("Trying Application Default Credentials...");
    try {
      credentials = GoogleCredentials.getApplicationDefault().createScoped(SCOPES);
      LOGGER.info("Using Application Default Credentials.");
      return credentials;
    } catch (IOException ex) {
      LOGGER.info("Failed to retrieve Application Default Credentials: " + ex.getMessage());
    }

    LOGGER.info("Trying to retrieve credentials from gcloud...");
    try {
      credentials = GcloudCredentials.tryCreateGcloudCredentials();
      LOGGER.info("Using credentials retrieved from gcloud.");
      return credentials;
    } catch (IOException ex) {
      LOGGER.info("Failed to retrieve credentials from gcloud: " + ex.getMessage());
    }

    LOGGER.info("ArtifactRegistry Maven Wagon: No credentials could be found.");
    throw new IOException("Failed to find credentials Check info logs for more details.");
  }
}
