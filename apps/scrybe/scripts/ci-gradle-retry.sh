#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "Usage: bash scripts/ci-gradle-retry.sh <gradle-task> [<gradle-task> ...]" >&2
  exit 2
fi

max_attempts="${SCRYBE_GRADLE_MAX_ATTEMPTS:-3}"
base_backoff_seconds="${SCRYBE_GRADLE_RETRY_BACKOFF_SECONDS:-15}"

is_transient_repository_failure() {
  local log_file="$1"

  grep -Eiq \
    "Received status code (403|408|409|425|429|500|502|503|504)|Could not (GET|HEAD) 'https?://|Read timed out|Connection reset|Temporary failure in name resolution|Connection timed out|Network is unreachable|Remote host terminated the handshake|PKIX path building failed|No route to host" \
    "$log_file"
}

clear_dependency_download_state() {
  rm -rf "${HOME}/.gradle/.tmp" "${HOME}/.gradle/caches/journal-1" "${HOME}/.gradle/caches/modules-2/files-2.1" 2>/dev/null || true

  if [ -d "${HOME}/.gradle/caches/modules-2" ]; then
    find "${HOME}/.gradle/caches/modules-2" -maxdepth 1 -type d -name 'metadata-*' -exec rm -rf {} + 2>/dev/null || true
  fi
}

attempt=1
while [ "$attempt" -le "$max_attempts" ]; do
  log_file="$(mktemp)"
  echo "Gradle attempt ${attempt}/${max_attempts}: ./gradlew $* --no-daemon"

  set +e
  ./gradlew "$@" --no-daemon 2>&1 | tee "$log_file"
  gradle_status=${PIPESTATUS[0]}
  set -e

  if [ "$gradle_status" -eq 0 ]; then
    rm -f "$log_file"
    exit 0
  fi

  if [ "$attempt" -eq "$max_attempts" ] || ! is_transient_repository_failure "$log_file"; then
    echo "Gradle failed without a retryable repository error." >&2
    rm -f "$log_file"
    exit "$gradle_status"
  fi

  sleep_seconds=$((base_backoff_seconds * attempt))
  echo "Detected transient dependency resolution failure. Clearing Gradle download state and retrying in ${sleep_seconds}s..." >&2
  clear_dependency_download_state
  rm -f "$log_file"
  sleep "$sleep_seconds"
  attempt=$((attempt + 1))
done
