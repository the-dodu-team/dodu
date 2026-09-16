param([ValidateSet('run', 'verify')][string]$Task = 'run')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jdkDirectory = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools/jdk') -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin/java.exe') } | Select-Object -First 1
if (-not $jdkDirectory) { throw '프로젝트 내부 JDK가 없습니다. README.md의 JDK 준비 절차를 확인하세요.' }
$env:JAVA_HOME = $jdkDirectory.FullName
$env:PATH = "$($env:JAVA_HOME)\bin;$env:PATH"
Push-Location (Join-Path $projectRoot 'backend')
try {
    if ($Task -eq 'verify') { & .\mvnw.cmd "-Dmaven.repo.local=$projectRoot/.tools/m2" verify }
    else { & .\mvnw.cmd "-Dmaven.repo.local=$projectRoot/.tools/m2" spring-boot:run "-Dspring-boot.run.arguments=--server.address=127.0.0.1" }
    if ($LASTEXITCODE -ne 0) { throw "Maven 종료 코드: $LASTEXITCODE" }
} finally { Pop-Location }
