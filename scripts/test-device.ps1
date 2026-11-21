param([string]$Serial = "")

$ErrorActionPreference = "Stop"
$adb = "C:\Users\DELL\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    throw "adb not found. Set ANDROID_HOME or install Android platform-tools."
}

$devices = & $adb devices -l | Select-String " device "
if (-not $devices) {
    throw "No authorized Android device."
}
if (-not $Serial) {
    $Serial = (($devices | Select-Object -First 1) -split "\s+")[0]
}

$env:ANDROID_SERIAL = $Serial
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot"
$env:ANDROID_HOME = "C:\Users\DELL\Android\Sdk"

Push-Location (Join-Path $PSScriptRoot "..\android")
try {
    & .\gradlew.bat :core:reminders:connectedDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & .\gradlew.bat :app:connectedDebugAndroidTest --console=plain
} finally {
    Pop-Location
}
