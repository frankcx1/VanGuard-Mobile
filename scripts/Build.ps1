param([switch]$Install, [string]$Device)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $localJdk = Join-Path $projectRoot '.tools\jdk'
    if (Test-Path -LiteralPath $localJdk) { $env:JAVA_HOME = (Get-ChildItem -LiteralPath $localJdk -Directory | Select-Object -First 1).FullName }
    $localSdk = Join-Path $projectRoot '.tools\android-sdk'
    if (Test-Path -LiteralPath $localSdk) { $env:ANDROID_HOME = $localSdk }
    & '.\gradlew.bat' ':app:assembleDebug' ':app:lintDebug' '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw 'Android build or lint failed.' }
    $analyzer = Join-Path $env:ANDROID_HOME 'build-tools\35.0.0\aapt.exe'
    $apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $analyzer)) { throw 'Install Android build-tools 35.0.0 for the APK permission audit.' }
    $permissions = & $analyzer dump permissions $apk
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect APK permissions.' }
    $permissions
    if ($permissions -match 'android.permission.INTERNET') { throw 'APK unexpectedly requests Internet access.' }
    if ($Install) {
        $adbPath = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
        if ($Device) { & $adbPath -s $Device install -r $apk }
        else { & $adbPath install -r $apk }
        if ($LASTEXITCODE -ne 0) { throw 'APK installation failed. Check the USB connection and debugging authorization.' }
    }
} finally { Pop-Location }
