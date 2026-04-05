param(
    [string]$AndroidSdkRoot = $env:ANDROID_HOME,
    [string]$NdkVersion = "27.3.13750724",
    [string]$NdkArchiveName = "android-ndk-r27d-windows.zip",
    [string]$NdkUrl = "https://dl.google.com/android/repository/android-ndk-r27d-windows.zip"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AndroidSdkRoot)) {
    $AndroidSdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}

$targetDir = Join-Path $AndroidSdkRoot ("ndk\" + $NdkVersion)
if (Test-Path $targetDir) {
    Write-Host "NDK already installed at $targetDir"
    exit 0
}

$tempZip = Join-Path $env:TEMP $NdkArchiveName
$extractRoot = Join-Path $env:TEMP "android-ndk-r27d"

Write-Host "Android SDK root: $AndroidSdkRoot"
Write-Host "Target NDK dir  : $targetDir"
Write-Host "Archive path     : $tempZip"

if (-not (Test-Path $tempZip)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $tempZip) | Out-Null
}

Write-Host "Downloading or resuming the NDK archive..."
& curl.exe -L -C - --retry 5 --retry-delay 5 -o $tempZip $NdkUrl
if ($LASTEXITCODE -ne 0) {
    throw "Failed to download the Android NDK archive."
}

if (-not (Test-Path $extractRoot)) {
    Write-Host "Extracting the NDK archive..."
    & tar.exe -xf $tempZip -C $env:TEMP
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to extract the Android NDK archive."
    }
}

New-Item -ItemType Directory -Force -Path (Split-Path $targetDir) | Out-Null
Move-Item -LiteralPath $extractRoot -Destination $targetDir

Write-Host "Installed NDK to $targetDir"
