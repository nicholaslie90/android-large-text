#!/usr/bin/env bash
#
# Builds a signed BigSign.apk without Gradle: aapt2 -> javac -> d8 -> apksigner.
# Only a JDK and the Android SDK build-tools are needed; nothing is downloaded.
#
set -euo pipefail

cd "$(dirname "$0")"
ROOT="$PWD"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
KEYSTORE="${BIGSIGN_KEYSTORE:-$HOME/.config/bigsign/bigsign.jks}"
KEY_ALIAS="bigsign"
KEY_PASS="${BIGSIGN_KEYSTORE_PASS:-bigsign}"

MIN_SDK=24
TARGET_SDK=34
VERSION_CODE=1
VERSION_NAME=1.0

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

die() { echo "error: $*" >&2; exit 1; }

[ -d "$SDK" ] || die "Android SDK not found at $SDK (set ANDROID_HOME)"
[ -x "$JAVA_HOME/bin/javac" ] || die "no JDK at $JAVA_HOME (set JAVA_HOME, or: brew install openjdk@17)"

# Newest installed build-tools and platform, so an SDK update does not break this.
BUILD_TOOLS="$(ls -1 "$SDK/build-tools" | sort -V | tail -1)"
BT="$SDK/build-tools/$BUILD_TOOLS"
PLATFORM_DIR="$(ls -1d "$SDK"/platforms/android-* | sort -V | tail -1)"
ANDROID_JAR="$PLATFORM_DIR/android.jar"
[ -f "$ANDROID_JAR" ] || die "no android.jar in $PLATFORM_DIR"

BUILD="$ROOT/build"
DIST="$ROOT/dist"
rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$DIST"

echo "==> toolchain"
echo "    build-tools $BUILD_TOOLS, $(basename "$PLATFORM_DIR"), $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"

echo "==> tests"
"$ROOT/test.sh"

echo "==> resources"
"$BT/aapt2" compile --dir app/res -o "$BUILD/res.zip"
"$BT/aapt2" link \
    -o "$BUILD/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest app/AndroidManifest.xml \
    --java "$BUILD/gen" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
    "$BUILD/res.zip"

echo "==> java"
find app/src "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac -encoding UTF-8 -nowarn \
    --release 11 \
    -classpath "$ANDROID_JAR" \
    -d "$BUILD/classes" \
    @"$BUILD/sources.txt"

echo "==> dex"
find "$BUILD/classes" -name '*.class' > "$BUILD/classes.txt"
"$BT/d8" --release --min-api "$MIN_SDK" --lib "$ANDROID_JAR" \
    --output "$BUILD/dex" @"$BUILD/classes.txt"

echo "==> package"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
(cd "$BUILD/dex" && zip -q "$BUILD/unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
    echo "==> generating signing key at $KEYSTORE"
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias "$KEY_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$KEY_PASS" -keypass "$KEY_PASS" \
        -dname "CN=BigSign, OU=Personal, O=BigSign, L=, S=, C=" >/dev/null
fi

echo "==> sign"
"$BT/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$KEY_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --out "$DIST/BigSign.apk" \
    "$BUILD/aligned.apk"
"$BT/apksigner" verify "$DIST/BigSign.apk"

echo "==> done"
"$BT/aapt2" dump badging "$DIST/BigSign.apk" | head -3
ls -lh "$DIST/BigSign.apk" | awk '{print "    " $9 "  " $5}'
