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

exit $STATUS
