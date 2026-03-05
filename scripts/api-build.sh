#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../apps/api"
./gradlew --no-daemon build -x test

echo "API build gate passed."
