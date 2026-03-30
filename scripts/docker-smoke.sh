#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/infra/docker-compose.yml"

cleanup() {
  docker compose -f "$COMPOSE_FILE" down --volumes
}

service_id() {
  docker compose -f "$COMPOSE_FILE" ps -q "$1"
}

service_state() {
  local id
  id="$(service_id "$1")"
  if [[ -z "$id" ]]; then
    echo "missing"
    return
  fi

  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id"
}

wait_for_state() {
  local service="$1"
  local expected="$2"
  local attempts="${3:-45}"
  local delay_seconds="${4:-2}"
  local state=""

  for _ in $(seq 1 "$attempts"); do
    state="$(service_state "$service")"
    if [[ "$state" == "$expected" ]]; then
      return 0
    fi
    sleep "$delay_seconds"
  done

  echo "service '$service' did not reach state '$expected' (last state: '$state')" >&2
  docker compose -f "$COMPOSE_FILE" ps >&2
  return 1
}

wait_for_http() {
  local name="$1"
  local url="$2"
  local attempts="${3:-45}"
  local delay_seconds="${4:-2}"

  for _ in $(seq 1 "$attempts"); do
    if curl -fsS "$url" >/dev/null; then
      return 0
    fi
    sleep "$delay_seconds"
  done

  echo "endpoint '$name' did not become ready at $url" >&2
  docker compose -f "$COMPOSE_FILE" ps >&2
  return 1
}

trap cleanup EXIT

docker compose -f "$COMPOSE_FILE" config >/dev/null
docker compose -f "$COMPOSE_FILE" down --volumes >/dev/null 2>&1 || true
docker compose -f "$COMPOSE_FILE" build api worker ui
docker compose -f "$COMPOSE_FILE" up -d postgres redis minio minio-init api worker ui

wait_for_state postgres healthy
wait_for_state redis healthy
wait_for_state minio healthy
wait_for_state worker healthy
wait_for_state api healthy
wait_for_http api-health http://127.0.0.1:8080/actuator/health
wait_for_http ui-builder http://127.0.0.1:3001/builder

docker compose -f "$COMPOSE_FILE" ps
