param(
    [string]$AndroidSdkRoot = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$SkipBind
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path $PSScriptRoot -Parent
$androidRoot = Join-Path $repoRoot "android"

if ([string]::IsNullOrWhiteSpace($AndroidSdkRoot)) {
    $AndroidSdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $JavaHome = "C:\Program Files\Android\Android Studio\jbr"
}

$env:ANDROID_HOME = $AndroidSdkRoot
$env:JAVA_HOME = $JavaHome

if (-not $SkipBind) {
    & (Join-Path $PSScriptRoot "bind_android_bridge.ps1") -AndroidSdkRoot $AndroidSdkRoot -JavaHome $JavaHome
}

$escapedSdk = $AndroidSdkRoot.Replace("\", "\\")
Set-Content -LiteralPath (Join-Path $androidRoot "local.properties") -Value "sdk.dir=$escapedSdk"

$gradleVersion = "8.2.1"
$gradleZip = Join-Path $env:TEMP ("gradle-" + $gradleVersion + "-bin.zip")
$gradleHome = Join-Path $env:TEMP ("gradle-" + $gradleVersion)

if (-not (Test-Path $gradleHome)) {
    Write-Host "Downloading or resuming Gradle $gradleVersion ..."
    & curl.exe -L -C - --retry 5 --retry-delay 5 -o $gradleZip ("https://services.gradle.org/distributions/gradle-" + $gradleVersion + "-bin.zip")
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to download Gradle $gradleVersion."
    }

    Write-Host "Extracting Gradle $gradleVersion ..."
    & tar.exe -xf $gradleZip -C $env:TEMP
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to extract Gradle $gradleVersion."
    }
}

Push-Location $androidRoot
try {
    & (Join-Path $gradleHome "bin\gradle.bat") assembleDebug
} finally {
    Pop-Location
}

Write-Host "Debug APK should be available at $androidRoot\app\build\outputs\apk\debug\app-debug.apk"
