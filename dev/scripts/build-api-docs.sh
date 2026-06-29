#!/usr/bin/env bash
# Regenerate the API reference (Scaladoc) under docs/api/.
#
# Run this whenever the public Scala API surface of nomos-core changes.
# The output is checked in so the docs site can serve it directly.
#
# Usage: dev/scripts/build-api-docs.sh

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

echo "==> Generating Scaladoc via scala-maven-plugin..."
mvn -q -pl nomos-core scala:doc

if [[ ! -d nomos-core/target/site/scaladocs ]]; then
  echo "ERROR: nomos-core/target/site/scaladocs not produced" >&2
  exit 1
fi

echo "==> Replacing docs/api/ with fresh output..."
rm -rf docs/api
cp -r nomos-core/target/site/scaladocs docs/api

echo "==> Done. Review with: git status docs/api/"
