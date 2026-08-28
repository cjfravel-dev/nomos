#!/usr/bin/env bash
# Build the published documentation site into an output directory (default: target/site).
#
# The site is assembled, never checked in: the hand-written pages under docs/ are copied,
# every {{NOMOS_VERSION}} placeholder is replaced with the reactor's version, and the core
# and runtime Scaladoc are generated fresh into api/ and runtime-api/. The Pages workflow
# publishes the result on every push to main, and CI builds it on every pull request.
#
# Usage: dev/scripts/build-site.sh [output-dir]
#
# Set NOMOS_SITE_SKIP_BUILD=1 to reuse artifacts already installed in the local repository
# (CI does this after its own `mvn install`); otherwise the modules are installed first so
# Scaladoc can resolve them.

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

OUT="${1:-target/site}"
MVN="./mvnw"
[[ -x "$MVN" ]] || MVN="mvn"

VERSION=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -1)
if [[ -z "$VERSION" ]]; then
    echo "ERROR: could not read the project version from pom.xml" >&2
    exit 1
fi

if [[ "${NOMOS_SITE_SKIP_BUILD:-0}" != "1" ]]; then
    echo "==> Installing nomos-runtime and nomos-core so Scaladoc can resolve them..."
    "$MVN" -q -B -pl nomos-runtime,nomos-core -am -DskipTests -Dgpg.skip=true install
fi

echo "==> Generating runtime and core Scaladoc via scala-maven-plugin..."
"$MVN" -q -B -pl nomos-runtime,nomos-core scala:doc

for module in nomos-runtime nomos-core; do
    if [[ ! -d "$module/target/site/scaladocs" ]]; then
        echo "ERROR: $module/target/site/scaladocs not produced" >&2
        exit 1
    fi
done

echo "==> Assembling the site in $OUT (version $VERSION)..."
rm -rf "$OUT"
mkdir -p "$OUT"
cp -r docs/. "$OUT"/

# Substitute placeholders in the hand-written pages only; Scaladoc is copied in afterwards.
while IFS= read -r -d '' page; do
    sed -i "s/{{NOMOS_VERSION}}/$VERSION/g" "$page"
done < <(find "$OUT" -type f \( -name '*.html' -o -name '*.js' -o -name '*.css' \) -print0)

leftover=$(grep -rlE '\{\{[A-Z_]+\}\}' "$OUT" || true)
if [[ -n "$leftover" ]]; then
    echo "ERROR: unsubstituted placeholder in:" >&2
    echo "$leftover" >&2
    exit 1
fi

cp -r nomos-core/target/site/scaladocs "$OUT/api"
cp -r nomos-runtime/target/site/scaladocs "$OUT/runtime-api"

echo "==> Done. Preview with: python3 -m http.server --directory $OUT"
