#!/usr/bin/env bash
# Copy the latest family research JSON into the app's generated assets (also runs automatically on every build).
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew -q :app:syncFamilyData "$@"
echo "Bundled: $(ls app/build/generated/familyAssets/data)"
