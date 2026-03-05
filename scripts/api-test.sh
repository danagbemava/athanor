#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../apps/api"
./gradlew --no-daemon test

echo "API test gate passed."
