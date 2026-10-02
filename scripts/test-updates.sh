#!/bin/sh
# Device-only integration test. Builds a tiny debug-signed APK; serves it on emulator loopback.
set -eu
cd "$(dirname "$0")/.."
PERILOG_SERIAL="${PERILOG_SERIAL:-emulator-5554}"
case "$PERILOG_SERIAL" in emulator-*) ;; *) echo 'Use a disposable test emulator.' >&2; exit 1;; esac
export ANDROID_SERIAL="$PERILOG_SERIAL"
PERILOG_SDK="${ANDROID_HOME:-$PWD/.tools/android-sdk}"
PERILOG_BT="$PERILOG_SDK/build-tools/36.0.0"
PERILOG_JAVA="${JAVA_HOME:-$PWD/.tools/jdk}"
PERILOG_FIXTURE="$PWD/.tools/update-fixture"
mkdir -p "$PERILOG_FIXTURE"
cat > "$PERILOG_FIXTURE/AndroidManifest.xml" <<'MANIFEST'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.poyal.perilog.debug" android:versionCode="9999" android:versionName="99.0.0">
    <uses-sdk android:minSdkVersion="31" android:targetSdkVersion="36" />
    <application android:label="페리로그 업데이트 검사" android:hasCode="false" />
</manifest>
MANIFEST
"$PERILOG_BT/aapt2" link -I "$PERILOG_SDK/platforms/android-36/android.jar" --manifest "$PERILOG_FIXTURE/AndroidManifest.xml" -o "$PERILOG_FIXTURE/unsigned.apk"
"$PERILOG_BT/zipalign" -f 4 "$PERILOG_FIXTURE/unsigned.apk" "$PERILOG_FIXTURE/aligned.apk"
# Android's standard development signing key, never the personal release key.
JAVA_HOME="$PERILOG_JAVA" "$PERILOG_BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$PERILOG_FIXTURE/update-fixture.apk" "$PERILOG_FIXTURE/aligned.apk"
"$PERILOG_SDK/platform-tools/adb" -s "$PERILOG_SERIAL" shell input keyevent KEYCODE_WAKEUP
"$PERILOG_SDK/platform-tools/adb" -s "$PERILOG_SERIAL" shell wm dismiss-keyguard
# Revoking this app-op kills the target process on Android 15. Restore it after instrumentation exits.
trap '"$PERILOG_SDK/platform-tools/adb" -s "$PERILOG_SERIAL" shell appops set com.poyal.perilog.debug REQUEST_INSTALL_PACKAGES default >/dev/null' EXIT HUP INT TERM
./scripts/build.sh :app:connectedDebugAndroidTest -PupdateFixture \
    -Pandroid.testInstrumentationRunnerArguments.class=com.poyal.perilog.AndroidUpdateDownloadTest \
    -Pandroid.testInstrumentationRunnerArguments.timeout_msec=120000 \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
