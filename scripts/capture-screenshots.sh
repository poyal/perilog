#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# Only an explicitly selected development emulator is used. Real device/user data is untouched.
PERILOG_SERIAL="${PERILOG_SERIAL:-emulator-5554}"
case "$PERILOG_SERIAL" in emulator-*) ;; *) echo 'Select a development emulator, not a personal device.' >&2; exit 1;; esac
export ANDROID_SERIAL="$PERILOG_SERIAL"
ADB="${ANDROID_HOME:-$PWD/.tools/android-sdk}/platform-tools/adb"
"$ADB" -s "$PERILOG_SERIAL" get-state >/dev/null
PERILOG_DEBUG_PATH=$("$ADB" -s "$PERILOG_SERIAL" shell pm path com.poyal.perilog.debug)
if [ -n "$PERILOG_DEBUG_PATH" ]; then
  "$ADB" -s "$PERILOG_SERIAL" shell pm clear com.poyal.perilog.debug >/dev/null
fi
./scripts/build.sh :app:connectedDebugAndroidTest -PcaptureScreenshots \
  -Pandroid.testInstrumentationRunnerArguments.class=com.poyal.perilog.capture.DocumentationCapture \
  -Pandroid.testInstrumentationRunnerArguments.timeout_msec=240000 \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
mkdir -p docs/screenshots
for name in $("$ADB" -s "$PERILOG_SERIAL" shell run-as com.poyal.perilog.debug ls files/manual-screenshots); do
  "$ADB" -s "$PERILOG_SERIAL" exec-out run-as com.poyal.perilog.debug cat "files/manual-screenshots/$name" > "docs/screenshots/$name"
done
