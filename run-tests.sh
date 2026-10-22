#!/bin/sh
# The tests of the app: the pure logic (the reading of the system's numbers, the addresses, the dates).
# No Gradle and no JUnit: javac and java, like the build.
#
#   ./run-tests.sh
#
# The tests that need the Android are not here: those are checked on the tablet.

set -e
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
[ -n "$SDK" ] && [ -d "$SDK" ] || { echo "Android SDK not found. Set ANDROID_HOME (see README)."; exit 1; }
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
AJ=""
for p in $(ls -d "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V -r); do
  v="$(basename "$(dirname "$p")" | sed 's/android-//')"
  if [ "$v" -le 30 ] 2>/dev/null; then AJ="$p"; break; fi
done
[ -n "$AJ" ] || AJ="$(ls -d "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1 || true)"
[ -f "${AJ:-}" ] || { echo "Android platform not found. Run: sdkmanager \"platforms;android-30\""; exit 1; }
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
command -v "$JAVAC" >/dev/null 2>&1 || { echo "JDK not found. Install JDK 17 and set JAVA_HOME."; exit 1; }

# The classes of the app (the same ones the build makes, but without the APK).
CLASSES_ONLY=1 ./build.sh

rm -rf out/tests
mkdir -p out/tests
"$JAVAC" -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$AJ:$BT/core-lambda-stubs.jar" \
  -classpath "$AJ:out/classes" -d out/tests $(find tests -name '*.java')
# The android.jar is in the classpath because the app's classes mention it (the tests do not call it).
"$JAVA" -cp "out/tests:out/classes:$AJ" io.freemytablet.Tests
