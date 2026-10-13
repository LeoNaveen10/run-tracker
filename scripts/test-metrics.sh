#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="${ANDROID_SDK_ROOT:-/workspace/android-sdk}"
compiler="${ECJ_JAR:-/workspace/android-tools/ecj.jar}"
mkdir -p build/tests
json="${JSON_TEST_JAR:-/workspace/android-tools/json.jar}"
java -jar "$compiler" -8 -classpath "build/classes:$json:$sdk/platforms/android-35/android.jar" -d build/tests tests/com/runtracker/app/MetricsTest.java tests/com/runtracker/app/GpsFilterTest.java tests/com/runtracker/app/RunHistoryTest.java tests/com/runtracker/app/GoalProgressTest.java tests/com/runtracker/app/SplitTrackerTest.java
java -cp "build/tests:build/classes:$sdk/platforms/android-35/android.jar" com.runtracker.app.MetricsTest
java -cp "build/tests:build/classes:$sdk/platforms/android-35/android.jar" com.runtracker.app.GpsFilterTest
java -cp "build/tests:build/classes:$json:$sdk/platforms/android-35/android.jar" com.runtracker.app.RunHistoryTest
java -cp "build/tests:build/classes:$sdk/platforms/android-35/android.jar" com.runtracker.app.GoalProgressTest
java -cp "build/tests:build/classes:$sdk/platforms/android-35/android.jar" com.runtracker.app.SplitTrackerTest
