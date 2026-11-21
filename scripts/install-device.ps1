param(
    [string]$Serial = "",
    [string]$Apk = "E:\github\ei-void\android\app\build\outputs\apk\debug\app-debug.apk"
)

$ErrorActionPreference = "Stop"
$adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "C:\Users\DELL\Android\Sdk\platform-tools\adb.exe"
}
if (-not (Test-Path $adb)) {
    throw "adb not found. Set ANDROID_HOME or install Android platform-tools."
}
if (-not (Test-Path $Apk)) {
    throw "APK not found at $Apk. Build it first: cd android; .\gradlew.bat :app:assembleDebug"
}

& $adb start-server | Out-Null
$devices = & $adb devices -l | Select-String " device "
if (-not $devices) {
    throw "No authorized Android device. Unlock the phone and accept the USB debugging prompt."
}

if (-not $Serial) {
    $Serial = (($devices | Select-Object -First 1) -split "\s+")[0]
}

Write-Host "Installing on $Serial ..."
& $adb -s $Serial install -r $Apk
if ($LASTEXITCODE -ne 0) {
    throw "Install failed. On MIUI enable Developer options > Install via USB and USB debugging (Security settings)."
}

& $adb -s $Serial shell am force-stop com.eivoid.app
& $adb -s $Serial shell am start -n com.eivoid.app/.MainActivity
Start-Sleep -Seconds 4
$shot = Join-Path $PSScriptRoot "..\docs\qa\eivoid-latest.png"
& $adb -s $Serial shell screencap -p /sdcard/eivoid-latest.png
& $adb -s $Serial pull /sdcard/eivoid-latest.png $shot | Out-Null
Write-Host "Installed and launched. Screenshot: $shot"
