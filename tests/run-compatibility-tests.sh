#!/bin/bash

set -euo pipefail

# Script to test gradle projects against multiple gradle versions.
#
# Usage:
# 1. Make sure this script is executable: chmod +x tests/run-compatibility-tests.sh
# 2. Run from the root of the repository:
#    ./tests/run-compatibility-tests.sh <root_project_path> [--rebuild-plugin] [--ci-build-id=<id>]
#
# Example:
#    ./tests/run-compatibility-tests.sh rootProjects/basic_gradle_proj --rebuild-plugin

# --- Configuration ---
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROJECT_ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
TESTED_GRADLE_VERSIONS_FILE="$SCRIPT_DIR/tested-gradle-versions.txt"
PROJECT_COMPONENTS_DIR="$SCRIPT_DIR/project_components"
TEST_MAVEN_REPO_DIR="$PROJECT_ROOT_DIR/build/testMavenRepo"

# --- Functions ---
log_info() {
  echo "INFO: $*"
}

log_error() {
  echo "ERROR: $*" >&2
}

print_log_tail() {
    local log_file="$1"
    echo "--- Last 50 lines of log from $log_file ---" >&2
    tail -n 50 "$log_file" >&2
    echo "-------------------------------------------------" >&2
}

# --- Argument Parsing ---
REBUILD_PLUGIN=false
CI_BUILD_ID=""
GRADLE_VERSION_OVERRIDE=""
POSITIONAL_ARGS=()

while [[ $# -gt 0 ]]; do
  case $1 in
    --rebuild-plugin)
      REBUILD_PLUGIN=true
      shift
      ;;
    --ci-build-id=*)
      CI_BUILD_ID="${1#*=}"
      shift
      ;;
    --gradle-version=*)
      GRADLE_VERSION_OVERRIDE="${1#*=}"
      shift
      ;;
    *)
      POSITIONAL_ARGS+=("$1")
      shift
      ;;
  esac
done

set -- "${POSITIONAL_ARGS[@]}" # Restore positional parameters

