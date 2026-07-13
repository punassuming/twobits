[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('doctor', 'devices', 'emulators', 'boot', 'build', 'install', 'run', 'test', 'verify', 'logcat', 'ui-dump', 'gradle')]
    [string]$Command = 'doctor',
    [ValidateSet('scrybe', 'shelf-snap', 'price-drop', 'all')]
    [string]$App = 'scrybe',
    [string]$Avd = 'scrybe-api35',
    [string]$Serial,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$script:AndroidHome = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $HOME 'scoop\apps\android-clt\current' }
$script:JavaHome = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { $env:JAVA_HOME } else { Join-Path $HOME 'scoop\apps\openjdk17\current' }
$script:Adb = Join-Path $script:AndroidHome 'platform-tools\adb.exe'
$script:Emulator = Join-Path $script:AndroidHome 'emulator\emulator.exe'
$script:AvdHome = if ($env:ANDROID_AVD_HOME) { $env:ANDROID_AVD_HOME } else { Join-Path $HOME '.android\avd' }
$script:GradleHome = Join-Path $RepoRoot '.gradle-user-home'
$script:AndroidUserHome = Join-Path $RepoRoot '.android-user-home'
$script:ProjectCacheRoot = Join-Path $RepoRoot '.gradle-project-cache'

$AppDefinitions = [ordered]@{
    'scrybe' = @{
        Root = Join-Path $RepoRoot 'apps\scrybe'
        Package = 'dev.scrybe.android'
        Activity = 'dev.scrybe.android/.MainActivity'
        Verify = @('ktlintFormat', 'assembleDebug', ':app:assembleDebugAndroidTest', 'testDebugUnitTest', 'lint', 'ktlintCheck', 'detekt')
    }
    'shelf-snap' = @{
        Root = Join-Path $RepoRoot 'apps\shelf-snap'
        Package = 'com.shelfsnap.app'
        Activity = 'com.shelfsnap.app/.MainActivity'
        Verify = @('assembleDebug', ':app:assembleDebugAndroidTest', 'testDebugUnitTest', 'lintDebug')
    }
    'price-drop' = @{
        Root = Join-Path $RepoRoot 'apps\price-drop'
        Package = 'com.twobits.pricedrop'
        Activity = 'com.twobits.pricedrop/.MainActivity'
        Verify = @('assembleDebug', ':app:assembleDebugAndroidTest', 'testDebugUnitTest', 'lintDebug')
    }
}

function Initialize-Toolchain {
    foreach ($path in @($script:AndroidHome, $script:JavaHome, $script:Adb)) {
        if (-not (Test-Path $path)) { throw "Required Android toolchain path is missing: $path" }
    }
    $env:ANDROID_HOME = $script:AndroidHome
    $env:ANDROID_SDK_ROOT = $script:AndroidHome
    $env:JAVA_HOME = $script:JavaHome
    $env:GRADLE_USER_HOME = $script:GradleHome
    $env:ANDROID_USER_HOME = $script:AndroidUserHome
    $env:ANDROID_AVD_HOME = Join-Path $HOME '.android\avd'
    $env:PATH = "$(Join-Path $script:JavaHome 'bin');$(Join-Path $script:AndroidHome 'platform-tools');$(Join-Path $script:AndroidHome 'emulator');$(Join-Path $script:AndroidHome 'cmdline-tools\latest\bin');$env:PATH"
    New-Item -ItemType Directory -Force -Path $script:GradleHome, $script:AndroidUserHome, $script:ProjectCacheRoot | Out-Null
}

function Get-RequestedApps {
    if ($App -eq 'all') { return @($AppDefinitions.Keys) }
    return @($App)
}

function Get-AdbDevices {
    Initialize-Toolchain
    $rows = & $script:Adb devices -l 2>&1
    return @($rows | Select-Object -Skip 1 | Where-Object { $_ -match '^\S+\s+\S+' } | ForEach-Object {
        $parts = ($_ -split '\s+', 3)
        [pscustomobject]@{ Serial = $parts[0]; State = $parts[1]; Details = if ($parts.Count -gt 2) { $parts[2] } else { '' } }
    })
}

function Get-AvdNames {
    if (-not (Test-Path $script:AvdHome)) { return @() }
    return @(Get-ChildItem -Path $script:AvdHome -Filter '*.ini' -File | ForEach-Object { $_.BaseName } | Sort-Object)
}

function Resolve-DeviceSerial {
    if ($Serial) {
        $match = Get-AdbDevices | Where-Object { $_.Serial -eq $Serial -and $_.State -eq 'device' }
        if (-not $match) { throw "ADB device '$Serial' is not connected and ready." }
        return $Serial
    }
    $ready = @(Get-AdbDevices | Where-Object State -eq 'device')
    $emulators = @($ready | Where-Object Serial -like 'emulator-*')
    if ($emulators.Count -eq 1) { return $emulators[0].Serial }
    if ($emulators.Count -gt 1) {
        throw "Multiple emulators are connected: $($emulators.Serial -join ', '). Pass -Serial explicitly."
    }
    if ($ready.Count -eq 1) { return $ready[0].Serial }
    $summary = if ($ready.Count) { $ready.Serial -join ', ' } else { 'none' }
    throw "No unique ready device is available (ready devices: $summary). Boot an emulator or pass -Serial."
}

function Invoke-AppGradle {
    param([string]$AppName, [string[]]$Tasks)
    Initialize-Toolchain
    $definition = $AppDefinitions[$AppName]
    $wrapper = Join-Path $definition.Root 'gradlew.bat'
    $cache = Join-Path $script:ProjectCacheRoot $AppName
    New-Item -ItemType Directory -Force -Path $cache | Out-Null
    Write-Host "[$AppName] Gradle $($Tasks -join ' ')"
    & $wrapper -p $definition.Root @Tasks --project-cache-dir $cache '-Pkotlin.compiler.execution.strategy=in-process' --no-build-cache --no-configuration-cache --no-daemon --console=plain @GradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed for $AppName with exit code $LASTEXITCODE." }
}

