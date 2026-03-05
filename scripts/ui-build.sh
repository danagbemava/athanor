#!/usr/bin/env bash
set -euo pipefail

root="$(dirname "$0")/../apps/ui"

if [ ! -d "$root/node_modules" ]; then
  echo "UI dependencies missing. Run: npm --prefix apps/ui install"
  exit 1
fi

npm --prefix "$root" run build
