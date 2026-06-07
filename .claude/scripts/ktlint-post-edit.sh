#!/bin/bash
# Called by Claude Code after every Edit/Write on a .kt file.
# Reads tool input JSON from stdin, extracts the file path, runs ktlintFormat
# on the owning Gradle module, then re-stages the file if it was already staged.

set -euo pipefail

INPUT=$(cat)
FILE=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('file_path', d.get('path', '')))" 2>/dev/null || true)

# Only care about .kt files inside apps/scrybe
[[ "$FILE" == *"apps/scrybe"* && "$FILE" == *.kt ]] || exit 0

REPO_ROOT=$(git -C "$(dirname "$FILE")" rev-parse --show-toplevel 2>/dev/null) || exit 0

# Derive Gradle module path from file path
REL=${FILE#$REPO_ROOT/apps/scrybe/}          # e.g. feature/settings/src/main/...
MODULE_PATH=${REL%%/src/*}                   # e.g. feature/settings
MODULE=":${MODULE_PATH//\//:}"               # e.g. :feature:settings

printf "ktlint: formatting %s\n" "$MODULE"
(cd "$REPO_ROOT/apps/scrybe" && ./gradlew "${MODULE}:ktlintFormat" -q 2>&1) || {
    echo "ktlint: format task failed (non-fatal)"
    exit 0
}

# If the file was already staged, re-stage it to pick up formatter changes
if git -C "$REPO_ROOT" diff --cached --name-only | grep -qF "${FILE#$REPO_ROOT/}"; then
    git -C "$REPO_ROOT" add "$FILE"
fi
