<#
.SYNOPSIS
    Deploy guidato e isolato del backend UgoCalc su Cloudflare Workers + D1 (0 EUR/mese).
.DESCRIPTION
    Questo script opera esclusivamente sul database D1 `ugocalc-db` e sul Worker `ugocalc-api`
    definiti in `infra/cloudflare-free/wrangler.toml`, senza toccare altri progetti Cloudflare.
    Compatibile con Windows PowerShell 5.1 (.NET Framework 4.8) e PowerShell 7+.
#>

param(
    [switch]$SetAuthSecret
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Sopprime i banner "npm notice" su stderr in Windows PowerShell 5.1
$env:NPM_CONFIG_LOGLEVEL = "error"
$env:NPM_CONFIG_UPDATE_NOTIFIER = "false"

$ScriptDir = $PSScriptRoot
$WranglerToml = Join-Path $ScriptDir "wrangler.toml"
$SchemaSql = Join-Path $ScriptDir "schema.sql"
$DbName = "ugocalc-db"
$WorkerName = "ugocalc-api"

function Invoke-Wrangler {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [switch]$CaptureOutput,
        [switch]$IgnoreExitCode
    )

    $prevPref = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        if ($CaptureOutput) {
            $rawLines = & npx --yes wrangler @Arguments
            $exitCode = $LASTEXITCODE
            $text = (($rawLines | ForEach-Object { "$_" }) -join "`n").Trim()
            if (-not $IgnoreExitCode -and $exitCode -ne 0) {
                throw "Comando 'wrangler $($Arguments -join ' ')' fallito (exit code $exitCode). Output:`n$text"
            }
            return [PSCustomObject]@{
                ExitCode = $exitCode
                Output   = $text
            }
        } else {
            & npx --yes wrangler @Arguments
            $exitCode = $LASTEXITCODE
            if (-not $IgnoreExitCode -and $exitCode -ne 0) {
                throw "Comando 'wrangler $($Arguments -join ' ')' fallito (exit code $exitCode)."
            }
            return [PSCustomObject]@{
                ExitCode = $exitCode
                Output   = ""
            }
        }
    } finally {
        $ErrorActionPreference = $prevPref
    }
}

function ConvertFrom-JsonArrayText {
    param(
        [string]$RawText
    )
    if ([string]::IsNullOrWhiteSpace($RawText)) {
        return @()
    }
    $startIdx = $RawText.IndexOf('[')
    $endIdx = $RawText.LastIndexOf(']')
    if ($startIdx -lt 0 -or $endIdx -lt $startIdx) {
        return @()
    }
    $jsonSlice = $RawText.Substring($startIdx, $endIdx - $startIdx + 1)
    $parsed = $jsonSlice | ConvertFrom-Json
    if ($null -eq $parsed) {
        return @()
    }
    return @($parsed)
}

Write-Host "=== UgoCalc Cloudflare Free Deploy (0 EUR/mese) ===" -ForegroundColor Cyan
Write-Host "Database isolato : $DbName"
Write-Host "Worker isolato   : $WorkerName"
Write-Host ""

# 1. Verifica prerequisiti Node/npx
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "Node.js non trovato nel PATH. Installa Node.js LTS da https://nodejs.org/ e riprova."
}
if (-not (Get-Command npx -ErrorAction SilentlyContinue)) {
    throw "npx non trovato nel PATH. Assicurati che npm sia installato correttamente."
}

if (-not (Test-Path $WranglerToml)) {
    throw "File mancante: $WranglerToml"
}
if (-not (Test-Path $SchemaSql)) {
    throw "File mancante: $SchemaSql"
}

