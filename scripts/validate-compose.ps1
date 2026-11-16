param(
    [switch]$Build,
    [switch]$Mcp,
    [switch]$Ai
)

$ErrorActionPreference = 'Stop'

# Compose requires a password to be supplied, but this validation must never
# read or create a credential file. The value exists only in this process.
$env:POSTGRES_PASSWORD = 'compose-validation-only'

if ([string]::IsNullOrWhiteSpace($env:FRONTEND_IMAGE) -or
    $env:FRONTEND_IMAGE -match '(^|/)(placeholder|frontend|angular)(:|@|$)' -or
    [string]::IsNullOrWhiteSpace($env:FRONTEND_USER) -or $env:FRONTEND_USER.StartsWith('0:')) {
    throw 'FRONTEND_IMAGE and non-root FRONTEND_USER are required; supply a real compiled Angular image (prefer a digest), not a placeholder.'
}

function Assert-LocalImageIsNonRoot([string]$image, [string]$name) {
    $inspect = docker image inspect $image 2>$null
    if ($LASTEXITCODE -eq 0) {
        $configuredUser = (($inspect | ConvertFrom-Json)[0].Config.User)
        if ([string]::IsNullOrWhiteSpace($configuredUser) -or $configuredUser -eq '0' -or $configuredUser -eq 'root' -or $configuredUser -eq '0:0') {
            throw "$name image is locally available but does not declare a non-root user; rebuild/provision it with USER or use an image-compatible non-root policy."
        }
    } else {
        Write-Host "$name image is not local; non-root metadata will be checked when the provisioned image is pulled." -ForegroundColor Yellow
    }
}

Assert-LocalImageIsNonRoot $env:FRONTEND_IMAGE 'FRONTEND'

if ($Mcp) {
    if ([string]::IsNullOrWhiteSpace($env:MCP_ARTIFACT_PATH) -or -not (Test-Path -LiteralPath $env:MCP_ARTIFACT_PATH -PathType Leaf)) { throw 'MCP_ARTIFACT_PATH must point to the provisioned MCP JAR.' }
    if ([IO.Path]::GetExtension($env:MCP_ARTIFACT_PATH) -ne '.jar' -or [string]::IsNullOrWhiteSpace($env:MCP_ARTIFACT_VERSION) -or [string]::IsNullOrWhiteSpace($env:MCP_JAR_SHA256)) { throw 'MCP artifact requires .jar, MCP_ARTIFACT_VERSION and MCP_JAR_SHA256.' }
    $env:MCP_ENABLED = 'true'
}

$files = @('-f', 'docker-compose.yml')
if ($Mcp) { $files += @('-f', 'docker-compose.mcp.yml') }
if ($Ai) {
    if ([string]::IsNullOrWhiteSpace($env:AI_IMAGE) -or [string]::IsNullOrWhiteSpace($env:AI_USER) -or $env:AI_USER.StartsWith('0:') -or [string]::IsNullOrWhiteSpace($env:AI_MODEL) -or [string]::IsNullOrWhiteSpace($env:AI_PROVIDER)) { throw 'AI profile requires AI_IMAGE, non-root AI_USER, AI_MODEL and AI_PROVIDER.' }
    Assert-LocalImageIsNonRoot $env:AI_IMAGE 'AI'
    $files += @('-f', 'docker-compose.ai.yml', '--profile', 'ai')
}

docker compose @files config --quiet
if ($LASTEXITCODE -ne 0) {
    throw 'docker compose config failed'
}

if ($Build) {
    docker compose @files build backend
    if ($LASTEXITCODE -ne 0) {
        throw 'backend image build failed'
    }
}

Write-Host 'Compose validation passed.'
