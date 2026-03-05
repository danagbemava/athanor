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

echo "Schema validation passed."
