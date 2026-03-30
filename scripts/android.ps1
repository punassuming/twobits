param(
    [Parameter(Position = 0)]
    [ValidateSet(
        "help",
        "env",
        "gradle",
        "build",
        "release",
        "install",
        "launch",
        "run",
        "lint",
        "test",
        "detekt",
        "format",
        "check-format",
        "verify",
        "clean",
        "devices",
        "emulators",
        "emulator",
        "logcat",
        "stop-app",
        "uninstall"
    )]
    [string]$Command = "help",
    [Parameter(Position = 1, ValueFromRemainingArguments = $true)]
    [string[]]$ExtraArgs,
    [string]$Avd,
    [switch]$NoWait
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$envScript = Join-Path $repoRoot "apps\android-whispering\scripts\android-env.ps1"
$appId = "dev.scrybe.android"
$mainActivity = "$appId/.MainActivity"

. $envScript

function Invoke-ScrybeGradle {
    param(
        [string[]]$Tasks,
        [string[]]$Arguments = @()
    )

    $gradleArgs = @(
        "-p", $env:SCRYBE_ANDROID_PROJECT_ROOT,
        "--project-cache-dir", $env:SCRYBE_GRADLE_PROJECT_CACHE,
        "--no-configuration-cache",
        "--no-daemon",
        "--console=plain",
        "--info"
    ) + $Tasks + $Arguments

    & $env:SCRYBE_ANDROID_GRADLEW @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

function Invoke-Adb {
    param([string[]]$Arguments)

    & adb @Arguments
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

function Get-DefaultAvdName {
    $availableAvds = @((& emulator -list-avds) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })

    if ($Avd) {
        return $Avd
    }

    if ($availableAvds -contains "scrybe-api35") {
        return "scrybe-api35"
    }

    if ($availableAvds.Count -eq 1) {
        return $availableAvds[0]
    }

    if ($availableAvds.Count -gt 1) {
        throw "Multiple AVDs found. Pass -Avd with one of: $($availableAvds -join ', ')"
    }

    throw "No Android Virtual Devices found. Run '.\scripts\android.ps1 emulators' after creating one with avdmanager."
}

function Wait-ForAndroidBoot {
    Invoke-Adb @("wait-for-device")

    while ($true) {
        $bootCompleted = (& adb shell getprop sys.boot_completed).Trim()
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }

        if ($bootCompleted -eq "1") {
            break
        }

        Start-Sleep -Seconds 2
    }
}

function Show-Help {
    @"
Scrybe Android helper

Usage:
  .\scripts\android.ps1 <command> [options] [extra args]

Commands:
  help           Show this help text.
  env            Print the resolved Android toolchain and repo paths.
  gradle         Pass raw arguments through to gradlew.bat.
  build          Assemble the debug APK.
  release        Assemble the release APK.
  install        Install the debug APK onto the connected device or emulator.
  launch         Launch the installed app on the connected device or emulator.
  run            Install the debug APK and then launch the app.
  lint           Run Android Lint.
  test           Run JVM unit tests.
  detekt         Run Detekt static analysis.
  format         Run ktlintFormat.
  check-format   Run ktlintCheck.
  verify         Run changelog + manifest validation and the full Gradle gate.
  clean          Run Gradle clean.
  devices        List adb devices.
  emulators      List available AVDs.
  emulator       Launch an AVD and optionally wait for boot completion.
  logcat         Show filtered logcat output for app/runtime debugging.
  stop-app       Force-stop the Android app on the connected device.
  uninstall      Uninstall the Android app from the connected device.

Examples:
  .\scripts\android.ps1 build
  .\scripts\android.ps1 lint
  .\scripts\android.ps1 emulator -Avd scrybe-api35
  .\scripts\android.ps1 run
  .\scripts\android.ps1 gradle :service:recording:lintDebug --stacktrace
"@ | Write-Output
}

switch ($Command) {
    "help" {
        Show-Help
    }
    "env" {
        @(
            "SCRYBE_REPO_ROOT=$env:SCRYBE_REPO_ROOT"
            "SCRYBE_ANDROID_PROJECT_ROOT=$env:SCRYBE_ANDROID_PROJECT_ROOT"
            "SCRYBE_ANDROID_GRADLEW=$env:SCRYBE_ANDROID_GRADLEW"
            "SCRYBE_GRADLE_PROJECT_CACHE=$env:SCRYBE_GRADLE_PROJECT_CACHE"
            "JAVA_HOME=$env:JAVA_HOME"
            "ANDROID_HOME=$env:ANDROID_HOME"
            "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
        ) | Write-Output
    }
    "gradle" {
        if (-not $ExtraArgs -or $ExtraArgs.Count -eq 0) {
            throw "Pass at least one Gradle task or argument, for example '.\scripts\android.ps1 gradle assembleDebug'."
        }

        Invoke-ScrybeGradle -Tasks $ExtraArgs
    }
    "build" {
        Invoke-ScrybeGradle -Tasks @("assembleDebug") -Arguments $ExtraArgs
    }
    "release" {
        Invoke-ScrybeGradle -Tasks @("assembleRelease") -Arguments $ExtraArgs
    }
    "install" {
        Invoke-ScrybeGradle -Tasks @("installDebug") -Arguments $ExtraArgs
    }
    "launch" {
        Invoke-Adb @("shell", "am", "start", "-n", $mainActivity)
    }
    "run" {
        Invoke-ScrybeGradle -Tasks @("installDebug") -Arguments $ExtraArgs
        Invoke-Adb @("shell", "am", "start", "-n", $mainActivity)
    }
    "lint" {
        Invoke-ScrybeGradle -Tasks @("lint") -Arguments $ExtraArgs
    }
    "test" {
        Invoke-ScrybeGradle -Tasks @("testDebugUnitTest") -Arguments $ExtraArgs
    }
    "detekt" {
        Invoke-ScrybeGradle -Tasks @("detekt") -Arguments $ExtraArgs
    }
    "format" {
        Invoke-ScrybeGradle -Tasks @("ktlintFormat") -Arguments $ExtraArgs
    }
    "check-format" {
        Invoke-ScrybeGradle -Tasks @("ktlintCheck") -Arguments $ExtraArgs
    }
    "verify" {
        Set-Location $env:SCRYBE_ANDROID_PROJECT_ROOT
        python scripts/manage-changelog.py validate --changelog ../../CHANGELOG.md
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }

        python scripts/validate-manifests.py
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }

        Set-Location $repoRoot
        Invoke-ScrybeGradle -Tasks @(
            "assembleDebug",
            "testDebugUnitTest",
            "lint",
            "ktlintFormat",
            "ktlintCheck",
            "detekt"
        ) -Arguments $ExtraArgs
    }
    "clean" {
        Invoke-ScrybeGradle -Tasks @("clean") -Arguments $ExtraArgs
    }
    "devices" {
        Invoke-Adb @("devices")
    }
    "emulators" {
        & emulator -list-avds
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
    "emulator" {
        $selectedAvd = Get-DefaultAvdName
        $emulatorArgs = @("-avd", $selectedAvd, "-no-snapshot", "-no-boot-anim") + $ExtraArgs

        Write-Output "Launching emulator '$selectedAvd'..."
        Start-Process -FilePath "emulator" -ArgumentList $emulatorArgs | Out-Null

        if (-not $NoWait) {
            Wait-ForAndroidBoot
            Write-Output "Android emulator '$selectedAvd' finished booting."
        }
    }
    "logcat" {
        $logcatArgs = if ($ExtraArgs -and $ExtraArgs.Count -gt 0) {
            $ExtraArgs
        } else {
            @("logcat", "-d", "AndroidRuntime:E", "ActivityManager:I", "ActivityTaskManager:I", "*:S")
        }

        Invoke-Adb $logcatArgs
    }
    "stop-app" {
        Invoke-Adb @("shell", "am", "force-stop", $appId)
    }
    "uninstall" {
        Invoke-Adb @("uninstall", $appId)
    }
}
