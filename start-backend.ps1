<#
.SYNOPSIS
    Starts the StaffSuite backend for local development.

.DESCRIPTION
    Sets the environment variables the backend needs, asks for your MySQL password (it is never written
    to disk), checks the port is free, then runs the backend.

    Any variable you have already set in your terminal is kept, so you can override the defaults below,
    for example:  $env:SERVER_PORT = "8085"; .\start-backend.ps1

.PARAMETER DryRun
    Show what would be used and check the port, without starting anything or asking for a password.

.EXAMPLE
    .\start-backend.ps1
#>
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'

$backend = Join-Path $PSScriptRoot 'backend'
if (-not (Test-Path (Join-Path $backend 'mvnw.cmd'))) {
    throw "Cannot find the backend folder at $backend. Run this script from the StaffSuite project folder."
}

# Keeps a value you already set in this terminal; otherwise uses the local-development default.
function Set-Default([string]$Name, [string]$Value) {
    if (-not (Get-Item "Env:$Name" -ErrorAction SilentlyContinue)) {
        Set-Item "Env:$Name" $Value
    }
}

Set-Default 'SERVER_PORT'          '8081'
Set-Default 'DB_URL'               'jdbc:mysql://localhost:3306/staffsuite?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
Set-Default 'DB_USERNAME'          'root'
# Development-only signing key. Anyone who knows it can forge logins, so never reuse it outside your own machine.
Set-Default 'JWT_SECRET'           'dev-only-secret-for-local-use-change-before-any-real-deployment'
Set-Default 'JWT_EXPIRY_MS'        '3600000'
Set-Default 'FILE_STORAGE_PATH'    './uploads'
Set-Default 'CORS_ALLOWED_ORIGINS' 'http://localhost:5173'

if (-not $env:DB_PASSWORD) {
    if ($DryRun) {
        $env:DB_PASSWORD = '(not asked in a dry run)'
    } else {
        $secure = Read-Host "MySQL password for user '$($env:DB_USERNAME)'" -AsSecureString
        $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR(
            [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
    }
}

Write-Host ''
Write-Host 'StaffSuite backend' -ForegroundColor Cyan
Write-Host "  Port ............ $($env:SERVER_PORT)"
Write-Host "  Database ........ $($env:DB_URL)"
Write-Host "  DB user ......... $($env:DB_USERNAME)   (password: hidden)"
Write-Host "  Files folder .... $([IO.Path]::GetFullPath((Join-Path $backend $env:FILE_STORAGE_PATH)))"
Write-Host "  Allowed origin .. $($env:CORS_ALLOWED_ORIGINS)"
Write-Host ''

# A second copy on the same port would fail with "Port already in use"; say so plainly instead.
$busy = Get-NetTCPConnection -LocalPort ([int]$env:SERVER_PORT) -State Listen -ErrorAction SilentlyContinue
if ($busy) {
    $owner = Get-Process -Id $busy[0].OwningProcess -ErrorAction SilentlyContinue
    Write-Host "Port $($env:SERVER_PORT) is already in use by '$($owner.ProcessName)' (PID $($busy[0].OwningProcess))." -ForegroundColor Yellow
    Write-Host 'If that is an old StaffSuite backend, close its terminal (or press Ctrl+C there) and run this again.'
    Write-Host 'To use another port instead:  $env:SERVER_PORT = "8085"; .\start-backend.ps1  (and change API_BASE_URL in frontend\public\config.js).'
    exit 1
}

if ($DryRun) {
    Write-Host 'Dry run: nothing was started. Port is free.' -ForegroundColor Green
    exit 0
}

Write-Host "Starting... wait for 'Started StaffsuiteBackendApplication', then open http://localhost:5173" -ForegroundColor Green
Write-Host 'Press Ctrl+C to stop it.'
Write-Host ''

Push-Location $backend
try {
    & .\mvnw.cmd spring-boot:run
} finally {
    Pop-Location
}
