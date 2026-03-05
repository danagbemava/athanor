#!/usr/bin/env bash
set -euo pipefail

schema_dir="$(dirname "$0")/../packages/spec/schemas"

for f in "$schema_dir"/*.schema.json; do
  if ! jq empty "$f" >/dev/null 2>&1; then
    echo "Invalid JSON: $f"
    exit 1
  fi

  if ! jq -e '.version and ."$schema" and .title and .type' "$f" >/dev/null; then
    echo "Missing required metadata fields in $f"
    exit 1
  fi

done

echo "Schema metadata validation passed."

if command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1; then
  spec_dir="$(dirname "$0")/../packages/spec"
  if [ -d "$spec_dir/node_modules" ]; then
    npm --prefix "$spec_dir" run validate
  else
    echo "Skipping fixture validation because packages/spec/node_modules is missing."
  fi
else
  echo "Skipping fixture validation because node/npm are not installed."
fi
