param(
    [string]$AndroidSdkRoot = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Output = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path $PSScriptRoot -Parent
if ([string]::IsNullOrWhiteSpace($AndroidSdkRoot)) {
    $AndroidSdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $JavaHome = "C:\Program Files\Android\Android Studio\jbr"
}
if ([string]::IsNullOrWhiteSpace($Output)) {
    $Output = Join-Path $repoRoot "android\app\libs\qqzone-mobile.aar"
}

$ndkRoot = Join-Path $AndroidSdkRoot "ndk"
if (-not (Test-Path $ndkRoot)) {
    throw "Android NDK was not found under $ndkRoot. Run scripts/install_android_ndk.ps1 first."
}

$latestNdk = Get-ChildItem $ndkRoot -Directory | Sort-Object Name -Descending | Select-Object -First 1
if ($null -eq $latestNdk) {
    throw "Android NDK was not found under $ndkRoot. Run scripts/install_android_ndk.ps1 first."
}

if (-not (Test-Path $JavaHome)) {
    throw "JAVA_HOME was not found at $JavaHome."
}

New-Item -ItemType Directory -Force -Path (Split-Path $Output) | Out-Null

$env:ANDROID_HOME = $AndroidSdkRoot
$env:JAVA_HOME = $JavaHome
$env:ANDROID_NDK_HOME = $latestNdk.FullName

$goPath = (go env GOPATH).Trim()
if (-not [string]::IsNullOrWhiteSpace($goPath)) {
    $goBin = Join-Path $goPath "bin"
    if (Test-Path $goBin) {
        $env:PATH = $goBin + ";" + $env:PATH
    }
}

Push-Location $repoRoot
try {
    Write-Host "Initializing gomobile with SDK $AndroidSdkRoot and NDK $($latestNdk.FullName)..."
    go run golang.org/x/mobile/cmd/gomobile@latest init
    if ($LASTEXITCODE -ne 0) {
        throw "gomobile init failed."
    }

    Write-Host "Binding mobile/bridge into $Output ..."
    $bindArgs = @(
        "run",
        "golang.org/x/mobile/cmd/gomobile@latest",
        "bind",
        "-target=android",
        "-androidapi=26",
        "-javapkg=com.qzone.mobile",
        "-o=$Output",
        "./mobile/bridge"
    )
    & go @bindArgs
    if ($LASTEXITCODE -ne 0) {
        throw "gomobile bind failed."
    }
} finally {
    Pop-Location
}

Write-Host "AAR generated at $Output"
