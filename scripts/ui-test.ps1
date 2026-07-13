[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('capture', 'compare', 'accept')]
    [string]$Command = 'capture',
    [ValidateSet('scrybe', 'shelf-snap', 'price-drop', 'all')]
    [string]$App = 'all',
    [string]$Avd = 'scrybe-api35',
    [string]$Serial,
    [string]$RunId = (Get-Date -Format 'yyyyMMdd-HHmmss')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$AndroidScript = Join-Path $PSScriptRoot 'android.ps1'
$ArtifactRoot = Join-Path $RepoRoot ".artifacts\ui-tests\$RunId"
$BaselineRoot = Join-Path $RepoRoot 'ui-baselines'
$ManifestPath = Join-Path $BaselineRoot 'manifest.json'
$BaselineApi = "api$((Get-Content -Raw $ManifestPath | ConvertFrom-Json).device.api)"
$AndroidHome = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $HOME 'scoop\apps\android-clt\current' }
$Adb = Join-Path $AndroidHome 'platform-tools\adb.exe'
$Definitions = [ordered]@{
    'scrybe' = @{ Package = 'dev.scrybe.android'; TestPackage = 'dev.scrybe.android.test'; Class = 'dev.scrybe.android.UiScreenshotMatrixTest'; Root = 'apps\scrybe' }
    'shelf-snap' = @{ Package = 'com.shelfsnap.app'; TestPackage = 'com.shelfsnap.app.test'; Class = 'com.shelfsnap.app.UiScreenshotMatrixTest'; Root = 'apps\shelf-snap' }
    'price-drop' = @{ Package = 'com.twobits.pricedrop'; TestPackage = 'com.twobits.pricedrop.test'; Class = 'com.twobits.pricedrop.UiScreenshotMatrixTest'; Root = 'apps\price-drop' }
}

function RequestedApps { if ($App -eq 'all') { @($Definitions.Keys) } else { @($App) } }

function Resolve-Serial {
    if ($Serial) { return $Serial }
    $booted = & $AndroidScript boot -Avd $Avd
    if ($LASTEXITCODE -ne 0) { throw "Could not boot $Avd." }
    return @($booted)[-1].ToString().Trim()
}

