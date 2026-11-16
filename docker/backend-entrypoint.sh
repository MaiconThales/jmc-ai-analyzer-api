#!/bin/sh
set -eu

if [ "${MCP_ENABLED:-false}" = "true" ]; then
  jar_path="${MCP_JAR_PATH:-}"
  version="${MCP_ARTIFACT_VERSION:-}"
  sha256="${MCP_JAR_SHA256:-}"
  [ -n "$jar_path" ] && [ -f "$jar_path" ] || { echo "MCP_ENABLED=true requires MCP_JAR_PATH to point to a provisioned JAR" >&2; exit 64; }
  [ -n "$version" ] || { echo "MCP_ENABLED=true requires MCP_ARTIFACT_VERSION" >&2; exit 64; }
  [ -n "$sha256" ] || { echo "MCP_ENABLED=true requires MCP_JAR_SHA256" >&2; exit 64; }
  actual="$(sha256sum "$jar_path" | awk '{print $1}')"
  [ "$actual" = "$sha256" ] || { echo "MCP artifact SHA-256 mismatch" >&2; exit 65; }
fi

exec java -jar /app/app.jar