function Wait-ForAndroidBoot {
    param(
        [string]$DeviceSerial,
        [datetime]$Deadline
    )
    do {
        $booted = (& $script:Adb -s $DeviceSerial shell getprop sys.boot_completed 2>$null).Trim()
        if ($booted -eq '1') { return $true }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $Deadline)
    return $false
}

function Start-CanonicalEmulator {
    Initialize-Toolchain
    if (-not (Test-Path $script:Emulator)) { throw "Android Emulator is missing: $script:Emulator" }
    $existing = @(Get-AdbDevices | Where-Object { $_.State -eq 'device' -and $_.Serial -like 'emulator-*' })
    foreach ($device in $existing) {
        $name = (& $script:Adb -s $device.Serial emu avd name 2>$null | Select-Object -First 1).Trim()
        if ($name -eq $Avd) {
            if (Wait-ForAndroidBoot -DeviceSerial $device.Serial -Deadline (Get-Date).AddMinutes(4)) {
                Write-Output $device.Serial
                return
            }
            throw "AVD '$Avd' did not finish booting within four minutes."
        }
    }
    $before = @($existing | ForEach-Object { $_.Serial })
    Start-Process -FilePath $script:Emulator -ArgumentList @('-avd', $Avd, '-no-boot-anim') | Out-Null
    $deadline = (Get-Date).AddMinutes(4)
    do {
        Start-Sleep -Seconds 2
        $candidate = Get-AdbDevices | Where-Object { $_.State -eq 'device' -and $_.Serial -like 'emulator-*' -and $_.Serial -notin $before } | Select-Object -First 1
        if ($candidate) {
            if (Wait-ForAndroidBoot -DeviceSerial $candidate.Serial -Deadline $deadline) {
                Write-Output $candidate.Serial
                return
            }
            break
        }
    } while ((Get-Date) -lt $deadline)
    throw "AVD '$Avd' did not finish booting within four minutes."
}

function Invoke-Doctor {
    Initialize-Toolchain
    $checks = [ordered]@{
        'JDK 17' = (& (Join-Path $script:JavaHome 'bin\java.exe') -version 2>&1 | Select-Object -First 1)
        'Android SDK' = $script:AndroidHome
        'ADB' = (& $script:Adb version | Select-Object -First 1)
        'ImageMagick' = if (Get-Command magick -ErrorAction SilentlyContinue) { (& magick -version | Select-Object -First 1) } else { 'MISSING' }
        'Canonical AVD' = if ((Get-AvdNames) -contains $Avd) { $Avd } else { 'MISSING' }
        'Codex config' = if (Test-Path (Join-Path $RepoRoot '.codex\config.toml')) { 'present' } else { 'MISSING' }
        'Codex hooks' = if (Test-Path (Join-Path $RepoRoot '.codex\hooks.json')) { 'present' } else { 'MISSING' }
        'Claude Design key' = if ($env:ANTHROPIC_API_KEY) { 'set' } else { 'not set (design review unavailable)' }
    }
    foreach ($entry in $checks.GetEnumerator()) { '{0,-20} {1}' -f $entry.Key, $entry.Value }
    foreach ($appName in $AppDefinitions.Keys) {
        $wrapper = Join-Path $AppDefinitions[$appName].Root 'gradlew.bat'
        '{0,-20} {1}' -f "$appName wrapper", $(if (Test-Path $wrapper) { 'present' } else { 'MISSING' })
    }
    if ($checks.Values -contains 'MISSING') { throw 'One or more required tools are missing.' }
}

Initialize-Toolchain
switch ($Command) {
    'doctor' { Invoke-Doctor }
    'devices' { Get-AdbDevices | Format-Table -AutoSize }
    'emulators' { Get-AvdNames }
    'boot' { Start-CanonicalEmulator }
    'build' { foreach ($name in Get-RequestedApps) { Invoke-AppGradle $name @('assembleDebug') } }
    'test' { foreach ($name in Get-RequestedApps) { Invoke-AppGradle $name @('testDebugUnitTest') } }
    'gradle' {
        if (-not $GradleArgs -or -not $GradleArgs.Count) { throw 'Pass one or more Gradle tasks after the command.' }
        foreach ($name in Get-RequestedApps) { Invoke-AppGradle $name $GradleArgs }
    }
    'verify' { foreach ($name in Get-RequestedApps) { Invoke-AppGradle $name $AppDefinitions[$name].Verify } }
    'install' {
        $target = Resolve-DeviceSerial
        $env:ANDROID_SERIAL = $target
        foreach ($name in Get-RequestedApps) { Invoke-AppGradle $name @('installDebug') }
    }
    'run' {
        $target = Resolve-DeviceSerial
        $env:ANDROID_SERIAL = $target
        foreach ($name in Get-RequestedApps) {
            Invoke-AppGradle $name @('installDebug')
            & $script:Adb -s $target shell am start -W -n $AppDefinitions[$name].Activity
            if ($LASTEXITCODE -ne 0) { throw "Could not launch $name on $target." }
        }
    }
    'logcat' {
        $target = Resolve-DeviceSerial
        & $script:Adb -s $target logcat -d 'AndroidRuntime:E' 'ActivityManager:I' 'ActivityTaskManager:I' '*:S'
    }
    'ui-dump' {
        $target = Resolve-DeviceSerial
        & $script:Adb -s $target shell uiautomator dump /sdcard/twobits-window.xml | Out-Null
        & $script:Adb -s $target shell cat /sdcard/twobits-window.xml
    }
}