function Capture-Matrix {
    $target = Resolve-Serial
    New-Item -ItemType Directory -Force -Path $ArtifactRoot | Out-Null
    $originalWindowScale = (& $Adb -s $target shell settings get global window_animation_scale).Trim()
    $originalTransitionScale = (& $Adb -s $target shell settings get global transition_animation_scale).Trim()
    $originalAnimatorScale = (& $Adb -s $target shell settings get global animator_duration_scale).Trim()
    $originalFontScale = (& $Adb -s $target shell settings get system font_scale).Trim()
    $originalSize = (& $Adb -s $target shell wm size | Out-String)
    $originalDensity = (& $Adb -s $target shell wm density | Out-String)
    $originalNightMode = (& $Adb -s $target shell cmd uimode night | Out-String)
    $originalTimeZone = (& $Adb -s $target shell getprop persist.sys.timezone).Trim()
    try {
        foreach ($name in RequestedApps) {
            $definition = $Definitions[$name]
            foreach ($theme in @('light', 'dark')) {
                Write-Host "Capturing $name / $theme on $target"
                & $Adb -s $target shell pm clear $definition.Package | Out-Null
                & $Adb -s $target shell cmd uimode night $(if ($theme -eq 'dark') { 'yes' } else { 'no' }) | Out-Null
                $env:ANDROID_SERIAL = $target
                & $AndroidScript install -App $name -Serial $target
                if ($LASTEXITCODE -ne 0) { throw "Failed to install $name." }
                $appRoot = Join-Path $RepoRoot $definition.Root
                $gradle = Join-Path $appRoot 'gradlew.bat'
                $env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { Join-Path $HOME 'scoop\apps\openjdk17\current' }
                $env:ANDROID_HOME = $AndroidHome
                $env:ANDROID_USER_HOME = Join-Path $RepoRoot '.android-user-home'
                $env:ANDROID_AVD_HOME = Join-Path $HOME '.android\avd'
                $env:GRADLE_USER_HOME = Join-Path $RepoRoot '.gradle-user-home'
                & $gradle -p $appRoot ':app:assembleDebugAndroidTest' --project-cache-dir (Join-Path $RepoRoot ".gradle-project-cache\$name") '-Pkotlin.compiler.execution.strategy=in-process' --no-build-cache --no-configuration-cache --no-daemon --console=plain
                if ($LASTEXITCODE -ne 0) { throw "Failed to assemble the $name test APK." }
                $testApk = Get-ChildItem (Join-Path $appRoot 'app\build\outputs\apk\androidTest\debug') -Filter '*.apk' | Select-Object -First 1
                if (-not $testApk) { throw "No androidTest APK found for $name." }
                & $Adb -s $target install -r $testApk.FullName | Out-Null
                if ($LASTEXITCODE -ne 0) { throw "Failed to install the $name test APK." }
                $instrumentationOutput = @(& $Adb -s $target shell am instrument -w -e class $definition.Class -e theme $theme "$($definition.TestPackage)/androidx.test.runner.AndroidJUnitRunner" 2>&1)
                $instrumentationOutput | Tee-Object -FilePath (Join-Path $ArtifactRoot "$name-$theme-instrumentation.txt")
                if ($LASTEXITCODE -ne 0 -or ($instrumentationOutput -join "`n") -notmatch 'OK \([0-9]+ test') {
                    throw "Instrumentation failed for $name / $theme. See the run's instrumentation log."
                }
                $destination = Join-Path $ArtifactRoot "$name\actual\$theme"
                New-Item -ItemType Directory -Force -Path $destination | Out-Null
                & $Adb -s $target pull "/sdcard/Android/data/$($definition.Package)/files/ui-test/$theme/." $destination | Out-Null
                & $Adb -s $target logcat -d | Set-Content -Encoding utf8 (Join-Path $ArtifactRoot "$name-$theme-logcat.txt")
                & $Adb -s $target shell uiautomator dump /sdcard/twobits-window.xml | Out-Null
                & $Adb -s $target pull /sdcard/twobits-window.xml (Join-Path $ArtifactRoot "$name-$theme-window.xml") | Out-Null
            }
        }
    } finally {
        & $Adb -s $target shell settings put global window_animation_scale $originalWindowScale | Out-Null
        & $Adb -s $target shell settings put global transition_animation_scale $originalTransitionScale | Out-Null
        & $Adb -s $target shell settings put global animator_duration_scale $originalAnimatorScale | Out-Null
        & $Adb -s $target shell settings put system font_scale $originalFontScale | Out-Null
        if ($originalSize -match 'Override size: ([0-9]+x[0-9]+)') { & $Adb -s $target shell wm size $Matches[1] | Out-Null } else { & $Adb -s $target shell wm size reset | Out-Null }
        if ($originalDensity -match 'Override density: ([0-9]+)') { & $Adb -s $target shell wm density $Matches[1] | Out-Null } else { & $Adb -s $target shell wm density reset | Out-Null }
        if ($originalNightMode -match 'Night mode: (yes|no|auto|custom)') { & $Adb -s $target shell cmd uimode night $Matches[1] | Out-Null }
        if ($originalTimeZone) { & $Adb -s $target shell cmd alarm set-timezone $originalTimeZone | Out-Null }
    }
    Write-Host "Captured UI artifacts in $ArtifactRoot"
}

function Get-MaskDrawCommands([string]$relativePath) {
    if (-not (Test-Path $ManifestPath)) { return @() }
    $manifest = Get-Content -Raw $ManifestPath | ConvertFrom-Json
    $result = @()
    foreach ($mask in $manifest.masks) {
        if ($relativePath -like $mask.pattern) {
            foreach ($rectangle in $mask.rectangles) {
                $values = @($rectangle)
                $result += "rectangle $($values[0]),$($values[1]) $($values[2]),$($values[3])"
            }
        }
    }
    return $result
}

function New-MaskedImage([string]$source, [string]$destination, [string]$relativePath) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
    $drawCommands = @(Get-MaskDrawCommands $relativePath)
    if (-not $drawCommands.Count) { Copy-Item $source $destination -Force; return }
    $draw = $drawCommands -join ' '
    & magick $source -fill black -draw $draw $destination
    if ($LASTEXITCODE -ne 0) { throw "Could not mask $relativePath." }
}

