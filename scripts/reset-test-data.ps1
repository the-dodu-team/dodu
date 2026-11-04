param([string]$ResultPath)
$ErrorActionPreference = 'Stop'
$Host.UI.RawUI.WindowTitle = 'Dodu Test Reset - fixed'
$doduRoot = Split-Path $PSScriptRoot -Parent
$doduJar = Join-Path $doduRoot 'backend\target\dodu-backend-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $doduJar)) { throw 'Build the backend JAR first.' }
$doduJava = Get-ChildItem (Join-Path $doduRoot '.tools\jdk') -Filter java.exe -Recurse | Where-Object FullName -Match '\\bin\\java.exe$' | Select-Object -First 1 -ExpandProperty FullName
if (-not $doduJava) { throw 'Local JDK was not found.' }
function Read-DoduSecret([string]$Prompt) {
    $doduSecure = Read-Host $Prompt -AsSecureString
    try { return [System.Net.NetworkCredential]::new('', $doduSecure).Password }
    finally { $doduSecure.Dispose() }
}
$doduNames = @('DODU_DB_URL','DODU_DB_USERNAME','DODU_DB_PASSWORD','DODU_ACCESS_CODE_PEPPER','DODU_PHOTO_STORAGE_PROVIDER','DODU_PHOTO_BUCKET','DODU_SUPABASE_URL','DODU_SUPABASE_SERVICE_ROLE_KEY','DODU_FCM_ENABLED')
$doduPrevious = @{}
foreach ($doduName in $doduNames) { $doduPrevious[$doduName] = [Environment]::GetEnvironmentVariable($doduName, 'Process') }
try {
    Write-Host 'Authorized scope: remove promise/photo/survey/notification test records. KEEP participants, access codes, sessions and subscriptions.'
    Write-Host 'Secrets are hidden, held only in this process, and never saved to files. Do not create new test data during reset.'
    $env:DODU_DB_URL = Read-Host 'DB JDBC URL (host/port/database only, same as Render)'
    if ($env:DODU_DB_URL -notmatch '^jdbc:postgresql://' -or $env:DODU_DB_URL -match '@|[?&](user|password)=') { throw 'Use a JDBC URL without credentials.' }
    $env:DODU_DB_USERNAME = Read-Host 'DB username'
    $env:DODU_DB_PASSWORD = Read-DoduSecret 'DB password'
    $env:DODU_ACCESS_CODE_PEPPER = Read-DoduSecret 'Render DODU_ACCESS_CODE_PEPPER (unchanged)'
    $env:DODU_SUPABASE_URL = Read-Host 'Supabase project URL (same as Render)'
    $env:DODU_SUPABASE_SERVICE_ROLE_KEY = Read-DoduSecret 'Supabase server-only service role credential'
    $env:DODU_PHOTO_STORAGE_PROVIDER = 'supabase'
    $env:DODU_PHOTO_BUCKET = 'dodu-photos'
    $env:DODU_FCM_ENABLED = 'false'
    & $doduJava -jar $doduJar --spring.profiles.active=operator --debug=false --trace=false --logging.level.root=OFF --logging.level.org.springframework=OFF --spring.main.banner-mode=off reset-test-data --scope=keep-participants | ForEach-Object {
        Write-Host $_
        if ($ResultPath -and $_ -match '^RESET_(OK|FAILED)') { $_ | Set-Content -LiteralPath $ResultPath -Encoding UTF8 }
    }
    if ($LASTEXITCODE -ne 0) { throw 'Reset did not complete. Do not report it as completed.' }
} finally {
    foreach ($doduName in $doduNames) { [Environment]::SetEnvironmentVariable($doduName, $doduPrevious[$doduName], 'Process') }
}
