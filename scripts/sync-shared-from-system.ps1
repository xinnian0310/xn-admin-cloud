# Sync shared packages from xn-system to xn-file / xn-log / xn-job.
# Usage (repo root):
#   .\scripts\sync-shared-from-system.ps1
#   .\scripts\sync-shared-from-system.ps1 -Check
param(
    [switch]$Check
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Src = Join-Path $Root "xn-system\src\main\java\com\smartadmin"
$Targets = @("xn-file", "xn-log", "xn-job")
$Packages = @(
    "common", "config", "security", "service", "util",
    "websocket", "monitor", "scheduler", "entity", "dto", "repository"
)

$diffCount = 0
$copyCount = 0

foreach ($pkg in $Packages) {
    $from = Join-Path $Src $pkg
    if (-not (Test-Path $from)) { continue }
    $files = Get-ChildItem -Path $from -Recurse -Filter *.java -File
    foreach ($t in $Targets) {
        $toRoot = Join-Path $Root "$t\src\main\java\com\smartadmin\$pkg"
        foreach ($f in $files) {
            $rel = $f.FullName.Substring($from.Length).TrimStart('\', '/')
            $dest = Join-Path $toRoot $rel
            $destDir = Split-Path $dest -Parent
            if (-not (Test-Path $destDir)) {
                New-Item -ItemType Directory -Force -Path $destDir | Out-Null
            }
            if (-not (Test-Path $dest)) {
                if ($Check) {
                    Write-Host "MISSING ${t}/${pkg}/${rel}"
                    $diffCount++
                } else {
                    Copy-Item $f.FullName $dest -Force
                    Write-Host "ADD ${t}/${pkg}/${rel}"
                    $copyCount++
                }
                continue
            }
            $srcHash = (Get-FileHash $f.FullName -Algorithm SHA256).Hash
            $dstHash = (Get-FileHash $dest -Algorithm SHA256).Hash
            if ($srcHash -ne $dstHash) {
                if ($Check) {
                    Write-Host "DRIFT ${t}/${pkg}/${rel}"
                    $diffCount++
                } else {
                    Copy-Item $f.FullName $dest -Force
                    Write-Host "SYNC ${t}/${pkg}/${rel}"
                    $copyCount++
                }
            }
        }
    }
}

if ($Check) {
    if ($diffCount -gt 0) {
        Write-Host "FAILED: $diffCount drifted/missing file(s). Run without -Check to sync."
        exit 1
    }
    Write-Host "OK: no drift"
} else {
    Write-Host "DONE: synced $copyCount file(s)"
    Write-Host "Rule: edit shared/security code in xn-system, then run this script."
}
