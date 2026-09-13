#!/usr/bin/env bash
#
# Runs the host-JVM tests for the parts of the app that hold no Android types.
#
set -euo pipefail

cd "$(dirname "$0")"

JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export PATH="$JAVA_HOME/bin:$PATH"

OUT="build/test-classes"
rm -rf "$OUT"
mkdir -p "$OUT"

javac -encoding UTF-8 -d "$OUT" \
    app/src/vc/east/bigsign/SignStyle.java \
    app/src/vc/east/bigsign/History.java \
    app/src/vc/east/bigsign/TextFitter.java \
    test/vc/east/bigsign/*.java

java -cp "$OUT" vc.east.bigsign.TestMain
