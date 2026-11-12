param([switch]$IncludeNpu)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$lock = Get-Content -LiteralPath (Join-Path $projectRoot 'model-lock.json') -Raw | ConvertFrom-Json
$modelFolder = Join-Path $projectRoot 'models'
New-Item -ItemType Directory -Path $modelFolder -Force | Out-Null
foreach ($artifact in @($lock) + $(if ($IncludeNpu) { @($lock.npu) } else { @() })) {
$destination = Join-Path $modelFolder $artifact.file
if (-not (Test-Path -LiteralPath $destination)) {
    Invoke-WebRequest "https://huggingface.co/$($artifact.repository)/resolve/$($artifact.revision)/$($artifact.file)" -OutFile "$destination.partial"
    if ((Get-Item -LiteralPath "$destination.partial").Length -ne $artifact.bytes -or (Get-FileHash -LiteralPath "$destination.partial" -Algorithm SHA256).Hash -ne $artifact.sha256) { throw 'Model verification failed; partial file retained for inspection.' }
    Move-Item -LiteralPath "$destination.partial" -Destination $destination
}
if ((Get-Item -LiteralPath $destination).Length -ne $artifact.bytes -or (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ne $artifact.sha256) { throw 'Stored model verification failed.' }
Write-Output "Verified model: $destination"
}
