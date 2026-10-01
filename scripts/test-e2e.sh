#!/bin/sh
# Repeatable UI/Room E2E on a disposable emulator. Never targets the personal release package.
set -eu
cd "$(dirname "$0")/.."
PERILOG_SERIAL="${PERILOG_SERIAL:-emulator-5554}"
case "$PERILOG_SERIAL" in emulator-*) ;; *) echo 'PERILOG_SERIAL must be a development emulator.' >&2; exit 1;; esac
PERILOG_PROFILE="${1:-normal}"
case "$PERILOG_PROFILE" in normal|large|all) ;; *) echo 'Usage: test-e2e.sh [normal|large|all]' >&2; exit 1;; esac
PERILOG_TEST_TARGET="${PERILOG_TEST_TARGET:-com.poyal.perilog.AppFlowTest}"
PERILOG_RESULTS_DIR="${PERILOG_RESULTS_DIR:-$PWD/.tools/e2e-results}"
export ANDROID_SERIAL="$PERILOG_SERIAL"
PERILOG_ADB="${ANDROID_HOME:-$PWD/.tools/android-sdk}/platform-tools/adb"
adb_device() { "$PERILOG_ADB" -s "$PERILOG_SERIAL" "$@"; }
adb_device get-state >/dev/null
PERILOG_FONT=$(adb_device shell settings get system font_scale | tr -d '\r')
PERILOG_AIRPLANE=$(adb_device shell settings get global airplane_mode_on | tr -d '\r')
PERILOG_HARD_KEYBOARD_IME=$(adb_device shell settings get secure show_ime_with_hard_keyboard | tr -d '\r')
PERILOG_SCREEN_TIMEOUT=$(adb_device shell settings get system screen_off_timeout | tr -d '\r')
PERILOG_STAY_ON=$(adb_device shell settings get global stay_on_while_plugged_in | tr -d '\r')
cleanup() {
  if [ "$PERILOG_FONT" = null ]; then adb_device shell settings delete system font_scale >/dev/null; else adb_device shell settings put system font_scale "$PERILOG_FONT" >/dev/null; fi
  if [ "$PERILOG_AIRPLANE" = 1 ]; then adb_device shell cmd connectivity airplane-mode enable; else adb_device shell cmd connectivity airplane-mode disable; fi
  if [ "$PERILOG_HARD_KEYBOARD_IME" = null ]; then adb_device shell settings delete secure show_ime_with_hard_keyboard >/dev/null; else adb_device shell settings put secure show_ime_with_hard_keyboard "$PERILOG_HARD_KEYBOARD_IME" >/dev/null; fi
  if [ "$PERILOG_SCREEN_TIMEOUT" = null ]; then adb_device shell settings delete system screen_off_timeout >/dev/null; else adb_device shell settings put system screen_off_timeout "$PERILOG_SCREEN_TIMEOUT" >/dev/null; fi
  if [ "$PERILOG_STAY_ON" = null ]; then adb_device shell settings delete global stay_on_while_plugged_in >/dev/null; else adb_device shell settings put global stay_on_while_plugged_in "$PERILOG_STAY_ON" >/dev/null; fi
}
trap cleanup EXIT HUP INT TERM
run_profile() {
  profile="$1"
  output="$PERILOG_RESULTS_DIR/$profile"
  mkdir -p "$output"
  rm -rf "$output/report" "$output/results"
  rm -f "$output"/failure-*.png
  # Compose semantics can still work behind a keyguard, but real window focus
  # and the software keyboard require an awake, unlocked device.
  adb_device shell settings put system screen_off_timeout 2147483647
  adb_device shell svc power stayon true
  adb_device shell input keyevent KEYCODE_WAKEUP
  adb_device shell wm dismiss-keyguard
  adb_device shell am force-stop com.poyal.perilog.debug
  if [ -n "$(adb_device shell pm path com.poyal.perilog.debug)" ]; then adb_device shell pm clear com.poyal.perilog.debug >/dev/null; fi
  if [ "$profile" = large ]; then adb_device shell settings put system font_scale 1.5; else adb_device shell settings put system font_scale 1.0; fi
  adb_device shell cmd connectivity airplane-mode enable
  adb_device shell settings put secure show_ime_with_hard_keyboard 1
  perilog_test_status=0
  ./scripts/build.sh :app:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=$PERILOG_TEST_TARGET" \
    -Pandroid.testInstrumentationRunnerArguments.timeout_msec=120000 \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true > "$output/run.log" 2>&1 || perilog_test_status=$?
  if [ -d app/build/reports/androidTests/connected ]; then cp -R app/build/reports/androidTests/connected "$output/report"; fi
  if [ -d app/build/outputs/androidTest-results/connected ]; then cp -R app/build/outputs/androidTest-results/connected "$output/results"; fi
  for name in $(adb_device shell run-as com.poyal.perilog.debug ls files/e2e-artifacts 2>/dev/null | tr -d '\r'); do
    case "$name" in *.png) adb_device exec-out run-as com.poyal.perilog.debug cat "files/e2e-artifacts/$name" > "$output/$name";; esac
  done
  adb_device logcat -d -t 2500 > "$output/logcat.txt"
  cat "$output/run.log"
  return "$perilog_test_status"
}
if [ "$PERILOG_PROFILE" = all ]; then run_profile normal; run_profile large; else run_profile "$PERILOG_PROFILE"; fi
