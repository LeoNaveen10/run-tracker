#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
formatter="${JAVA_FORMAT_JAR:-/workspace/android-tools/google-java-format.jar}"
url="https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/1.24.0/google-java-format-1.24.0-all-deps.jar"
if [ ! -f "$formatter" ]; then
    mkdir -p "$(dirname "$formatter")"
    curl -fsSL "$url" -o "$formatter"
fi
expected="$(curl -fsSL "$url.sha1")"
printf '%s  %s\n' "$expected" "$formatter" | sha1sum -c -
java -jar "$formatter" --aosp --replace app/src/main/java/com/runtracker/app/*.java tests/com/runtracker/app/*.java
