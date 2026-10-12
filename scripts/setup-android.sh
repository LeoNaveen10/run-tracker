#!/usr/bin/env bash
set -euo pipefail
# Build-only toolchain. Java 21, Python 3, curl and unzip are required.
root="${ANDROID_SDK_ROOT:-/workspace/android-sdk}"
mkdir -p "$root/platforms" "$root/build-tools" /workspace/android-tools
temporary="$(mktemp -d /tmp/run-tracker-setup.XXXXXX)"
trap 'rm -rf "$temporary"' EXIT
download() {
    curl -fsSL --retry 2 "$1" -o "$2"
    printf '%s  %s\n' "$3" "$2" | sha1sum -c -
}
if [ ! -f "$root/platforms/android-35/android.jar" ]; then
    download https://dl.google.com/android/repository/platform-35_r02.zip "$temporary/platform.zip" 0bb560a90a7a2cbd0dd8348224d518b638fe7949
    unzip -q "$temporary/platform.zip" -d "$temporary/platform"
    mv "$temporary/platform/android-35" "$root/platforms/android-35"
fi
if [ ! -f "$root/build-tools/35.0.0/aapt2" ]; then
    download https://dl.google.com/android/repository/build-tools_r35_linux.zip "$temporary/tools.zip" 2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc
    unzip -q "$temporary/tools.zip" -d "$temporary/tools"
    mv "$temporary/tools/android-15" "$root/build-tools/35.0.0"
fi
if [ ! -f /workspace/android-tools/ecj.jar ]; then
    download https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.41.0/ecj-3.41.0.jar "$temporary/ecj.jar" "$(curl -fsSL https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.41.0/ecj-3.41.0.jar.sha1)"
    mv "$temporary/ecj.jar" /workspace/android-tools/ecj.jar
fi
java -version
"$root/build-tools/35.0.0/aapt2" version
