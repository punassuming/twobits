param(
    [string]$JavaHome,
    [string]$AndroidHome,
    [string]$GradleUserHome
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$repoRoot = (Resolve-Path (Join-Path $projectRoot "..\..")).Path
$gradleWrapper = Join-Path $projectRoot "gradlew.bat"

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

$resolvedJavaHome = Resolve-FirstExistingDirectory -Candidates @(
    $JavaHome,
    $env:JAVA_HOME,
    (Join-Path $HOME "scoop\apps\openjdk17\current"),
    "C:\Program Files\Android\Android Studio\jbr"
) -Label "JAVA_HOME"

$resolvedAndroidHome = Resolve-FirstExistingDirectory -Candidates @(
    $AndroidHome,
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    (Join-Path $HOME "scoop\apps\android-clt\current")
) -Label "ANDROID_HOME"

$resolvedGradleUserHome = if ([string]::IsNullOrWhiteSpace($GradleUserHome)) {
    Join-Path $repoRoot ".guh"
} elseif ([System.IO.Path]::IsPathRooted($GradleUserHome)) {
    $GradleUserHome
} else {
    Join-Path $repoRoot $GradleUserHome
}

$projectCacheDir = Join-Path $repoRoot ".gradle-project-cache"

New-Item -ItemType Directory -Force $resolvedGradleUserHome | Out-Null
New-Item -ItemType Directory -Force $projectCacheDir | Out-Null

$env:JAVA_HOME = $resolvedJavaHome
$env:ANDROID_HOME = $resolvedAndroidHome
$env:ANDROID_SDK_ROOT = $resolvedAndroidHome
$env:GRADLE_USER_HOME = $resolvedGradleUserHome
$env:SCRYBE_GRADLE_PROJECT_CACHE = $projectCacheDir
$env:SCRYBE_REPO_ROOT = $repoRoot
$env:SCRYBE_ANDROID_PROJECT_ROOT = $projectRoot
$env:SCRYBE_ANDROID_GRADLEW = $gradleWrapper

$prepend = @(
    (Join-Path $resolvedJavaHome "bin"),
    (Join-Path $resolvedAndroidHome "emulator"),
    (Join-Path $resolvedAndroidHome "platform-tools"),
    (Join-Path $resolvedAndroidHome "cmdline-tools\latest\bin")
) | Where-Object { Test-Path $_ }

$existingPath = @()
if (-not [string]::IsNullOrWhiteSpace($env:PATH)) {
    $existingPath = $env:PATH -split ';'
}
$env:PATH = (($prepend + $existingPath) | Where-Object { $_ } | Select-Object -Unique) -join ';'

# Write-Output "Tip: dot-source this script so the environment changes stay in your current PowerShell session."
# Write-Output "Example: . .\scripts\android-env.ps1"
# Write-Output ""
# Write-Output "JAVA_HOME=$env:JAVA_HOME"
# Write-Output "ANDROID_HOME=$env:ANDROID_HOME"
# Write-Output "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
# Write-Output "PROJECT_CACHE_DIR=$projectCacheDir"
# Write-Output "SCRYBE_GRADLE_PROJECT_CACHE=$env:SCRYBE_GRADLE_PROJECT_CACHE"
# Write-Output "SCRYBE_REPO_ROOT=$env:SCRYBE_REPO_ROOT"
# Write-Output "SCRYBE_ANDROID_PROJECT_ROOT=$env:SCRYBE_ANDROID_PROJECT_ROOT"
# Write-Output "SCRYBE_ANDROID_GRADLEW=$env:SCRYBE_ANDROID_GRADLEW"
# Write-Output ""
# Write-Output "Run Gradle directly from the repo root, for example:"
# Write-Output "Set-Location `"$env:SCRYBE_REPO_ROOT`""
# Write-Output "& `"$env:SCRYBE_ANDROID_GRADLEW`" -p `"$env:SCRYBE_ANDROID_PROJECT_ROOT`" help --project-cache-dir `"$env:SCRYBE_GRADLE_PROJECT_CACHE`" --no-configuration-cache --console=plain --info"
# Write-Output "& `"$env:SCRYBE_ANDROID_GRADLEW`" -p `"$env:SCRYBE_ANDROID_PROJECT_ROOT`" assembleDebug --project-cache-dir `"$env:SCRYBE_GRADLE_PROJECT_CACHE`" --no-configuration-cache --no-daemon --console=plain --info"
