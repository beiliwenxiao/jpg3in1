# PHP Configuration Fix Script
# This script fixes PHP extension configuration

$phpDir = "E:\php-8.1.34-Win32-vs16-x64"
$phpIni = "$phpDir\php.ini"
$extDir = "$phpDir\ext"

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "PHP Configuration Fix" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "PHP Directory: $phpDir" -ForegroundColor Yellow
Write-Host "PHP INI: $phpIni" -ForegroundColor Yellow
Write-Host "Extension Directory: $extDir" -ForegroundColor Yellow
Write-Host ""

# Check if php.ini exists
if (-not (Test-Path $phpIni)) {
    Write-Host "[ERROR] php.ini not found" -ForegroundColor Red
    $template = "$phpDir\php.ini-development"
    if (Test-Path $template) {
        Copy-Item $template $phpIni
        Write-Host "[OK] Created php.ini from template" -ForegroundColor Green
    } else {
        Write-Host "[ERROR] No template found" -ForegroundColor Red
        exit 1
    }
}

# Backup
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backup = "$phpIni.backup.$timestamp"
Copy-Item $phpIni $backup -ErrorAction SilentlyContinue
Write-Host "[OK] Backup: $backup" -ForegroundColor Green
Write-Host ""

# List available extensions
Write-Host "Available extensions:" -ForegroundColor Cyan
Write-Host "------------------------------------"
$dlls = Get-ChildItem "$extDir\php_*.dll" -ErrorAction SilentlyContinue | Sort-Object Name
$dlls | ForEach-Object { Write-Host $_.Name }
Write-Host ""

# Read php.ini
Write-Host "Reading php.ini..." -ForegroundColor Cyan
$content = Get-Content $phpIni -Raw -Encoding UTF8

# Remove old extension lines
Write-Host "Cleaning old configurations..." -ForegroundColor Yellow
$lines = $content -split "`n"
$newLines = @()
foreach ($line in $lines) {
    if ($line -notmatch '^\s*;?\s*(extension|zend_extension)\s*=') {
        $newLines += $line
    }
}
$content = $newLines -join "`n"

# Set extension_dir
Write-Host "Setting extension_dir..." -ForegroundColor Yellow
if ($content -match 'extension_dir') {
    $content = $content -replace '(?m)^;?\s*extension_dir\s*=.*$', "extension_dir = `"$extDir`""
} else {
    $content = "extension_dir = `"$extDir`"`n" + $content
}

# Add extensions
Write-Host ""
Write-Host "Adding extensions..." -ForegroundColor Cyan
$content += "`n`n; ========================================`n"
$content += "; Extensions (Auto-configured)`n"
$content += "; ========================================`n"

foreach ($dll in $dlls) {
    $name = $dll.Name -replace '^php_', '' -replace '\.dll$', ''
    if ($name -eq 'opcache') {
        $content += "zend_extension=$($dll.Name)`n"
        Write-Host "  [OK] $name (zend)" -ForegroundColor Green
    } else {
        $content += "extension=$($dll.Name)`n"
        Write-Host "  [OK] $name" -ForegroundColor Green
    }
}

# Save
Write-Host ""
Write-Host "Saving configuration..." -ForegroundColor Cyan
$content | Set-Content $phpIni -NoNewline -Encoding UTF8
Write-Host "[OK] Configuration saved!" -ForegroundColor Green

# Verify
Write-Host ""
Write-Host "====================================" -ForegroundColor Cyan
Write-Host "Verification" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "PHP Version:" -ForegroundColor Yellow
& php -v
Write-Host ""

Write-Host "Loaded Extensions:" -ForegroundColor Yellow
& php -m
Write-Host ""

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "Done!" -ForegroundColor Green
Write-Host "====================================" -ForegroundColor Cyan
