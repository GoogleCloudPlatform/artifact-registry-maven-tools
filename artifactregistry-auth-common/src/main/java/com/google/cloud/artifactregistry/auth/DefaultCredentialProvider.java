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
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// DefaultCredentialProvider fetches credentials from gcloud and falls back to Application Default
// Credentials if that fails.
public final class DefaultCredentialProvider implements CredentialProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(GcloudCredentials.class.getName());

  private static String[] SCOPES = {"https://www.googleapis.com/auth/cloud-platform",
      "https://www.googleapis.com/auth/cloud-platform.read-only"};

  private GoogleCredentials cachedCredentials;

  // Singleton instance
  private final static DefaultCredentialProvider defaultInstance = new DefaultCredentialProvider();
  public static DefaultCredentialProvider getInstance() {
    return defaultInstance;
  }

  public static long LAST_REFRESH_TIME_MS = 0;
  public static final long REFESH_INTERVAL_MS = Duration.ofSeconds(10).toMillis();

  // Private constructor so that they must use the singleton.
  private DefaultCredentialProvider(){}

  public Credentials getCredential() throws IOException {
    synchronized (this) {
      if (cachedCredentials == null) {
        LOGGER.info("Initializing Credentials...");
        cachedCredentials = makeGoogleCredentials();
      }
      refreshIfNeeded();
      return cachedCredentials;
    }
  }

  public void refreshIfNeeded() throws IOException {
    long now = Instant.now().toEpochMilli();
    if (cachedCredentials != null && now > LAST_REFRESH_TIME_MS + REFESH_INTERVAL_MS) {
      LOGGER.info("Refreshing Credentials...");
      cachedCredentials.refreshIfExpired();
      LAST_REFRESH_TIME_MS = now;
    }
  }

  public GoogleCredentials makeGoogleCredentials() throws IOException {
    LOGGER.debug("ArtifactRegistry: Retrieving credentials...");
    GoogleCredentials credentials;

    LOGGER.debug("Trying Application Default Credentials...");
    try {
      credentials = GoogleCredentials.getApplicationDefault().createScoped(SCOPES);
      LOGGER.info("Using Application Default Credentials.");
      return credentials;
    } catch (IOException ex) {
      LOGGER.info("Application Default Credentials unavailable.");
      LOGGER.debug("Failed to retrieve Application Default Credentials: " + ex.getMessage());
    }

    LOGGER.debug("Trying gcloud credentials...");
    try {
      credentials = GcloudCredentials.tryCreateGcloudCredentials();
      LOGGER.info("Using credentials retrieved from gcloud.");
      return credentials;
    } catch (IOException ex) {
      LOGGER.info("Failed to retrieve credentials from gcloud: " + ex.getMessage());
    }

    LOGGER.info("ArtifactRegistry: No credentials could be found.");
    throw new IOException("Failed to find credentials Check debug logs for more details.");
  }
}