Push-Location $ScriptDir
try {
    # 2. Verifica login Cloudflare (interattivo se necessario)
    Write-Host "[1/6] Verifica autenticazione Cloudflare..." -ForegroundColor Yellow
    $whoami = Invoke-Wrangler -Arguments @("whoami") -CaptureOutput -IgnoreExitCode
    if ($whoami.ExitCode -ne 0 -or $whoami.Output -match "not authenticated|Not logged in") {
        Write-Host "Nessuna sessione attiva: avvio 'wrangler login'..." -ForegroundColor Yellow
        $null = Invoke-Wrangler -Arguments @("login")
    } else {
        Write-Host "Sessione Cloudflare attiva." -ForegroundColor Green
    }

    # 3. Cerca o crea il DB D1 isolato 'ugocalc-db'
    Write-Host "[2/6] Controllo esistenza database D1 '$DbName'..." -ForegroundColor Yellow
    $d1ListRes = Invoke-Wrangler -Arguments @("d1", "list", "--json") -CaptureOutput -IgnoreExitCode
    $existingDb = $null
    if ($d1ListRes.ExitCode -eq 0) {
        try {
            $d1List = ConvertFrom-JsonArrayText -RawText $d1ListRes.Output
            $existingDb = $d1List | Where-Object { $_.name -eq $DbName } | Select-Object -First 1
        } catch {
            $existingDb = $null
        }
    }

    $databaseId = $null
    if ($null -ne $existingDb -and -not [string]::IsNullOrWhiteSpace($existingDb.uuid)) {
        $databaseId = $existingDb.uuid
        Write-Host "Trovato database D1 esistente '$DbName' (ID: $databaseId). Nessun altro DB modificato." -ForegroundColor Green
    } else {
        Write-Host "Creazione nuovo database D1 '$DbName'..." -ForegroundColor Yellow
        $null = Invoke-Wrangler -Arguments @("d1", "create", $DbName)
        $d1ListAfterRes = Invoke-Wrangler -Arguments @("d1", "list", "--json") -CaptureOutput
        $d1ListAfter = ConvertFrom-JsonArrayText -RawText $d1ListAfterRes.Output
        $createdDb = $d1ListAfter | Where-Object { $_.name -eq $DbName } | Select-Object -First 1
        if ($null -eq $createdDb -or [string]::IsNullOrWhiteSpace($createdDb.uuid)) {
            throw "Impossibile leggere l'UUID del database D1 appena creato."
        }
        $databaseId = $createdDb.uuid
        Write-Host "Database D1 '$DbName' creato (ID: $databaseId)." -ForegroundColor Green
    }

    # 4. Aggiorna database_id in wrangler.toml (solo se necessario)
    Write-Host "[3/6] Aggiornamento '$WranglerToml'..." -ForegroundColor Yellow
    $tomlContent = Get-Content -Path $WranglerToml -Raw -Encoding UTF8
    $updatedToml = [regex]::Replace(
        $tomlContent,
        'database_id\s*=\s*"[^"]*"',
        "database_id = `"$databaseId`""
    )
    if ($updatedToml -ne $tomlContent) {
        Set-Content -Path $WranglerToml -Value $updatedToml -NoNewline -Encoding UTF8
        Write-Host "Aggiornato database_id in wrangler.toml." -ForegroundColor Green
    } else {
        Write-Host "wrangler.toml già allineato." -ForegroundColor Green
    }

    # 5. Applica schema.sql in modo idempotente (CREATE TABLE IF NOT EXISTS / INSERT OR IGNORE)
    Write-Host "[4/6] Applicazione schema SQL su '$DbName'..." -ForegroundColor Yellow
    $null = Invoke-Wrangler -Arguments @("d1", "execute", $DbName, "--remote", "--file=$SchemaSql", "--yes")
    Write-Host "Schema SQL applicato correttamente." -ForegroundColor Green

    # 6. Deploy isolato del solo Worker ugocalc-api (deve avvenire PRIMA di secret list/put su un nuovo Worker)
    Write-Host "[5/6] Deploy del Worker '$WorkerName'..." -ForegroundColor Yellow
    $deployRes = Invoke-Wrangler -Arguments @("deploy", "--config", $WranglerToml) -CaptureOutput
    Write-Host $deployRes.Output

    # 7. Configurazione opzionale AUTH_SECRET (solo se richiesto via -SetAuthSecret, mai bloccante)
    if ($SetAuthSecret) {
        Write-Host "[6/6] Configurazione AUTH_SECRET su '$WorkerName'..." -ForegroundColor Yellow
        $manual = Read-Host "Inserisci AUTH_SECRET (lascia vuoto per generarne uno sicuro casuale)"
        if ([string]::IsNullOrWhiteSpace($manual)) {
            $bytes = New-Object byte[] 32
            $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
            try {
                $rng.GetBytes($bytes)
            } finally {
                $rng.Dispose()
            }
            $manual = ([System.BitConverter]::ToString($bytes)).Replace("-", "").ToLowerInvariant()
            Write-Host "Generato AUTH_SECRET casuale (salvalo in un posto sicuro): $manual" -ForegroundColor Cyan
        }
        $prevPref = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $manual | & npx --yes wrangler secret put AUTH_SECRET --config $WranglerToml
            if ($LASTEXITCODE -ne 0) {
                throw "Impostazione AUTH_SECRET fallita (exit code $LASTEXITCODE)."
            }
        } finally {
            $ErrorActionPreference = $prevPref
        }
        Write-Host "AUTH_SECRET salvato sul Worker '$WorkerName'." -ForegroundColor Green
    } else {
        Write-Host "[6/6] AUTH_SECRET saltato (modalità non interattiva; usa -SetAuthSecret se vuoi impostarlo)." -ForegroundColor DarkGray
    }

    $urlMatch = [regex]::Match($deployRes.Output, 'https://[a-zA-Z0-9._-]+\.workers\.dev')
    Write-Host ""
    Write-Host "=== Deploy completato con successo (0 EUR/mese)! ===" -ForegroundColor Green
    if ($urlMatch.Success) {
        $workerUrl = $urlMatch.Value
        Write-Host "URL Worker : $workerUrl" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Incolla questo URL in app/src/main/java/com/ugocalc/app/di/NetworkModule.kt:" -ForegroundColor Yellow
        Write-Host "    private const val AWS_BASE_URL = `"$workerUrl`"" -ForegroundColor White
        Write-Host ""
        try {
            $health = Invoke-RestMethod -Uri "$workerUrl/health" -TimeoutSec 5
            Write-Host "Health check /health OK: $($health | ConvertTo-Json -Compress)" -ForegroundColor Green
        } catch {
            Write-Host "Nota: /health potrebbe richiedere qualche secondo per la propagazione DNS iniziale." -ForegroundColor DarkYellow
        }
    }
}
finally {
    Pop-Location
}
