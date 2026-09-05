#!/usr/bin/env bash
#
# Downloads the app binaries that are too large to keep in git.
#
# ApiDemos (4.7 MB) IS committed — the smoke suite must run straight after clone.
# My Demo App (32 MB) is not; pulling it on demand keeps the repo cloneable and lets
# CI cache it as a build artefact instead of paying for it on every fetch.
#
# Usage:  ./scripts/fetch-apps.sh
set -euo pipefail

APPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/src/test/resources/apps"
mkdir -p "$APPS_DIR"

# Pinned to an exact release: a moving "latest" would silently change the app under test
# and turn a vendor update into a mystery test failure.
MY_DEMO_APP_VERSION="1.3.0"
MY_DEMO_APP_BUILD="244"
MY_DEMO_APP_URL="https://github.com/saucelabs/my-demo-app-rn/releases/download/v${MY_DEMO_APP_VERSION}/Android-MyDemoAppRN.${MY_DEMO_APP_VERSION}.build-${MY_DEMO_APP_BUILD}.apk"
MY_DEMO_APP_FILE="${APPS_DIR}/Android-MyDemoAppRN.apk"

download() {
  local url="$1" target="$2" name="$3"
  if [[ -f "$target" ]]; then
    echo "✓ ${name} already present ($(du -h "$target" | cut -f1)) — skipping"
    return
  fi
  echo "↓ Downloading ${name} ..."
  curl --fail --location --progress-bar --output "${target}.part" "$url"
  # Every Android/iOS package is a zip; a redirect or error page is not.
  if ! head -c 2 "${target}.part" | grep -q "PK"; then
    rm -f "${target}.part"
    echo "✗ ${name}: the download was not a valid package (check the URL / your network)" >&2
    exit 1
  fi
  mv "${target}.part" "$target"
  echo "✓ ${name} -> ${target}"
}

download "$MY_DEMO_APP_URL" "$MY_DEMO_APP_FILE" "My Demo App RN ${MY_DEMO_APP_VERSION}"

# TODO: add the iOS simulator build here once a macOS runner exists. It ships as a .zip
# that must be expanded into src/test/resources/apps/MyRNDemoApp.app, e.g.
#   curl -fL -o /tmp/sim.zip "https://github.com/saucelabs/my-demo-app-rn/releases/download/v1.3.0/iOS-Simulator-MyRNDemoApp.1.3.0-162.zip"
#   unzip -q /tmp/sim.zip -d "$APPS_DIR"

echo
echo "Apps ready in ${APPS_DIR}:"
ls -lh "$APPS_DIR"
