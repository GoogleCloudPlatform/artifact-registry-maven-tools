package com.google.cloud.buildartifacts.wagon;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.buildartifacts.auth.CredentialProvider;
import com.google.common.io.Files;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Date;
import org.apache.maven.wagon.FileTestUtils;
import java.io.File;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.repository.Repository;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildArtifactsWagonTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private final String REPO_URL = "buildartifacts://maven.pkg.dev/my-project/my-repo";

  @Test
  public void testAnonymousGet() throws Exception {
    MockHttpTransport transport = transportWithResponse("test content");
    BuildArtifactsWagon wagon = new BuildArtifactsWagon();
    wagon.setCredentialProvider(new FailingCredentialProvider(new IOException("failed to get access token")));
    wagon.setHttpTransportFactory(() -> transport);
    wagon.connect(new Repository("my-repo", REPO_URL));
    File f = FileTestUtils.createUniqueFile("my/artifact/dir", "test");
    wagon.get("my/resource", f);
    assertFileContains(f, "test content");
  }

  @Test
  public void testGetPermissionDenied() throws Exception {
    MockHttpTransport transport = failingTransportWithStatus(
        HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
    BuildArtifactsWagon wagon = new BuildArtifactsWagon();
    wagon.setCredentialProvider(new FailingCredentialProvider(new IOException("failed to get access token")));
    wagon.setHttpTransportFactory(() -> transport);
    wagon.connect(new Repository("my-repo", REPO_URL));
    File f = FileTestUtils.createUniqueFile("my/artifact/dir", "test");
    expectedException.expect(AuthorizationException.class);
    expectedException.expectMessage(CoreMatchers
        .containsString("Permission denied on remote repository (or it may not exist)"));
    expectedException.expectMessage(
        CoreMatchers.containsString("The request had no credentials"));
    wagon.get("my/resource", f);
  }

  @Test
  public void testNotFoundReturnsMessage() throws Exception {
    MockHttpTransport transport = failingTransportWithStatus(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
    BuildArtifactsWagon wagon = new BuildArtifactsWagon();
    wagon.setCredentialProvider(new FailingCredentialProvider(new IOException("failed to get access token")));
    wagon.setHttpTransportFactory(() -> transport);
    wagon.connect(new Repository("my-repo", REPO_URL));
    File f = FileTestUtils.createUniqueFile("my/artifact/dir", "test");
    expectedException.expect(ResourceDoesNotExistException.class);
    expectedException.expectMessage(CoreMatchers.containsString("remote resource does not exist"));
    wagon.get("my/resource", f);
  }

  @Test
  public void testAuthenticatedGet() throws Exception {
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(new MockLowLevelHttpResponse().setContent("test content"))
        .build();
    BuildArtifactsWagon wagon = new BuildArtifactsWagon();
    wagon.setCredentialProvider(() -> GoogleCredentials.create(new AccessToken("test-access-token", Date.from(
        Instant.now().plusSeconds(1000)))));
    wagon.setHttpTransportFactory(() -> transport);
    wagon.connect(new Repository("my-repo", REPO_URL));
    File f = FileTestUtils.createUniqueFile("my/artifact/dir", "test");
    wagon.get("my/resource", f);
    assertFileContains(f, "test content");
    String authHeader = transport.getLowLevelHttpRequest().getFirstHeaderValue("Authorization");
    Assert.assertEquals("Bearer test-access-token", authHeader);
    Assert.assertEquals("https://maven.pkg.dev/my-project/my-repo/my/resource",
        transport.getLowLevelHttpRequest().getUrl());
  }

  @Test
  public void testAuthenticatedPut() throws Exception {
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(new MockLowLevelHttpResponse()).build();
    BuildArtifactsWagon wagon = new BuildArtifactsWagon();
    wagon.setCredentialProvider(() -> GoogleCredentials.create(new AccessToken("test-access-token", Date.from(
        Instant.now().plusSeconds(1000)))));
    wagon.setHttpTransportFactory(() -> transport);
    wagon.connect(new Repository("my-repo", REPO_URL));
    File f = FileTestUtils.createUniqueFile("my/artifact/dir", "test");
    Files.asCharSink(f, Charset.defaultCharset()).write("test content");
    wagon.put(f, "my/resource");
    String authHeader = transport.getLowLevelHttpRequest().getFirstHeaderValue("Authorization");
    Assert.assertEquals("Bearer test-access-token", authHeader);
    Assert.assertEquals("test content", transport.getLowLevelHttpRequest().getContentAsString());
    Assert.assertEquals("https://maven.pkg.dev/my-project/my-repo/my/resource",
        transport.getLowLevelHttpRequest().getUrl());
  }

  @Test
  public void testPutPermissionDenied() throws Exception {
    MockHttpTransport transport = failingTransportWithStatus(
        HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
    BuildArtifactsWagon wagon = new BuildArtifactsWagon();
    wagon.setCredentialProvider(new FailingCredentialProvider(new IOException("failed to get access token")));
    wagon.setHttpTransportFactory(() -> transport);
    wagon.connect(new Repository("my-repo", REPO_URL));
    File f = FileTestUtils.createUniqueFile("my/artifact/dir", "test");
    Files.asCharSink(f, Charset.defaultCharset()).write("test content");
    expectedException.expect(AuthorizationException.class);
    expectedException.expectMessage(CoreMatchers
        .containsString("Permission denied on remote repository (or it may not exist)"));
    expectedException.expectMessage(
        CoreMatchers.containsString("The request had no credentials"));
    wagon.put(f, "my/resource");
  }

  private void assertFileContains(File f, String wantContent) throws IOException {
    String content = Files.asCharSource(f, Charset.defaultCharset()).read();
    Assert.assertEquals(wantContent, content);
  }

  private MockHttpTransport transportWithResponse(String content) {
    return new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(new MockLowLevelHttpResponse().setContent(content))
        .build();
  }

  private MockHttpTransport failingTransport(IOException e) {
    return new MockHttpTransport.Builder().setLowLevelHttpRequest(new MockLowLevelHttpRequest() {
      @Override
      public LowLevelHttpResponse execute() throws IOException {
        throw e;
      }
    }).build();
  }

  private MockHttpTransport failingTransportWithStatus(int statusCode) {
    return failingTransport(
        new HttpResponseException.Builder(statusCode, "", new HttpHeaders()).build());
  }

  private static class FailingCredentialProvider implements CredentialProvider {
    private final IOException exception;

    public FailingCredentialProvider(IOException e) {
      this.exception = e;
    }

    @Override
    public Credentials getCredential() throws IOException {
      throw exception;
    }
  }

}