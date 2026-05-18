#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "error: $ENV_FILE not found. Copy .env.example and fill in your credentials." >&2
  exit 1
fi

set -a
# shellcheck source=.env
source "$ENV_FILE"
set +a

echo "Starting Go minimal app on http://localhost:${PORT:-8080}"
cd "$SCRIPT_DIR"
go run .
