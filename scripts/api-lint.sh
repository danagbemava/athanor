#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../apps/api"
./gradlew --no-daemon compileJava

echo "API lint gate passed (compileJava)."
