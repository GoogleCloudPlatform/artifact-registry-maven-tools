/*
 * Copyright 2021 Google LLC
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

package com.google.cloud.artifactregistry.wagon;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import java.io.IOException;

/**
 * ArtifactRegistryRequestInitializer initializes outbound requests with the provided credentials and read timeout. 
 */
public class ArtifactRegistryRequestInitializer implements HttpRequestInitializer {

  private HttpCredentialsAdapter credentialsAdapter;
  private int readTimeout;

  ArtifactRegistryRequestInitializer(Credentials credentials, int readTimeout) {
    this.credentialsAdapter = new HttpCredentialsAdapter(credentials);
    this.readTimeout = readTimeout;
  }

  @Override
  public void initialize(HttpRequest request) throws IOException {
    this.credentialsAdapter.initialize(request);
    request.setReadTimeout(readTimeout);
  }
}