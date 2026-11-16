# jmc-ai-analyzer-api

## MCP STDIO transport

The MCP bridge accepts exactly one framing per process: `lines` (the default,
one complete JSON-RPC object per UTF-8 line) or `content-length` (the legacy
`Content-Length: N\r\n\r\n` header followed by exactly `N` UTF-8 bytes). The two
framings must not be mixed. Configure it with `MCP_TRANSPORT` or
`mcp.transport` in `application-*.yaml`; any other value is rejected during
configuration binding. Responses may be fragmented at the stream level and
multiple frames may be read consecutively, but a line frame must end in a
newline and a content-length frame must contain the complete declared body.

## Docker Compose

The base Compose stack contains the backend, PostgreSQL, and a configurable
image slot for a compiled Angular application. This repository does **not**
contain a frontend artifact, so `FRONTEND_IMAGE` and its non-root
`FRONTEND_USER` must be provisioned externally (prefer an immutable digest).
The value is required and placeholder/default images are rejected by the
validation script. Only the frontend port is published on loopback.

```powershell
Copy-Item .env.example .env
# Set POSTGRES_PASSWORD, FRONTEND_IMAGE and FRONTEND_USER in .env.
docker compose up -d --build
```

JFR files are read from the named `jfr-data` volume. The MCP server is supplied
through the read-only artifact mount at `/opt/mcp/mcp-server-jmc.jar` only when
the explicit MCP override is used. MCP is disabled by default. The artifact
must be supplied by a trusted build, identified by `MCP_ARTIFACT_VERSION`, and
verified with `MCP_JAR_SHA256`; startup rejects missing or mismatched files.
Use `docker-compose.mcp.yml` and do not mount arbitrary host paths.

The AI profile is opt-in and has no invented model/image defaults:

```powershell
docker compose -f docker-compose.yml -f docker-compose.ai.yml --profile ai up -d
```

It requires `AI_IMAGE`, `AI_USER` (non-root), `AI_MODEL`, and `AI_PROVIDER`.
The backend receives `AI_ENABLED=true` and waits for the AI health check. Pin
the externally provisioned AI image by digest and provision the model before
claiming an operational AI deployment. The base stack keeps AI disabled.

Run the deterministic Compose validation without creating a credentials file:

```powershell
.\scripts\validate-compose.ps1
.\scripts\validate-compose.ps1 -Build
```

For enabled MCP validation, set the artifact variables and pass `-Mcp`; the
script checks that the file exists, is a JAR, and has a declared SHA-256. For
the AI profile, pass `-Ai`; it requires the configured image/model/non-root
user and checks image metadata when locally available.

Full analysis execution inside Compose remains B7.7 and is intentionally
pending until the real frontend, MCP artifact, and AI integration are supplied.
