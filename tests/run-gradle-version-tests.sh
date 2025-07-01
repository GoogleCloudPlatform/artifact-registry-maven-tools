#!/bin/bash

set -euo pipefail

# Script to test gradle projects against multiple gradle versions.
#
# Usage:
# 1. Make sure this script is executable: chmod +x tests/run-gradle-version-tests.sh
# 2. Run from the root of the repository:
#    ./tests/run-gradle-version-tests.sh <root_project_path>
#
# Example:
#    ./tests/run-gradle-version-tests.sh rootProjects/basic_gradle_proj

# --- Configuration ---
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
TESTED_GRADLE_VERSIONS_FILE="$SCRIPT_DIR/tested-gradle-versions.txt"
PROJECT_COMPONENTS_DIR="$SCRIPT_DIR/project_components"
PROJECT_ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
TEST_BUILDS_DIR="$PROJECT_ROOT_DIR/build/test-reports/gradle-version-tests"

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

# --- Main script ---

if [[ $# -ne 1 ]]; then
  log_error "Usage: $0 <root_project_path>"
  log_error "Example: $0 rootProjects/basic_gradle_proj"
  exit 1
fi

ROOT_PROJECT_PATH="$1"

if [[ ! -d "$PROJECT_COMPONENTS_DIR/$ROOT_PROJECT_PATH" ]]; then
    log_error "Root project not found at $PROJECT_COMPONENTS_DIR/$ROOT_PROJECT_PATH"
    exit 1
fi

if [[ ! -f "$TESTED_GRADLE_VERSIONS_FILE" ]]; then
    log_error "Gradle versions file not found at $TESTED_GRADLE_VERSIONS_FILE"
    exit 1
fi

# Read gradle versions, ignoring comments and empty lines
GRADLE_VERSIONS=$(grep -v '^#' "$TESTED_GRADLE_VERSIONS_FILE" | grep -v '^$' || true)

if [ -z "$GRADLE_VERSIONS" ]; then
    log_error "No gradle versions found in $TESTED_GRADLE_VERSIONS_FILE"
    exit 1
fi

declare -a results
declare -a failed_logs
overall_success=true

# Create a parent temporary directory for all test runs
PARENT_TEMP_DIR=$(mktemp -d -t gradle-version-tests.XXXXXX)
trap 'rm -rf "$PARENT_TEMP_DIR"' EXIT

log_info "Parent temporary directory: $PARENT_TEMP_DIR"

for version in $GRADLE_VERSIONS; do
  log_info "Testing Gradle version: $version"

  # Create a persistent directory for this version's logs
  VERSION_LOG_DIR="$TEST_BUILDS_DIR/$version"
  mkdir -p "$VERSION_LOG_DIR"
  LOG_FILE="$VERSION_LOG_DIR/build.log"

  # Create a dedicated temporary directory for this version's run
  TEST_DIR=$(mktemp -d -p "$PARENT_TEMP_DIR" "gradle-$version-XXXXXX")
  log_info "  Test directory: $TEST_DIR"
  log_info "  Log file: $LOG_FILE"

  # Copy all project components into the temp dir for this run
  log_info "  Copying project components to $TEST_DIR..."
  cp -a "$PROJECT_COMPONENTS_DIR/." "$TEST_DIR/"

  PROJECT_RUN_DIR="$TEST_DIR/$ROOT_PROJECT_PATH"

  # Change into the project directory to run the build
  pushd "$PROJECT_RUN_DIR" > /dev/null

  log_info "  Updating Gradle wrapper to version $version..."
  if ! ./gradlew wrapper --gradle-version "$version" --no-daemon > "$LOG_FILE" 2>&1; then
      log_error "  Failed to update wrapper to version $version"
      print_log_tail "$LOG_FILE"
      results+=("Gradle $version: FAIL (wrapper update)")
      failed_logs+=("$LOG_FILE")
      overall_success=false
      popd > /dev/null
      continue
  fi

  log_info "  Running ./gradlew wrapper again to update scripts..."
  if ! ./gradlew wrapper --no-daemon >> "$LOG_FILE" 2>&1; then
      log_error "  Failed to run wrapper a second time for version $version"
      print_log_tail "$LOG_FILE"
      results+=("Gradle $version: FAIL (wrapper second run)")
      failed_logs+=("$LOG_FILE")
      overall_success=false
      popd > /dev/null
      continue
  fi

  log_info "  Running ./gradlew build..."
  if ./gradlew build --no-daemon >> "$LOG_FILE" 2>&1; then
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
