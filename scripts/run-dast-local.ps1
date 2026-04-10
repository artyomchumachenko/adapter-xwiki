param(
    [string]$EnvFile = ".env.dast.local",
    [string]$ComposeFile = "docker-compose.dast.local.yml",
    [string]$AppBaseUrl = "http://localhost:8081",
    [string]$ScannerBaseUrl = "",
    [string]$ReportDir = "reports/dast",
    [switch]$UseExistingContainers = $true,
    [switch]$RunGate = $true
)

$ErrorActionPreference = "Stop"

function Import-EnvFile {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        $fallback = ".env.dast.local.example"
        if (Test-Path -LiteralPath $fallback) {
            Write-Host "Environment file '$Path' was not found. Using '$fallback'."
            $Path = $fallback
        }
        else {
            throw "Environment file '$Path' was not found."
        }
    }

    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }
        $parts = $line -split "=", 2
        if ($parts.Count -eq 2) {
            [Environment]::SetEnvironmentVariable($parts[0], $parts[1], "Process")
        }
    }
}

function Wait-HttpOk {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 180
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                return
            }
        } catch {
            Start-Sleep -Seconds 3
        }
    }
    throw "Timeout waiting for URL '$Url'."
}

function Get-PythonCommand {
    if (Get-Command python -ErrorAction SilentlyContinue) {
        return "python"
    }
    if (Get-Command py -ErrorAction SilentlyContinue) {
        return "py -3"
    }
    throw "Python executable was not found."
}

New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null
New-Item -ItemType Directory -Path "$ReportDir\zap" -Force | Out-Null

Import-EnvFile -Path $EnvFile

if ($AppBaseUrl -eq "http://localhost:8081" -and $env:SERVER_PORT) {
    $AppBaseUrl = "http://localhost:$($env:SERVER_PORT)"
}

if (-not $ScannerBaseUrl) {
    $ScannerBaseUrl = $AppBaseUrl `
        -replace "://localhost(?=[:/]|$)", "://host.docker.internal" `
        -replace "://127\.0\.0\.1(?=[:/]|$)", "://host.docker.internal"
}

if (-not $UseExistingContainers) {
    Write-Host "Starting local infrastructure from $ComposeFile"
    docker compose --env-file $EnvFile -f $ComposeFile up -d
}

$healthUrl = "$AppBaseUrl/actuator/health"
Write-Host "Starting Spring Boot app on host for DAST profile"
$appProcess = Start-Process -FilePath ".\mvnw.cmd" `
    -ArgumentList "spring-boot:run -Dspring-boot.run.profiles=dast-local" `
    -PassThru `
    -NoNewWindow

try {
    Write-Host "Waiting for application health at $healthUrl"
    Wait-HttpOk -Url $healthUrl -TimeoutSeconds 240

    $searchTarget = "$ScannerBaseUrl/api/xwiki/search?query=security-test&limit=1"
    $excludeConfig = "-config globalexcludeurl.url_list.url(0).regex=.*\/api\/(?!xwiki\/search(\?.*)?$).* " +
                     "-config globalexcludeurl.url_list.url(0).description=exclude-non-search-api " +
                     "-config globalexcludeurl.url_list.url(0).enabled=true"

    Write-Host "Running OWASP ZAP baseline scan against $searchTarget"
    docker run --rm `
        -v "${PWD}:/zap/wrk" `
        ghcr.io/zaproxy/zaproxy:stable `
        zap-baseline.py `
        -t $searchTarget `
        -m 3 `
        -J "reports/dast/zap-report.json" `
        -r "reports/dast/zap-report.html" `
        -I `
        -z $excludeConfig

    $targetsPath = Join-Path $ReportDir "nuclei-targets.txt"
    $paths = Get-Content -LiteralPath "scripts/dast-api-targets.txt" | Where-Object { $_ -and -not $_.StartsWith("#") }
    $fullTargets = $paths | ForEach-Object { "$ScannerBaseUrl$_" }
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines((Resolve-Path $targetsPath), $fullTargets, $utf8NoBom)

    Write-Host "Running Nuclei scan"
    docker run --rm `
        -v "${PWD}:/workspace" `
        projectdiscovery/nuclei:latest `
        -l /workspace/reports/dast/nuclei-targets.txt `
        -json-export /workspace/reports/dast/nuclei-report.json

    if ($RunGate) {
        Write-Host "Running DAST security gate"
        $pythonCmd = Get-PythonCommand
        Invoke-Expression "$pythonCmd scripts/security_gate.py --mode dast --zap-report reports/dast/zap-report.json --nuclei-report reports/dast/nuclei-report.json --dast-suppressions reports/dast/suppressions.yml"
    }
}
finally {
    if ($appProcess -and -not $appProcess.HasExited) {
        Write-Host "Stopping Spring Boot process (PID=$($appProcess.Id))"
        Stop-Process -Id $appProcess.Id -Force
    }
}

Write-Host "DAST local run completed. Reports: $ReportDir"
