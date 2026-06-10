#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="$ROOT_DIR/playwright-platform-web"

if [ ! -d "$TARGET_DIR" ]; then
  echo "Missing directory: $TARGET_DIR" >&2
  exit 1
fi

cd "$TARGET_DIR"
exec npm test -- "$@"
