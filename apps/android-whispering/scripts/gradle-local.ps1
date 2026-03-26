param(
    [ValidateSet("verify", "build-debug", "build-release", "lint", "test", "recording-lint", "tasks")]
    [string]$Command = "verify",
    [string[]]$Task,
    [string]$GradleHomeName,
    [switch]$FreshGradleHome,
    [switch]$KeepDaemon,
    [switch]$Stacktrace
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$gradlew = Join-Path $projectRoot "gradlew.bat"

function Resolve-FirstExistingDirectory {
    param(
        [string[]]$Candidates,
        [string]$Label
    )

    foreach ($candidate in $Candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }

        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "Unable to find $Label. Checked: $($Candidates -join ', ')"
}

function Get-GradleTasks {
    param(
        [string]$SelectedCommand,
        [string[]]$CustomTasks
    )

    switch ($SelectedCommand) {
        "verify" { return @("assembleDebug", "assembleRelease", "lint", "testDebugUnitTest") }
        "build-debug" { return @("assembleDebug") }
        "build-release" { return @("assembleRelease") }
        "lint" { return @("lint") }
        "test" { return @("testDebugUnitTest") }
        "recording-lint" { return @(":service:recording:lintDebug") }
        "tasks" {
            if (-not $CustomTasks -or $CustomTasks.Count -eq 0) {
                throw "The 'tasks' command requires at least one value for -Task."
            }

            return $CustomTasks
        }
        default {
            throw "Unsupported command '$SelectedCommand'."
        }
    }
}

function Set-AndroidEnvironment {
    param(
        [string]$JavaHome,
        [string]$AndroidHome,
        [string]$GradleUserHome
    )

    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_HOME = $AndroidHome
    $env:ANDROID_SDK_ROOT = $AndroidHome
    $env:GRADLE_USER_HOME = $GradleUserHome

    $prepend = @(
        (Join-Path $JavaHome "bin"),
        (Join-Path $AndroidHome "emulator"),
        (Join-Path $AndroidHome "platform-tools"),
        (Join-Path $AndroidHome "cmdline-tools\latest\bin")
    ) | Where-Object { Test-Path $_ }

    $existingPath = @()
    if (-not [string]::IsNullOrWhiteSpace($env:PATH)) {
        $existingPath = $env:PATH -split ';'
    }

    $env:PATH = (($prepend + $existingPath) | Where-Object { $_ } | Select-Object -Unique) -join ';'
}

function Resolve-DirectGradleCommand {
    $gradleBat = Get-ChildItem -Path (Join-Path $HOME ".gradle\wrapper\dists\gradle-8.9-bin") -Recurse -Filter "gradle.bat" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName

    if ($gradleBat) {
        return $gradleBat
    }

    return $null
}

function Invoke-Gradle {
    param(
        [string]$GradleUserHome,
        [string[]]$GradleTasks,
        [switch]$DisableDaemon,
        [switch]$IncludeStacktrace
    )

    try {
        New-Item -ItemType Directory -Force $GradleUserHome | Out-Null
    } catch {
        $fallbackGradleHome = Join-Path $projectRoot ".gradle-user-home-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        Write-Warning "Unable to create GRADLE_USER_HOME '$GradleUserHome'. Falling back to '$fallbackGradleHome'."
        New-Item -ItemType Directory -Force $fallbackGradleHome | Out-Null
        $GradleUserHome = $fallbackGradleHome
    }

    $env:GRADLE_USER_HOME = $GradleUserHome

    $arguments = @()
    $arguments += $GradleTasks
    $arguments += "--console=plain"

    if ($DisableDaemon) {
        $arguments += "--no-daemon"
    }

    if ($IncludeStacktrace) {
        $arguments += "--stacktrace"
    }

    Write-Host "Project root: $projectRoot"
    Write-Host "JAVA_HOME: $env:JAVA_HOME"
    Write-Host "ANDROID_HOME: $env:ANDROID_HOME"
    Write-Host "GRADLE_USER_HOME: $GradleUserHome"
    Write-Host "Gradle tasks: $($GradleTasks -join ' ')"

    function Invoke-RunnerStreaming {
        param(
            [string]$RunnerPath,
            [string[]]$RunnerArguments
        )

        $outputLines = New-Object System.Collections.Generic.List[string]
        & $RunnerPath @RunnerArguments 2>&1 | ForEach-Object {
            $line = "$_"
            Write-Host $line
            $outputLines.Add($line)
        }

        return [pscustomobject]@{
            ExitCode = $LASTEXITCODE
            Output   = ($outputLines -join [Environment]::NewLine)
        }
    }

    $runner = $gradlew
    Push-Location $projectRoot
    try {
        $result = Invoke-RunnerStreaming -RunnerPath $runner -RunnerArguments $arguments
        $exitCode = $result.ExitCode
        $output = $result.Output

        $bootstrapErrorPattern = "Access is denied|lock file|\.zip\.lck|Could not unzip|NoSuchFileException|gradle-8\.9-bin\.zip"
        if ($exitCode -ne 0 -and $output -match $bootstrapErrorPattern) {
            $directGradle = Resolve-DirectGradleCommand
            if ($directGradle) {
                Write-Warning "Gradle wrapper bootstrap failed. Retrying with direct Gradle: $directGradle"
                $runner = $directGradle
                $result = Invoke-RunnerStreaming -RunnerPath $runner -RunnerArguments $arguments
                $exitCode = $result.ExitCode
                $output = $result.Output
            }
        }
    } finally {
        Pop-Location
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output   = ($output | Out-String)
    }
}

$javaHome = Resolve-FirstExistingDirectory -Candidates @(
    $env:JAVA_HOME,
    (Join-Path $HOME "scoop\apps\openjdk17\current"),
    "C:\Program Files\Android\Android Studio\jbr"
) -Label "JAVA_HOME"

$androidHome = Resolve-FirstExistingDirectory -Candidates @(
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    (Join-Path $HOME "scoop\apps\android-clt\current")
) -Label "ANDROID_HOME"

$gradleTasks = Get-GradleTasks -SelectedCommand $Command -CustomTasks $Task

$defaultGradleHomePath = if ($FreshGradleHome) {
    Join-Path $HOME ".gradle-user-home-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
} elseif (-not [string]::IsNullOrWhiteSpace($GradleHomeName)) {
    if ([System.IO.Path]::IsPathRooted($GradleHomeName)) {
        $GradleHomeName
    } else {
        Join-Path $HOME $GradleHomeName
    }
} else {
    Join-Path $HOME ".gradle"
}

$gradleUserHome = $defaultGradleHomePath

Set-AndroidEnvironment -JavaHome $javaHome -AndroidHome $androidHome -GradleUserHome $gradleUserHome

$useDaemon = $true
if ($env:CI -eq "true") {
    $useDaemon = $false
}
if ($PSBoundParameters.ContainsKey("KeepDaemon")) {
    $useDaemon = [bool]$KeepDaemon
}

$result = Invoke-Gradle `
    -GradleUserHome $gradleUserHome `
    -GradleTasks $gradleTasks `
    -DisableDaemon:(-not $useDaemon) `
    -IncludeStacktrace:$Stacktrace

$lockErrorPattern = "Access is denied|lock file|\.zip\.lck"

if ($result.ExitCode -ne 0 -and
    $result.Output -match $lockErrorPattern -and
    -not $FreshGradleHome -and
    [string]::IsNullOrWhiteSpace($GradleHomeName)) {
    $retryHome = Join-Path $HOME ".gradle-user-home-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    Write-Warning "Gradle hit a lock-file issue under '$gradleUserHome'. Retrying once with '$retryHome'."

    Set-AndroidEnvironment -JavaHome $javaHome -AndroidHome $androidHome -GradleUserHome $retryHome

    $result = Invoke-Gradle `
        -GradleUserHome $retryHome `
        -GradleTasks $gradleTasks `
        -DisableDaemon:(-not $useDaemon) `
        -IncludeStacktrace:$Stacktrace
}

if ($result.ExitCode -ne 0 -and $result.Output -match $lockErrorPattern) {
    throw @"
Gradle still failed while opening a lock file.

If you are running this in your own terminal, the likely causes are a stale lock, an ACL mismatch on the selected GRADLE_USER_HOME, or another Gradle process still using that directory.
If you are running this through Codex, this can also be the known sandbox file-locking limitation.

Try re-running with:
  .\scripts\gradle-local.ps1 -Command $Command -FreshGradleHome
"@
}

exit $result.ExitCode
