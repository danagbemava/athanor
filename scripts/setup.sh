#!/usr/bin/env bash
set -euo pipefail

npm --prefix "$(dirname "$0")/../apps/ui" install
npm --prefix "$(dirname "$0")/../packages/spec" install

echo "Setup complete: UI and spec dependencies installed."
