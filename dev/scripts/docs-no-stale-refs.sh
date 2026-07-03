#!/usr/bin/env bash
# Fail the build if the docs or source comments reference a banned JSON library or a removed
# symbol. Nomos generates dependency-free code and a first-party runtime, so no doc, README, or
# source comment may name a third-party JSON library; and the published docs must not mention
# symbols that no longer exist in the code. This keeps the checked-in docs (including the
# generated Scaladoc under docs/api) from silently drifting away from the code.
#
# Wired into the Maven `test` phase (parent pom). Run standalone with:
#   dev/scripts/docs-no-stale-refs.sh

set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

status=0

# User-facing docs: the published site plus every tracked Markdown file.
mapfile -t DOC_FILES < <(git ls-files -- docs '*.md')
# Main (non-test) sources: comments and strings must not name Jackson or removed symbols.
mapfile -t SRC_FILES < <(git ls-files -- '*.scala' '*.java' | grep -v '/src/test/')

ALL_FILES=("${DOC_FILES[@]}" "${SRC_FILES[@]}")

# 1) No third-party JSON library may be named (the dependency-free guarantee).
banned_lib='Jackson|fasterxml|ObjectMapper|JsonNode'
lib_hits=$(grep -rniE "$banned_lib" "${ALL_FILES[@]}" 2>/dev/null || true)
if [[ -n "$lib_hits" ]]; then
    echo "Banned JSON-library reference (nomos is dependency-free):"
    echo "$lib_hits"
    status=1
fi

# 2) Removed symbols must not be referenced (they no longer exist in the code).
removed='AmbiguityError|withNullableTypes|detectAmbiguity'
removed_hits=$(grep -rnE "$removed" "${ALL_FILES[@]}" 2>/dev/null || true)
if [[ -n "$removed_hits" ]]; then
    echo "Reference to a removed symbol:"
    echo "$removed_hits"
    status=1
fi

if [[ $status -eq 0 ]]; then
    echo "docs-no-stale-refs: OK (no banned or removed references)."
fi
exit $status
