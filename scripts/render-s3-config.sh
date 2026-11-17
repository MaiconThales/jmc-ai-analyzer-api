#!/usr/bin/env bash
# Gera docker/seaweedfs/s3.json (não versionado) com as credenciais do ambiente (.env).
set -euo pipefail
cd "$(dirname "$0")/.."
: "${BLOB_ACCESS_KEY:?defina BLOB_ACCESS_KEY}"
: "${BLOB_SECRET_KEY:?defina BLOB_SECRET_KEY}"
python3 - <<'PY'
import json, os
with open("docker/seaweedfs/s3.json.example") as f:
    config = json.load(f)
credentials = config["identities"][0]["credentials"][0]
credentials["accessKey"] = os.environ["BLOB_ACCESS_KEY"]
credentials["secretKey"] = os.environ["BLOB_SECRET_KEY"]
with open("docker/seaweedfs/s3.json", "w") as f:
    json.dump(config, f, indent=2)
PY
echo "docker/seaweedfs/s3.json gerado"
