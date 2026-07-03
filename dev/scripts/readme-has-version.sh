#!/bin/bash

# Fail if the project version in pom.xml is not referenced by the docs that must advertise it.
# Wired into the Maven `test` phase (parent pom) so a version bump that forgets the docs breaks
# the build. Run standalone with: dev/scripts/readme-has-version.sh

# Extract version from the root pom.xml (the parent's <version> is first)
VERSION=$(grep -oPm1 "(?<=<version>)[^<]+" pom.xml)

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
    if grep -q "$VERSION" "$f"; then
        echo "Version $VERSION is present in $f."
    else
        echo "Version $VERSION is NOT present in $f."
        STATUS=1
    fi
done

exit $STATUS
