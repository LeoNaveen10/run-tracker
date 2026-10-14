#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="${ANDROID_SDK_ROOT:-/workspace/android-sdk}"
compiler="${ECJ_JAR:-/workspace/android-tools/ecj.jar}"
tools="$sdk/build-tools/35.0.0"
platform="$sdk/platforms/android-35/android.jar"
out="$PWD/build"
mkdir -p "$out/classes" "$out/dex" "$out/resources"
find "$out/classes" "$out/dex" "$out/resources" -type f -delete
"$tools/aapt2" compile --dir app/src/main/res -o "$out/resources"
"$tools/aapt2" link -o "$out/unsigned.apk" -I "$platform" --manifest app/src/main/AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 35 --version-code 7 --version-name 0.7.0 "$out/resources"/*.flat
find app/src/main/java -name '*.java' -print >"$out/sources.txt"
java -jar "$compiler" -8 -bootclasspath "$platform:$tools/core-lambda-stubs.jar" -d "$out/classes" @"$out/sources.txt"
python3 - "$out" <<'PY'
import pathlib, sys, zipfile
out = pathlib.Path(sys.argv[1])
with zipfile.ZipFile(out / 'classes.jar', 'w') as jar:
    for path in sorted((out / 'classes').rglob('*.class')):
        jar.write(path, path.relative_to(out / 'classes'))
PY
"$tools/d8" --lib "$platform" --min-api 26 --output "$out/dex" "$out/classes.jar"
python3 - "$out" <<'PY'
import pathlib, sys, zipfile
out = pathlib.Path(sys.argv[1])
with zipfile.ZipFile(out / 'unsigned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as apk:
    for path in sorted((out / 'dex').glob('*.dex')):
        apk.write(path, path.name)
PY
"$tools/zipalign" -f -p 4 "$out/unsigned.apk" "$out/aligned.apk"
key="${RUN_TRACKER_DEBUG_KEY:-/workspace/android-tools/run-tracker-debug.jks}"
if [ ! -f "$key" ]; then
    mkdir -p "$(dirname "$key")"
    keytool -genkeypair -keystore "$key" -storepass android -keypass android -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
fi
"$tools/apksigner" sign --ks "$key" --ks-pass pass:android --key-pass pass:android --out "$out/RunTracker-Android12.apk" "$out/aligned.apk"
"$tools/apksigner" verify --verbose "$out/RunTracker-Android12.apk"
"$tools/aapt2" dump badging "$out/RunTracker-Android12.apk"
printf 'APK: %s\n' "$out/RunTracker-Android12.apk"
