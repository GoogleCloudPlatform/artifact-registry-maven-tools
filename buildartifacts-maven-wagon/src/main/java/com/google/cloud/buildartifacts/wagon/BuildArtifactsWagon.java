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

package com.google.cloud.buildartifacts.wagon;


import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.http.HttpTransportFactory;
import com.google.cloud.buildartifacts.auth.CredentialProvider;
import com.google.cloud.buildartifacts.auth.DefaultCredentialProvider;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.maven.wagon.AbstractWagon;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.repository.Repository;
import org.apache.maven.wagon.resource.Resource;


public final class BuildArtifactsWagon extends AbstractWagon {

  private GoogleRepository googleRepository;
  private HttpRequestFactory requestFactory;
  private boolean hasCredentials;
  private HttpTransportFactory httpTransportFactory = NetHttpTransport::new;
  private CredentialProvider credentialProvider = new DefaultCredentialProvider();
  private Credentials credentials;

  private InputStream getInputStream(Resource resource)
      throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
    try {
      GenericUrl url = googleRepository.constructURL(resource.getName());
      HttpRequest request = requestFactory.buildGetRequest(url);
      HttpResponse response = request.execute();
      return response.getContent();
    } catch (HttpResponseException e) {
      rethrowAuthorizationException(e);
      rethrowNotFoundException(e);
      throw new TransferFailedException("Received an error from the remote server.", e);
    } catch (IOException e) {
      throw new TransferFailedException("Failed to send request to remote server.", e);
    }
  }

  @Override
  protected void openConnectionInternal() throws ConnectionException, AuthenticationException {
    HttpRequestInitializer requestInitializer;
    HttpTransport httpTransport = httpTransportFactory.create();
    try {
      credentials = credentialProvider.getCredential();
      requestInitializer = new HttpCredentialsAdapter(credentials);
      requestFactory = httpTransport.createRequestFactory(requestInitializer);
      hasCredentials = true;
    } catch (IOException e) {
      requestFactory = httpTransport.createRequestFactory();
    }
    googleRepository = new GoogleRepository(repository);
  }

  @Override
  protected void closeConnection() throws ConnectionException {

  }

  @Override
  public void get(String resourceName, File destination)
      throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
    getIfNewer(resourceName, destination, 0);
  }

  @Override
  public boolean resourceExists(String resource)
      throws TransferFailedException, AuthorizationException {
    try {
      GenericUrl url = googleRepository.constructURL(resource);
      HttpRequest request = requestFactory.buildHeadRequest(url);
      return request.execute().isSuccessStatusCode();
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
        return false;
      }
      rethrowAuthorizationException(e);
      throw new TransferFailedException("Received an error from the remote server.", e);
    } catch (IOException e) {
      throw new TransferFailedException("Failed to send request to remote server.", e);
    }
  }

  @Override
  public boolean getIfNewer(String resourceName, File destination, long timestamp)
      throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
    Resource resource = new Resource(resourceName);
    this.fireGetInitiated(resource, destination);
    try {
      this.fireGetStarted(resource, destination);
      InputStream input = getInputStream(resource);
      this.getTransfer(resource, destination, input);
      this.fireGetCompleted(resource, destination);
    } catch (Exception e) {
      this.fireTransferError(resource, e, TransferEvent.REQUEST_GET);
      throw e;
    }
    return true;
  }

  public void setHttpTransportFactory(HttpTransportFactory httpTransportFactory) {
    this.httpTransportFactory = httpTransportFactory;
  }

  public void setCredentialProvider(CredentialProvider provider) {
    this.credentialProvider = provider;
  }

  private void handlePutRequest(File source, Resource resource, GenericUrl url)
      throws AuthorizationException, ResourceDoesNotExistException, TransferFailedException {
    try {
      HttpRequest request = requestFactory.buildPutRequest(url, new HttpContent() {
        @Override
        public long getLength() throws IOException {
          return source.length();
        }

        @Override
        public String getType() {
          return null;
        }

        @Override
        public boolean retrySupported() {
          return true;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
          try {
            putTransfer(resource, source, out, false);
          } catch (TransferFailedException | AuthorizationException | ResourceDoesNotExistException e) {
            throw new FileTransferException(e);
          }
        }
      });
      request.execute();
    } catch (HttpResponseException e) {
      rethrowAuthorizationException(e);
      rethrowNotFoundException(e);
      throw new TransferFailedException("Received an error from the remote server.", e);
    } catch (FileTransferException e) {
      Throwable cause = e.getCause();
      if (cause instanceof TransferFailedException) {
        throw (TransferFailedException) cause;
      } else if (cause instanceof AuthorizationException) {
        throw (AuthorizationException) cause;
      } else if (cause instanceof ResourceDoesNotExistException) {
        throw (ResourceDoesNotExistException) cause;
      }
      throw new TransferFailedException("Error uploading file.", cause);
    } catch (IOException e) {
      throw new TransferFailedException("Failed to send request to remote server.", e);
    }

  }

  @Override
  public void put(File source, String destination)
      throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
    Resource resource = new Resource(destination);
    this.firePutInitiated(resource, source);
    resource.setContentLength(source.length());
    resource.setLastModified(source.lastModified());
    GenericUrl url = googleRepository.constructURL(resource.getName());
    this.firePutStarted(resource, source);
    handlePutRequest(source, resource, url);
    this.firePutCompleted(resource, source);
  }

  private void rethrowAuthorizationException(HttpResponseException e)
      throws AuthorizationException {
    if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_FORBIDDEN
        || e.getStatusCode() == HttpStatusCodes.STATUS_CODE_UNAUTHORIZED) {
      String errorMessage = "Permission denied on remote repository (or it may not exist). ";
      if (!hasCredentials) {
        errorMessage += "The request had no credentials because none were available "
            + "from the environment. Ensure that either 1) You are logged into gcloud or 2) "
            + "Application default credentials are setup (see "
            + "https://developers.google.com/accounts/docs/application-default-credentials for "
            + "more information).";
      }
      throw new AuthorizationException(errorMessage, e);
    }
  }

  private void rethrowNotFoundException(HttpResponseException e)
      throws ResourceDoesNotExistException {
    if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
      throw new ResourceDoesNotExistException("The remote resource does not exist.", e);
    }
  }

  private static class FileTransferException extends IOException {

    FileTransferException(Throwable cause) {
      super(cause);
    }
  }

  private static class GoogleRepository {

    private final Repository repository;

    GoogleRepository(Repository repository) {
      this.repository = repository;
    }

    GenericUrl constructURL(String artifactPath) {
      GenericUrl url = new GenericUrl();
      url.setScheme("https");
      url.setHost(repository.getHost());
      url.appendRawPath(repository.getBasedir());
      url.appendRawPath("/");
      url.appendRawPath(artifactPath);
      return url;
    }
  }
}