if [[ $# -ne 1 ]]; then
  log_error "Usage: $0 <root_project_path> [--rebuild-plugin] [--ci-build-id=<id>] [--gradle-version=<version>]"
  log_error "Example: $0 rootProjects/basic_gradle_proj"
  exit 1
fi

ROOT_PROJECT_PATH="$1"

# --- Main script ---

if [[ ! -d "$PROJECT_COMPONENTS_DIR/$ROOT_PROJECT_PATH" ]]; then
    log_error "Root project not found at $PROJECT_COMPONENTS_DIR/$ROOT_PROJECT_PATH"
    exit 1
fi

if [ "$REBUILD_PLUGIN" = true ]; then
    log_info "Rebuilding plugin..."
    pushd "$PROJECT_ROOT_DIR" > /dev/null
    if ! ./gradlew publishAllPublicationsToTestMavenRepoRepository; then
        log_error "Failed to rebuild and publish the plugin."
        exit 1
    fi
    popd > /dev/null
    log_info "Plugin rebuilt successfully."
fi

if [[ ! -d "$TEST_MAVEN_REPO_DIR" ]]; then
    log_error "Test Maven repository not found at $TEST_MAVEN_REPO_DIR"
    log_error "Run the script with --rebuild-plugin or run './gradlew publishAllPublicationsToTestMavenRepoRepository' manually."
    exit 1
fi

# Read gradle versions, ignoring comments and empty lines
if [ -n "$GRADLE_VERSION_OVERRIDE" ]; then
    log_info "Using provided Gradle version: $GRADLE_VERSION_OVERRIDE"
    GRADLE_VERSIONS="$GRADLE_VERSION_OVERRIDE"
else
    log_info "Reading Gradle versions from $TESTED_GRADLE_VERSIONS_FILE"
    if [[ ! -f "$TESTED_GRADLE_VERSIONS_FILE" ]]; then
        log_error "Gradle versions file not found at $TESTED_GRADLE_VERSIONS_FILE"
        exit 1
    fi
    GRADLE_VERSIONS=$(grep -v -e '^#' -e '^[[:space:]]*

declare -a results
declare -a failed_logs
overall_success=true

# Create a unique, persistent directory for this test run
if [ -n "$CI_BUILD_ID" ]; then
    RUN_DIR="$PROJECT_ROOT_DIR/build/compatibility-tests/$CI_BUILD_ID"
else
    RUN_DIR="$PROJECT_ROOT_DIR/build/compatibility-tests/test-run-$(date +%Y%m%d-%H%M%S)"
fi
mkdir -p "$RUN_DIR"
log_info "Test run directory: $RUN_DIR"

# Copy the test maven repo to the run directory
cp -a "$TEST_MAVEN_REPO_DIR" "$RUN_DIR/testMavenRepo"
RUN_TEST_MAVEN_REPO_DIR="$RUN_DIR/testMavenRepo"


for version in $GRADLE_VERSIONS; do
  # This is a simple approach for skipping known incompatible versions.
  # If more complex skip logic is needed in the future (e.g., for different
  # operating systems or architectures), this should be refactored into a
  # dedicated function or a case statement.
  current_jdk_major=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F. '{print $1}')
  # Skip versions on JDK 22+ that are known to be incompatible.
  # This applies to the range [8.0, 8.8), as versions before 8.0 and after 8.8 work correctly.
  if [[ "$current_jdk_major" -ge 22 \
        && "$(printf '%s\n' "$version" "8.0" | sort -V | head -n1)" = "8.0" \
        && "$(printf '%s\n' "$version" "8.8" | sort -V | head -n1)" != "8.8" ]]; then
      log_info "SKIPPING Gradle $version on JDK $current_jdk_major (incompatible). This range requires Gradle < 8.0 or >= 8.8."
      results+=("Gradle $version: SKIP")
      continue
  fi

  log_info "Testing Gradle version: $version"


  VERSION_TEST_DIR="$RUN_DIR/$version"
  mkdir -p "$VERSION_TEST_DIR"
  LOG_FILE="$VERSION_TEST_DIR/build.log"

  log_info "  Test directory: $VERSION_TEST_DIR"
  log_info "  Log file: $LOG_FILE"

  # Copy all project components into the version-specific test dir
  cp -a "$PROJECT_COMPONENTS_DIR/." "$VERSION_TEST_DIR/"

  PROJECT_RUN_DIR="$VERSION_TEST_DIR/$ROOT_PROJECT_PATH"

  pushd "$PROJECT_RUN_DIR" > /dev/null

  log_info "  Updating Gradle wrapper to version $version..."
  if ! ./gradlew wrapper --gradle-version "$version" --no-daemon -PtestMavenRepoPath="$RUN_TEST_MAVEN_REPO_DIR" > "$LOG_FILE" 2>&1; then
      log_error "  Failed to update wrapper to version $version"
      print_log_tail "$LOG_FILE"
      results+=("Gradle $version: FAIL (wrapper update)")
      failed_logs+=("$LOG_FILE")
      overall_success=false
      popd > /dev/null
      continue
  fi

  log_info "  Running ./gradlew wrapper again to update scripts..."
  if ! ./gradlew wrapper --no-daemon -PtestMavenRepoPath="$RUN_TEST_MAVEN_REPO_DIR" >> "$LOG_FILE" 2>&1; then
      log_error "  Failed to run wrapper a second time for version $version"
      print_log_tail "$LOG_FILE"
      results+=("Gradle $version: FAIL (wrapper second run)")
      failed_logs+=("$LOG_FILE")
      overall_success=false
      popd > /dev/null
      continue
  fi

  log_info "  Running ./gradlew build..."
  if ./gradlew build --no-daemon -PtestMavenRepoPath="$RUN_TEST_MAVEN_REPO_DIR" >> "$LOG_FILE" 2>&1; then
    log_info "  Build successful for version $version"
    results+=("Gradle $version: PASS")
  else
    log_error "  Build failed for version $version"
    print_log_tail "$LOG_FILE"
    results+=("Gradle $version: FAIL (build)")
    failed_logs+=("$LOG_FILE")
    overall_success=false
  fi

  popd > /dev/null
done

# --- Report ---
echo
echo "--- Test Report ---"
for result in "${results[@]}"; do
  echo "$result"
done
echo "-------------------"

if [ ${#results[@]} -eq 0 ]; then
    log_error "No valid gradle versions found in $TESTED_GRADLE_VERSIONS_FILE to test."
    exit 1
fi

if [ ${#failed_logs[@]} -gt 0 ]; then
    echo
    echo "--- Failed Logs ---"
    for log in "${failed_logs[@]}"; do
        echo "Log file for failed run: $log"
    done
    echo "-------------------"
fi


if [ "$overall_success" = true ]; then
  log_info "All tests passed!"
  exit 0
else
  log_error "Some tests failed."
  exit 1
fi
 "$TESTED_GRADLE_VERSIONS_FILE" || true)
fi

if [ -z "$GRADLE_VERSIONS" ]; then
    log_error "No gradle versions found in $TESTED_GRADLE_VERSIONS_FILE"
    exit 1
fi

declare -a results
declare -a failed_logs
overall_success=true

# Create a unique, persistent directory for this test run
if [ -n "$CI_BUILD_ID" ]; then
    RUN_DIR="$PROJECT_ROOT_DIR/build/compatibility-tests/$CI_BUILD_ID"
else
    RUN_DIR="$PROJECT_ROOT_DIR/build/compatibility-tests/test-run-$(date +%Y%m%d-%H%M%S)"
fi
mkdir -p "$RUN_DIR"
log_info "Test run directory: $RUN_DIR"

# Copy the test maven repo to the run directory
cp -a "$TEST_MAVEN_REPO_DIR" "$RUN_DIR/testMavenRepo"
RUN_TEST_MAVEN_REPO_DIR="$RUN_DIR/testMavenRepo"


for version in $GRADLE_VERSIONS; do
  # This is a simple approach for skipping known incompatible versions.
  # If more complex skip logic is needed in the future (e.g., for different
  # operating systems or architectures), this should be refactored into a
  # dedicated function or a case statement.
  current_jdk_major=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F. '{print $1}')
  # Skip versions on JDK 22+ that are known to be incompatible.
  # This applies to the range [8.0, 8.8), as versions before 8.0 and after 8.8 work correctly.
  if [[ "$current_jdk_major" -ge 22 \
        && "$(printf '%s\n' "$version" "8.0" | sort -V | head -n1)" = "8.0" \
        && "$(printf '%s\n' "$version" "8.8" | sort -V | head -n1)" != "8.8" ]]; then
      log_info "SKIPPING Gradle $version on JDK $current_jdk_major (incompatible). This range requires Gradle < 8.0 or >= 8.8."
      results+=("Gradle $version: SKIP")
      continue
  fi

  log_info "Testing Gradle version: $version"


  VERSION_TEST_DIR="$RUN_DIR/$version"
  mkdir -p "$VERSION_TEST_DIR"
  LOG_FILE="$VERSION_TEST_DIR/build.log"

  log_info "  Test directory: $VERSION_TEST_DIR"
  log_info "  Log file: $LOG_FILE"

  # Copy all project components into the version-specific test dir
  cp -a "$PROJECT_COMPONENTS_DIR/." "$VERSION_TEST_DIR/"

  PROJECT_RUN_DIR="$VERSION_TEST_DIR/$ROOT_PROJECT_PATH"

  pushd "$PROJECT_RUN_DIR" > /dev/null

  log_info "  Updating Gradle wrapper to version $version..."
  if ! ./gradlew wrapper --gradle-version "$version" --no-daemon -PtestMavenRepoPath="$RUN_TEST_MAVEN_REPO_DIR" > "$LOG_FILE" 2>&1; then
      log_error "  Failed to update wrapper to version $version"
      print_log_tail "$LOG_FILE"
      results+=("Gradle $version: FAIL (wrapper update)")
      failed_logs+=("$LOG_FILE")
      overall_success=false
      popd > /dev/null
      continue
  fi

  log_info "  Running ./gradlew wrapper again to update scripts..."
  if ! ./gradlew wrapper --no-daemon -PtestMavenRepoPath="$RUN_TEST_MAVEN_REPO_DIR" >> "$LOG_FILE" 2>&1; then
      log_error "  Failed to run wrapper a second time for version $version"
      print_log_tail "$LOG_FILE"
      results+=("Gradle $version: FAIL (wrapper second run)")
      failed_logs+=("$LOG_FILE")
      overall_success=false
      popd > /dev/null
      continue
  fi

  log_info "  Running ./gradlew build..."
  if ./gradlew build --no-daemon -PtestMavenRepoPath="$RUN_TEST_MAVEN_REPO_DIR" >> "$LOG_FILE" 2>&1; then
    log_info "  Build successful for version $version"
    results+=("Gradle $version: PASS")
  else
    log_error "  Build failed for version $version"
    print_log_tail "$LOG_FILE"
    results+=("Gradle $version: FAIL (build)")
    failed_logs+=("$LOG_FILE")
    overall_success=false
  fi

  popd > /dev/null
done

# --- Report ---
echo
echo "--- Test Report ---"
for result in "${results[@]}"; do
  echo "$result"
done
echo "-------------------"

if [ ${#results[@]} -eq 0 ]; then
    log_error "No valid gradle versions found in $TESTED_GRADLE_VERSIONS_FILE to test."
    exit 1
fi

if [ ${#failed_logs[@]} -gt 0 ]; then
    echo
    echo "--- Failed Logs ---"
    for log in "${failed_logs[@]}"; do
        echo "Log file for failed run: $log"
    done
    echo "-------------------"
fi


if [ "$overall_success" = true ]; then
  log_info "All tests passed!"
  exit 0
else
  log_error "Some tests failed."
  exit 1
fi
