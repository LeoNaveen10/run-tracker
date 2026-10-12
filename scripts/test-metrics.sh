#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="${ANDROID_SDK_ROOT:-/workspace/android-sdk}"
compiler="${ECJ_JAR:-/workspace/android-tools/ecj.jar}"
mkdir -p build/tests
java -jar "$compiler" -8 -classpath "build/classes:$sdk/platforms/android-35/android.jar" -d build/tests tests/com/runtracker/app/MetricsTest.java tests/com/runtracker/app/GpsFilterTest.java
java -cp "build/tests:build/classes:$sdk/platforms/android-35/android.jar" com.runtracker.app.MetricsTest
java -cp "build/tests:build/classes:$sdk/platforms/android-35/android.jar" com.runtracker.app.GpsFilterTest
