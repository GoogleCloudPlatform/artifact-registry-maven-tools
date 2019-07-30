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
package com.google.cloud.buildartifacts.auth;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.GenericData;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Logger;

public class GcloudCredentials extends GoogleCredentials {

  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  private static final Logger LOGGER = Logger.getLogger(GcloudCredentials.class.getName());

  private static final String KEY_ACCESS_TOKEN = "access_token";
  private static final String KEY_TOKEN_EXPIRY = "token_expiry";

  private GcloudCredentials(AccessToken initialToken) {
    super(initialToken);
  }

  @Override
  public AccessToken refreshAccessToken() throws IOException {
    return getGcloudAccessToken();
  }

  /**
   * Tries to get credentials from gcloud. Returns null if credentials are not available.
   */
  public static GcloudCredentials tryCreateGcloudCredentials() {
    try {
      return new GcloudCredentials(getGcloudAccessToken());
    } catch (IOException e) {
      LOGGER.info("Failed to get credentials from gcloud: " + e.getMessage());
      return null;
    }
  }

  private static AccessToken getGcloudAccessToken() throws IOException {
    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.command("gcloud", "config", "config-helper", "--format=json(credential)");
    Process process = processBuilder.start();
    try {
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IOException("gcloud exited with status " + exitCode);
      }
      GenericData result = JSON_FACTORY
          .fromInputStream(process.getInputStream(), GenericData.class);
      Map credential = (Map) result.get("credential");
      if (credential == null) {
        throw new IOException("No credential returned from gcloud");
      }
      if (!credential.containsKey(KEY_ACCESS_TOKEN) || !credential.containsKey(KEY_TOKEN_EXPIRY)) {
        throw new IOException("Malformed response from gcloud");
      }
      DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
      df.setTimeZone(TimeZone.getTimeZone("UTC"));
      Date expiry = df.parse((String) credential.get(KEY_TOKEN_EXPIRY));
      return new AccessToken((String) credential.get(KEY_ACCESS_TOKEN), expiry);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(e);
    } catch (ParseException e) {
      throw new IOException("Failed to parse timestamp from gcloud output", e);
    }
  }

}
