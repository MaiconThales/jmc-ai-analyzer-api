#!/usr/bin/env bash
# Cria a árvore de dados no host e gera a configuração do SeaweedFS a partir do .env.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ ! -f .env ]]; then
  echo "Arquivo .env não encontrado. Copie .env.example para .env e ajuste os valores." >&2
  exit 1
fi
set -a
# shellcheck disable=SC1091
source .env
set +a

DATA_DIR="${DATA_DIR:-./docker-data}"
APP_UID="${APP_UID:-1000}"
APP_GID="${APP_GID:-1000}"

mkdir -p "$DATA_DIR/postgres" "$DATA_DIR/seaweedfs" "$DATA_DIR/api/logs" "$DATA_DIR/api/tmp" \
         "$DATA_DIR/mcp" "$DATA_DIR/frontend"
# A API roda como usuário não-root (APP_UID) e precisa escrever em api/.
if [[ "$(id -u)" == "0" ]]; then
  chown -R "$APP_UID:$APP_GID" "$DATA_DIR/api"
elif [[ "$(id -u)" != "$APP_UID" ]]; then
  echo "Aviso: rode como root ou ajuste APP_UID/APP_GID no .env para o seu usuário ($(id -u):$(id -g))." >&2
fi

./scripts/render-s3-config.sh
echo "Pastas prontas em $DATA_DIR"
