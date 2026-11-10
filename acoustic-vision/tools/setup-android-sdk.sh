#!/bin/bash
# Android SDK for building YOUNGIL Acoustic Vision APKs (Songs/acoustic-vision)
set -e
SDK=${SDK:-/root/android-sdk}
if [ ! -d "$SDK/platforms/android-36" ]; then
  mkdir -p "$SDK/cmdline-tools"
  curl -sSfo /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-9862592_latest.zip
  rm -rf "$SDK/cmdline-tools/latest" "$SDK/cmdline-tools/cmdline-tools"
  unzip -q /tmp/clt.zip -d "$SDK/cmdline-tools" && mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -f /tmp/clt.zip
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
  "$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-36" "build-tools;36.0.0" > /dev/null
fi
# Maven Central rate limits here: add Google's Maven Central mirror (this machine only)
mkdir -p /root/.gradle/init.d
cat > /root/.gradle/init.d/central-mirror.gradle <<'G'
def mirror = 'https://maven-central.storage-download.googleapis.com/maven2/'
settingsEvaluated { s ->
    s.pluginManagement.repositories { maven { url mirror } }
    s.dependencyResolutionManagement.repositories { maven { url mirror } }
}
G
echo "sdk.dir=$SDK" > "$(dirname "$0")/../local.properties"
