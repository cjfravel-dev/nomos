#!/usr/bin/env bash
# Regenerate the core and runtime API references.
#
# Run this whenever the public Scala API surface of nomos-core or nomos-runtime changes.
# The output is checked in so the docs site can serve it directly.
#
# Usage: dev/scripts/build-api-docs.sh

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

echo "==> Generating runtime and core Scaladoc via scala-maven-plugin..."
mvn -q -pl nomos-runtime,nomos-core scala:doc

for module in nomos-runtime nomos-core; do
  if [[ ! -d "$module/target/site/scaladocs" ]]; then
    echo "ERROR: $module/target/site/scaladocs not produced" >&2
    exit 1
  fi
done

echo "==> Replacing checked-in API references with fresh output..."
rm -rf docs/api docs/runtime-api
cp -r nomos-core/target/site/scaladocs docs/api
cp -r nomos-runtime/target/site/scaladocs docs/runtime-api

echo "==> Done. Review with: git status docs/api docs/runtime-api"
