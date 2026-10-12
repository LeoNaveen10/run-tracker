# Run Tracker

A native Android app for **outdoor running**, with a dark dashboard, lime accents,
live GPS diagnostics and an interactive route map. Android 8+; Android 12 supported.
No accounts, backend, advertising or paid API keys are needed.

## Install

Download [`downloads/RunTracker-0.2.0.apk`](downloads/RunTracker-0.2.0.apk) and open
it on your phone. Allow installation from the app opening it if Android asks.
Grant **precise location**, enable GPS and use it outdoors. This is a test APK,
signed with the same debug key as version 0.1.0: install over that version to
preserve your completed run history. Do not uninstall first.

## Running

- Tap **Start run**. Time starts after the first usable GPS fix, not while waiting
  for the receiver to acquire a signal.
- GPS requests use zero minimum time and zero minimum movement, accepting fixes
  at the receiver's native rate (typically around one per second). Screen metrics
  refresh twice per second. Distance is displayed to one metre; display precision
  does not imply one-metre GPS accuracy.
- Pause, resume, then finish and save. Pauses are excluded from run time and GPS
  route segments are separated so movement during a pause does not add distance.
- View live and saved routes. Drag to pan, pinch or use +/- to zoom, and tap
  **Fit route** to recenter. Tap a run in history to open its details.
- Recent GPS fix age, reported position accuracy and satellites used in the fix
  help identify poor reception.

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

The 26 JVM checks cover run time, pace, successive running fixes, stationary
drift, jumps, recovery after a rejected fix, duplicate timestamps, reception
gaps, invalid coordinates, pause/reset boundaries, geodesic distance and map
projection. The build checks the APK signature and metadata. A map tile request
with the app's User-Agent succeeded. These checks are not a substitute for
outdoor GPS and screen-off testing on a physical device.

An Android 12 software emulator successfully booted and installed an earlier
0.2.0 build, but graphics-buffer errors subsequently stalled launch and screenshot
requests. A complete runtime smoke test of the final APK remains unverified.

Device acceptance checks: start outdoors, wait for the GPS indicator, run a
known route, pause/resume, lock/unlock the screen, save, inspect history, and
force-close/reopen during a separate run to check paused checkpoint recovery.
