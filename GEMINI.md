# Gemini Project Configuration

This file provides context and instructions for the Gemini AI agent. Following these guidelines will help the agent understand the project and provide more accurate and helpful responses.

## Project Overview

This project contains a set of tools for authenticating with and using Google Artifact Registry with Maven and Gradle.

- `artifactregistry-auth-common`: Common authentication library.
- `artifactregistry-gradle-plugin`: A Gradle plugin for Artifact Registry.
- `artifactregistry-maven-wagon`: A Maven wagon for Artifact Registry.
- `sample-composite-project`: A sample project demonstrating usage.

## General Instructions

- Follow the existing coding style and conventions.
- Use the Gradle wrapper (`./gradlew`) for all Gradle commands.
- Do not commit directly to the `main` branch. All changes should be made through pull requests.

## Development

### Building the Project

To build the entire project, run the following command from the root directory:

```bash
./gradlew build
```

### Running Tests

To run all tests, use:

```bash
./gradlew test
```

## Architecture

The project is a monorepo composed of three main Java modules that work together to provide authentication for Google Artifact Registry within Maven and Gradle build environments.

- **`artifactregistry-auth-common`**: This is the foundational library.
  - It provides a `CredentialProvider` interface and a `DefaultCredentialProvider` implementation.
  - The core responsibility is to abstract away the details of obtaining Google Cloud credentials. It attempts to source credentials first from the `gcloud` CLI tool and falls back to standard Application Default Credentials (ADC) if `gcloud` is not available or configured.
  - This module has no dependencies on Maven or Gradle and can be used independently.

- **`artifactregistry-maven-wagon`**: This module implements a custom Wagon for Apache Maven.
  - The `ArtifactRegistryWagon` class extends Maven's `AbstractWagon`.
  - It is responsible for handling the `artifactregistry://` protocol in `pom.xml` files.
  - When Maven needs to interact with a repository using this protocol, this wagon is invoked. It uses the `artifactregistry-auth-common` module to fetch the necessary Google Cloud credentials.
  - It then translates the `artifactregistry://` URL into a standard `https://` URL and injects the OAuth2 access token into the request headers for authentication, enabling Maven to securely download dependencies from and publish artifacts to private Artifact Registry repositories.

- **`artifactregistry-gradle-plugin`**: This module provides a plugin for the Gradle build tool.
  - The `ArtifactRegistryGradlePlugin` class is the entry point.
  - It integrates into the Gradle lifecycle to automatically configure repositories that are defined with an `artifactregistry://` URL.
  - Similar to the Maven wagon, it uses the `artifactregistry-auth-common` library to obtain credentials.
  - It then dynamically rewrites the repository URL to use `https://` and configures the repository with the correct `oauth2accesstoken` credentials, allowing Gradle to resolve dependencies and publish artifacts to Artifact Registry.

The overall architecture is a classic example of shared-kernel, where the `auth-common` module provides the core, reusable logic, and the `maven-wagon` and `gradle-plugin` modules act as adapters for their specific build tool ecosystems.

## Coding Style

The project follows standard Google Java style guidelines.

- **Indentation:** 2 spaces.
- **Brace Style:** Opening braces are on the same line as the declaration (e.g., `class MyClass {`).
- **Naming Conventions:**
    - Classes are `PascalCase` (e.g., `DefaultCredentialProvider`).
    - Methods are `camelCase` (e.g., `getCredential`).
    - Constants are `UPPER_SNAKE_CASE` (e.g., `REFESH_INTERVAL_MS`).
    - Local variables are `camelCase` (e.g., `cachedCredentials`).
- **Spacing:**
    - A single space after commas in argument lists.
    - A single space around operators (e.g., `x + y`).
- **Comments:** Javadoc-style comments are used for public classes and methods. Single-line comments are used for implementation details.
- **License Headers:** All files must have a standard Apache 2.0 license header.


