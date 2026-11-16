param(
    [string]$Image = 'jmc-ai-analyzer-api-tests:local'
)

$ErrorActionPreference = 'Stop'

docker build --file Dockerfile.test --tag $Image .
if ($LASTEXITCODE -ne 0) {
    Write-Host 'PENDING: container Maven build/test could not complete (Testcontainers needs Docker daemon access inside the build container).' -ForegroundColor Yellow
    exit 2
}

# Testcontainers needs access to a Docker daemon. On Docker Desktop for Windows
# this is not portable into a Linux container, so preserve the evidence instead
# of pretending that a container run succeeded.
docker run --rm $Image
if ($LASTEXITCODE -ne 0) {
    Write-Host 'PENDING: container Maven test run did not complete (check Docker/Testcontainers daemon access).' -ForegroundColor Yellow
    exit 2
}

Write-Host 'Container Maven build and tests passed.'
