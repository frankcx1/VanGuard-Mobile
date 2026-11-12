param([Parameter(Mandatory=$true)][string]$Device)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $env:JAVA_HOME = (Get-ChildItem -LiteralPath '.tools\jdk' -Directory | Select-Object -First 1).FullName
    $env:ANDROID_HOME = Join-Path $projectRoot '.tools\android-sdk'
    $adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
    function Invoke-ProbeAdb {
        & $adb -s $Device @args
        if ($LASTEXITCODE -ne 0) { throw 'ADB probe command failed.' }
    }
    $soc = (Invoke-ProbeAdb shell getprop ro.soc.model).Trim()
    if ($soc -ne 'Tensor G5') { throw "This probe is pinned for Tensor G5, not $soc." }
    $models = @(
        @{ Lock = 'npu-probe\model-lock.json'; DeviceName = 'tensor-g5.litertlm' },
        @{ Lock = 'model-lock.json'; DeviceName = 'cpu-gpu.litertlm' }
    )
    foreach ($entry in $models) {
        $lock = Get-Content -LiteralPath $entry.Lock -Raw | ConvertFrom-Json
        $path = Join-Path $projectRoot "models\$($lock.file)"
        if (-not (Test-Path -LiteralPath $path)) {
            Invoke-WebRequest "https://huggingface.co/$($lock.repository)/resolve/$($lock.revision)/$($lock.file)" -OutFile "$path.partial"
            if ((Get-Item -LiteralPath "$path.partial").Length -ne $lock.bytes -or (Get-FileHash -LiteralPath "$path.partial" -Algorithm SHA256).Hash -ne $lock.sha256) { throw 'Downloaded model verification failed.' }
            Move-Item -LiteralPath "$path.partial" -Destination $path
        }
        if ((Get-Item -LiteralPath $path).Length -ne $lock.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $lock.sha256) { throw 'Stored model verification failed.' }
        $entry.Path = $path
    }
    & '.\gradlew.bat' '-PnpuProbe' ':npu-probe:assembleDebug' ':npu-probe:lintDebug' '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw 'NPU probe build failed.' }
    Invoke-ProbeAdb install -r 'npu-probe\build\outputs\apk\debug\npu-probe-debug.apk'
    Invoke-ProbeAdb shell mkdir -p /data/local/tmp/vfg-npu-probe
    Invoke-ProbeAdb shell run-as com.vanfieldguide.npuprobe mkdir -p no_backup
    foreach ($entry in $models) {
        $remote = "/data/local/tmp/vfg-npu-probe/$($entry.DeviceName)"
        Invoke-ProbeAdb push $entry.Path $remote
        Invoke-ProbeAdb shell run-as com.vanfieldguide.npuprobe cp $remote "no_backup/$($entry.DeviceName)"
        Invoke-ProbeAdb shell rm $remote
    }
    $outputDir = Join-Path $projectRoot '.tools\npu-investigation'
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
    foreach ($mode in @('cpu-text', 'cpu-image', 'npu-text', 'npu-image')) {
        Invoke-ProbeAdb shell am instrument -w -e mode $mode com.vanfieldguide.npuprobe/.NpuProbeRunner
        $report = Invoke-ProbeAdb exec-out run-as com.vanfieldguide.npuprobe cat "no_backup/$mode.json"
        $report | Set-Content -LiteralPath (Join-Path $outputDir "$mode.json") -Encoding utf8
        if (($report -join "`n" | ConvertFrom-Json).stage -ne 'success') { throw "$mode did not complete successfully. Inspect the report and logcat." }
    }
    Write-Output "Reports: $outputDir. These are model microbenchmarks, not complete app latency or retrieval accuracy."
} finally { Pop-Location }
