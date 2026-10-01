#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -d "$PWD/.tools/jdk" ]; then export JAVA_HOME="$PWD/.tools/jdk"; fi
if [ -d "$PWD/.tools/android-sdk" ]; then export ANDROID_HOME="$PWD/.tools/android-sdk"; fi
if [ -d "$PWD/.tools/gradle-home" ]; then export GRADLE_USER_HOME="$PWD/.tools/gradle-home"; fi
if [ "$#" -eq 0 ]; then set -- :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease; fi
exec ./gradlew "$@"
