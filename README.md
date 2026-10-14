# Run Tracker

A native Android app for **outdoor running**, with a light dashboard, orange accents,
live GPS diagnostics and an interactive route map. Android 8+; Android 12 supported.
No accounts, backend, advertising or paid API keys are needed.

## Install

Download [`downloads/RunTracker-0.7.0.apk`](downloads/RunTracker-0.7.0.apk) and open
it on your phone. Allow installation from the app opening it if Android asks.
Grant **precise location**, enable GPS and use it outdoors. This is a test APK,
signed with the same debug key as earlier versions: install over your existing app to
preserve your completed run history. Do not uninstall first.

## Running

- Choose a target before each new run: **5 km and 10 km presets**, any positive
  custom distance such as **1, 2, 3 or 4 km**, and **+2 / +5 / +10 km** buttons
  that add to the current selection. Fractional targets are supported to two
  decimals (minimum 0.01 km). Recovery keeps the original target.
- The dedicated recording page has **four large figures: distance, current pace,
  run time and target**. There is no map on the live page. The screen stays on
  while visible; pause/resume and finish controls remain available.
- All distance displays use **two decimals**, e.g. **1.22 km**. Stored distance,
  time and pace calculations are unchanged. Time starts after a usable GPS fix.
- Current pace means the time to cover **1 km at your current speed**, in min/km.
  Speed is averaged over an eight-second rolling window; the pace display refreshes
  every five seconds. It warms up for five seconds after starting/resuming or losing
  GPS. Distance and time still refresh normally. Paused, stationary, warming-up or
  stale readings show an em dash.
- Every completed kilometre triggers a spoken update, such as **“2 kilometres
  done. 3 kilometres to go.”** Target completion is announced once, and you can
  continue running. Voice uses the phone's English text-to-speech engine and
  media volume; if speech is unavailable the app plays a beep instead.
- Pause/recovery preserve milestone state to avoid announcing old kilometres
  again. Progress and targets are retained with interrupted runs.
- Tap any saved run to open its **date, distance, time, average pace and route**.
  The map opens at street-level zoom; pan, pinch, use +/- or **Fit route** to see
  the complete recorded route. Old saved runs remain available after updating.
- Saved-run details now include **per-kilometre splits**, with the fastest
  complete kilometre highlighted. Crossing times are interpolated between
  accepted GPS fixes using the existing pause-excluding run timer. Splits are
  checkpointed for recovery. Older logs keep their maps and metrics; historical
  split times are not invented when they were never recorded.
- **Share route & metrics** creates a PNG card with the date, metrics, target,
  full fitted route and OpenStreetMap attribution, then opens Android's share
  chooser. The light card uses a time-of-day title, a large orange-route map and
  a four-metric grid inspired by the supplied reference. A target-completion banner
  appears only when earned; no unrecorded calorie, elevation or PR claims are added.
  Export waits up to ten seconds for visible tiles, then offers an explicit outline
  fallback if offline. Start/end locations are included; there is no privacy badge.
  The chooser decides which app receives the image.
- Delete saved runs through the **⋮** menu or **Delete run** in details, with
  confirmation. Dashboard totals and previews update without affecting an
  interrupted recording.

## Reliability and GPS limits

This is outdoor-only. Running in a room, under a roof or between tall buildings
may produce no usable position. GPS cannot measure each footstep. Do not treat
screen refresh frequency as a guarantee of GPS precision.

Old fixes, fixes with reported accuracy worse than 20 m, impossible running
speeds and duplicate samples are rejected. Doppler-derived speed helps suppress
stationary drift. Small movements can count between fixes without the original
three-metre cutoff. Gaps in GPS reception start new route segments instead of
inventing distance across the gap. These filters cannot eliminate all GPS errors,
and missing reception can result in undercounting distance.

During a run, progress is checkpointed approximately every two seconds and on
location updates. If the app is stopped, it restores the last checkpoint in a
**paused** state on reopening. Time and movement while the app was stopped are
excluded. A few recent seconds can be lost if Android terminates the process
before queued storage writes finish. Completed runs and session closure are
saved together to reduce duplicate/lost-save risks. The app never automatically
resumes recording after an interruption.

Runs and coordinates are stored privately on the phone. Uninstalling the app
removes that data. Screen-off behavior and outdoor GPS accuracy must be tested
on the actual phone, particularly manufacturer battery restrictions. The
settings menu links to location and app battery settings.

## Maps and privacy

Street maps use OpenStreetMap's standard raster tiles over HTTPS, with visible
attribution, a stable app User-Agent, HTTP cache metadata and conditional
revalidation. Only visible tiles are requested; there is no bulk-download or
map-prefetch feature. Previously viewed tiles have a bounded local cache.
Street tiles need internet; GPS recording and the route overlay work without it.
If tiles are unavailable, the route is drawn on a clearly labelled fallback grid.

Map viewport tile coordinates and the phone's IP reach the tile provider. The
complete recorded route and run history are not uploaded. See the
[OSMF privacy policy](https://osmfoundation.org/wiki/Privacy_Policy) and
[tile usage policy](https://operations.osmfoundation.org/policies/tiles/).

## Build

Java 21, Python 3, curl and unzip are available in the cloud environment.
Pinned SDK archives and compiler dependencies are checksum-verified. The setup
script can reproduce the build prerequisites without modifying app source.

```sh
cd /workspace/run-tracker
bash scripts/setup-android.sh
bash scripts/build-apk.sh
bash scripts/test-metrics.sh
```

Output: `build/RunTracker-Android12.apk`. The signing key remains outside the
repository, at `/workspace/android-tools/run-tracker-debug.jks`. Preserve that
key for compatible test updates. A production/Play Store release needs a
separate protected release-signing setup; the APK here is a debug-signed build.

## Validation

The 104 JVM checks cover run time, average/current pace, speed units, target validation, milestone announcements,
recovery/duplicate/fractional goal boundaries, split interpolation, stationary
time, multiple boundaries, recovery and legacy unknown splits, saved-run deletion,
appended/duplicate/changed-history safety, successive running fixes, stationary
drift, jumps, recovery after a rejected fix, duplicate timestamps, reception
gaps, invalid coordinates, pause/reset boundaries, geodesic distance and map
projection, rolling pace warmup, time-weighted irregular samples, publication cadence,
speed changes, stationary pace, invalid samples and reset/gap behavior. The build checks the APK signature and metadata. A map tile request
with the app's User-Agent succeeded. These checks are not a substitute for
outdoor GPS and screen-off testing on a physical device.

An Android 12 software emulator successfully booted and installed an earlier
0.2.0 build, but graphics-buffer errors subsequently stalled launch and screenshot
requests. A complete runtime smoke test of the final APK remains unverified.

Device acceptance checks: start outdoors, wait for the GPS indicator, run a
known route, pause/resume, lock/unlock the screen, save, inspect history, and
force-close/reopen during a separate run to check paused checkpoint recovery.

Version 0.7.0 pace smoothing, refreshed layouts and full-route sharing still need
physical-device acceptance testing. Previous distance, time and pace behavior was confirmed by the
user on Android 12; other Android versions meet the minimum SDK requirement but
have not been device-tested. Shared images use a private read-only content provider
with a temporary URI grant to the selected app; no storage permission is needed.

## Code formatting

All app and test Java files use google-java-format 1.24.0 (AOSP style).
Run `bash scripts/format-code.sh` to apply the same format; the pinned formatter
is downloaded outside the repository and checksum-verified. XML resources and
shell scripts are also formatted for readable maintenance.
