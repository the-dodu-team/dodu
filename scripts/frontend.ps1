param([ValidateSet('run', 'verify')][string]$Task = 'run')
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path (Split-Path $PSScriptRoot -Parent) 'frontend')
try {
    if ($Task -eq 'verify') {
        & npm.cmd run lint
        if ($LASTEXITCODE -ne 0) { throw '프런트엔드 lint 실패' }
        & npm.cmd run build
    } else { & npm.cmd run dev }
    if ($LASTEXITCODE -ne 0) { throw "npm 종료 코드: $LASTEXITCODE" }
} finally { Pop-Location }
