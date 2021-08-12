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

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.time.Instant;
import java.util.Date;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class ArtifactRegistryRequestInitializerTest {

  @Test
  public void testInitialize() throws Exception {
    Credentials creds = GoogleCredentials.create(new AccessToken("test-access-token", Date.from(
        Instant.now().plusSeconds(1000))));
    ArtifactRegistryRequestInitializer initializer = new ArtifactRegistryRequestInitializer(creds, 100);
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(new MockLowLevelHttpResponse().setContent("test content"))
        .build();
    GenericUrl url = new GenericUrl("https://www.example.com");
    HttpRequestFactory requestFactory = transport.createRequestFactory(initializer);
    HttpRequest request = requestFactory.buildHeadRequest(url);
    Assert.assertEquals(request.getReadTimeout(), 100);
    Assert.assertEquals(request.getHeaders().getFirstHeaderStringValue("Authorization"), "Bearer test-access-token");
  }
}