#!/bin/sh
set -e
cd "$(dirname "$0")"

if [ -x /storage/emulated/0/apps/gradle-9.7.1/bin/gradle ]; then
  GRADLE=/storage/emulated/0/apps/gradle-9.7.1/bin/gradle
elif command -v gradle >/dev/null 2>&1; then
  GRADLE="$(command -v gradle)"
else
  echo "No Gradle found. Need Gradle 9.x (on-device: /storage/emulated/0/apps/gradle-9.7.1)." >&2
  exit 1
fi
echo "Gradle: $GRADLE"

if [ -z "${JAVA_HOME:-}" ]; then
  J="$(command -v java 2>/dev/null)" || true
  if [ -n "$J" ]; then
    LD="$(cd "$(dirname "$J")" && pwd)"
    JAVA_HOME="$(cd "$LD/.." && pwd)"
  elif [ -x /data/data/com.termux/files/usr/lib/jvm/java-21-openjdk ]; then
    JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk
  elif [ -x /data/data/com.termux/files/usr/lib/jvm/java-17-openjdk ]; then
    JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
  fi
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
  echo "No JDK found. Need JDK 17+; set JAVA_HOME (on-device: restore Termux, then 'pkg install openjdk-21')." >&2
  exit 1
fi
export JAVA_HOME
echo "JAVA_HOME: $JAVA_HOME"
"$JAVA_HOME/bin/java" -version

if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -d /storage/emulated/0/apps/android-sdk ]; then
    ANDROID_HOME=/storage/emulated/0/apps/android-sdk
  elif [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    ANDROID_HOME="${ANDROID_SDK_ROOT}"
  fi
fi
if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "${ANDROID_HOME}/platforms" ]; then
  echo "No Android SDK found. Set ANDROID_HOME (needs platform android-35)." >&2
  exit 1
fi
export ANDROID_HOME
echo "ANDROID_HOME: $ANDROID_HOME"

if [ ! -f local.properties ]; then
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
  echo "Wrote local.properties (sdk.dir=$ANDROID_HOME)"
fi

sh "$GRADLE" --no-daemon :app:assembleDebug

APK=app/build/outputs/apk/debug/app-debug.apk
if [ -f "$APK" ]; then
  echo
  echo "BUILD OK -> $APK ($(wc -c < "$APK") bytes)"
  echo "Install: adb install -r '$APK'"
else
  echo "Build failed: no APK produced." >&2
  exit 1
fi
