param(
    [ValidateRange(1, 100)][int]$Count = 1,
    [string]$StudyCode = 'technical-smoke-20261104',
    [string]$Prefix = 'BATCH-20261105'
)
$ErrorActionPreference = 'Stop'
$doduRoot = Split-Path $PSScriptRoot -Parent
$doduJar = Join-Path $doduRoot 'backend\target\dodu-backend-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $doduJar)) { throw 'Build the backend JAR first.' }
$doduJava = Get-ChildItem (Join-Path $doduRoot '.tools\jdk') -Filter java.exe -Recurse |
    Where-Object { $_.Directory.Name -eq 'bin' } | Select-Object -First 1 -ExpandProperty FullName
if (-not $doduJava) { throw 'Local JDK was not found.' }

function Read-DoduSecret([string]$Prompt) {
    $doduSecure = Read-Host $Prompt -AsSecureString
    try { return [System.Net.NetworkCredential]::new('', $doduSecure).Password }
    finally { $doduSecure.Dispose() }
}

$doduNames = @('DODU_DB_URL', 'DODU_DB_USERNAME', 'DODU_DB_PASSWORD', 'DODU_ACCESS_CODE_PEPPER')
$doduPrevious = @{}
foreach ($doduName in $doduNames) { $doduPrevious[$doduName] = [Environment]::GetEnvironmentVariable($doduName, 'Process') }
try {
    Write-Host 'Use the same DB connection and pepper as Render. Secrets are hidden and not saved to files.'
    $env:DODU_DB_URL = Read-Host 'DB JDBC URL (host/port/database only)'
    if ($env:DODU_DB_URL -notmatch '^jdbc:postgresql://' -or $env:DODU_DB_URL -match '@|[?&](user|password)=') {
        throw 'Enter a JDBC URL without username or password.'
    }
    $env:DODU_DB_USERNAME = Read-Host 'DB username'
    $env:DODU_DB_PASSWORD = Read-DoduSecret 'DB password'
    $env:DODU_ACCESS_CODE_PEPPER = Read-DoduSecret 'Render DODU_ACCESS_CODE_PEPPER'
    if ($Count -eq 1) {
        $doduParticipant = [Guid]::Parse((Read-Host 'Participant UUID from Supabase'))
    } else {
        Write-Host "Batch: $Count participants, study: $StudyCode, labels: $Prefix-001 onwards. Existing labels are skipped."
    }
    $doduOperator = Read-Host 'Operator name'
    $doduOptions = @('--spring.profiles.active=operator', '--logging.level.root=ERROR', '--logging.level.org.springframework.web=OFF')
    if ($Count -eq 1) {
        & $doduJava -jar $doduJar @doduOptions issue "--participant=$doduParticipant" "--operator=$doduOperator"
    } else {
        & $doduJava -jar $doduJar @doduOptions enroll-batch "--study-code=$StudyCode" "--prefix=$Prefix" "--count=$Count" "--operator=$doduOperator"
    }
    if ($LASTEXITCODE -ne 0) { throw 'Issuance failed. Keep credentials private when reviewing the error.' }
} finally {
    foreach ($doduName in $doduNames) { [Environment]::SetEnvironmentVariable($doduName, $doduPrevious[$doduName], 'Process') }
}
