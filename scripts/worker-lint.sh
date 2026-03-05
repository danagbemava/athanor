#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../apps/worker"
go test ./... >/dev/null

echo "Worker lint placeholder passed via go test compile check."
