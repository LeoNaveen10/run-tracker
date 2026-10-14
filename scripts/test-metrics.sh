#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="${ANDROID_SDK_ROOT:-/workspace/android-sdk}"
compiler="${ECJ_JAR:-/workspace/android-tools/ecj.jar}"
json="${JSON_TEST_JAR:-/workspace/android-tools/json.jar}"
classpath="build/classes:$json:$sdk/platforms/android-35/android.jar"
mkdir -p build/tests
java -jar "$compiler" -8 -classpath "$classpath" -d build/tests tests/com/runtracker/app/*.java
for test in Metrics GpsFilter RunHistory GoalProgress SplitTracker RollingPace; do
    java -cp "build/tests:$classpath" "com.runtracker.app.${test}Test"
done