function Compare-Matrix {
    if (-not (Get-Command magick -ErrorAction SilentlyContinue)) { throw 'ImageMagick (magick) is required.' }
    $actualRoot = $ArtifactRoot
    if (-not (Test-Path $actualRoot)) { throw "Run artifacts do not exist: $actualRoot" }
    $results = @()
    foreach ($appName in RequestedApps) {
        $appActualRoot = Join-Path $ArtifactRoot "$appName\actual"
        if (-not (Test-Path $appActualRoot)) { continue }
        foreach ($file in Get-ChildItem $appActualRoot -Recurse -Filter '*.png') {
            $afterActual = $file.FullName.Substring($appActualRoot.Length + 1)
            $relative = "$appName/$($afterActual -replace '\\','/')"
            $baseline = Join-Path $BaselineRoot "$appName\$BaselineApi\$afterActual"
            if (-not (Test-Path $baseline)) {
                $results += [pscustomobject]@{ Image = $relative; Status = 'missing-baseline'; ChangedPixels = $null; Limit = $null }
                continue
            }
            $maskedActual = Join-Path $ArtifactRoot "masked\actual\$relative"
            $maskedBaseline = Join-Path $ArtifactRoot "masked\baseline\$relative"
            $diff = Join-Path $ArtifactRoot "diff\$relative"
            New-MaskedImage $file.FullName $maskedActual $relative
            New-MaskedImage $baseline $maskedBaseline $relative
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $diff) | Out-Null
            $dimensions = (& magick identify -format '%w %h' $maskedActual).Trim() -split ' '
            $baselineDimensions = (& magick identify -format '%w %h' $maskedBaseline).Trim() -split ' '
            if (($dimensions -join 'x') -ne ($baselineDimensions -join 'x')) {
                $results += [pscustomobject]@{ Image = $relative; Status = 'dimension-mismatch'; ChangedPixels = $null; Limit = 0 }
                continue
            }
            $metricOutput = (& magick compare -metric AE -fuzz '2%' $maskedBaseline $maskedActual $diff 2>&1 | Out-String).Trim()
            $changed = if ($metricOutput -match '([0-9]+)') { [long]$Matches[1] } else { 0L }
            $limit = [math]::Floor(([long]$dimensions[0] * [long]$dimensions[1]) * 0.0025)
            $status = if ($changed -le $limit) { 'pass' } else { 'fail' }
            $results += [pscustomobject]@{ Image = $relative; Status = $status; ChangedPixels = $changed; Limit = $limit }
        }
    }
    $results | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $ArtifactRoot 'report.json')
    @('# TwoBits UI comparison', '', '| Image | Status | Changed pixels | Limit |', '|---|---:|---:|---:|') +
        @($results | ForEach-Object { "| $($_.Image) | $($_.Status) | $($_.ChangedPixels) | $($_.Limit) |" }) |
        Set-Content -Encoding utf8 (Join-Path $ArtifactRoot 'report.md')
    $results | Format-Table -AutoSize
    if ($results.Status -contains 'fail' -or $results.Status -contains 'missing-baseline' -or $results.Status -contains 'dimension-mismatch') {
        throw "UI comparison failed. See $(Join-Path $ArtifactRoot 'report.md')."
    }
}

function Accept-Matrix {
    $changes = @()
    foreach ($appName in RequestedApps) {
        $appActualRoot = Join-Path $ArtifactRoot "$appName\actual"
        if (-not (Test-Path $appActualRoot)) { continue }
        foreach ($file in Get-ChildItem $appActualRoot -Recurse -Filter '*.png') {
            $afterActual = $file.FullName.Substring($appActualRoot.Length + 1)
            $relative = "$appName/$($afterActual -replace '\\','/')"
            $changes +=
                [pscustomobject]@{
                    Image = $relative
                    Source = $file.FullName
                    Destination = (Join-Path $BaselineRoot "$appName\$BaselineApi\$afterActual")
                }
        }
    }
    if (-not $changes.Count) { throw "No captured PNGs found in $ArtifactRoot." }
    Write-Host 'Accepting these baseline images:'
    $changes.Image | Sort-Object | ForEach-Object { Write-Host "  $_" }
    foreach ($change in $changes) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $change.Destination) | Out-Null
        Copy-Item $change.Source $change.Destination -Force
    }
}

switch ($Command) {
    'capture' { Capture-Matrix }
    'compare' { Compare-Matrix }
    'accept' { Accept-Matrix }
}
