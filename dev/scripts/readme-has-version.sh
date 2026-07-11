#!/bin/bash

# Fail if the project version is not referenced by the docs that must advertise it.
# Wired into the Maven `test` phase (parent pom), which passes ${project.version} as $1, so a
# version bump that forgets the docs breaks the build. Run standalone with:
#   dev/scripts/readme-has-version.sh [version]

# Prefer the version passed by Maven (${project.version}); otherwise read the root pom's own
# <version> (the first one, before any <dependency>/<plugin> versions).
VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
    VERSION=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -1)
fi

if [[ -z "$VERSION" ]]; then
    echo "Version not found in pom.xml"
    exit 1
fi

# Files that must reference the current version
FILES=(
    "README.md"
    "CHANGELOG.md"
    "docs/users/getting-started.html"
)

STATUS=0
for f in "${FILES[@]}"; do
    if [[ ! -f "$f" ]]; then
        echo "Version-check file missing: $f"
        STATUS=1
        continue
    fi
    if grep -qF "$VERSION" "$f"; then
        echo "Version $VERSION is present in $f."
    else
        echo "Version $VERSION is NOT present in $f."
        STATUS=1
    fi
done

PARENT_POMS=(
    "nomos-bom/pom.xml"
    "nomos-runtime/pom.xml"
    "nomos-core/pom.xml"
    "nomos-maven-plugin/pom.xml"
)
for f in "${PARENT_POMS[@]}"; do
    parent_version=$(sed -n '/<parent>/,/<\/parent>/s:.*<version>\(.*\)</version>.*:\1:p' "$f" | head -1)
    if [[ "$parent_version" == "$VERSION" ]]; then
        echo "Parent version $VERSION is present in $f."
    else
        echo "Parent version in $f is '$parent_version', expected '$VERSION'."
        STATUS=1
    fi
done

example_version=$(sed -n 's:.*<nomos.version>\(.*\)</nomos.version>.*:\1:p' nomos-example/pom.xml | head -1)
if [[ "$example_version" == "$VERSION" ]]; then
    echo "Nomos example version matches $VERSION."
else
    echo "Nomos example version is '$example_version', expected '$VERSION'."
    STATUS=1
fi

exit $STATUS
