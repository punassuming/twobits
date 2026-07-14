[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('doctor', 'setup', 'path', 'status', 'sync', 'test', 'contract', 'dev')]
    [string]$Command = 'doctor',
    [string]$WorkerRoot = (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'twobits-worker'),
    [string]$Remote = 'https://github.com/punassuming/twobits-worker.git',
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$WorkerArgs
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:WorkerRoot = [System.IO.Path]::GetFullPath($WorkerRoot)

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Executable,
        [string[]]$Arguments = @()
    )
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Executable exited with code $LASTEXITCODE"
    }
}

function Assert-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' is not available."
    }
}

function Assert-WorkerCheckout {
    if (-not (Test-Path (Join-Path $script:WorkerRoot '.git'))) {
        throw "Worker checkout not found at '$script:WorkerRoot'. Run: .\scripts\worker.ps1 setup"
    }
    $actualRemote = (& git -C $script:WorkerRoot remote get-url origin).Trim()
    if ($LASTEXITCODE -ne 0 -or $actualRemote -notmatch 'punassuming[\\/]twobits-worker(?:\.git)?$') {
        throw "'$script:WorkerRoot' is not the expected punassuming/twobits-worker checkout (origin: '$actualRemote')."
    }
}

function Show-Doctor {
    foreach ($name in @('git', 'gh', 'node', 'npm')) {
        $commandInfo = Get-Command $name -ErrorAction SilentlyContinue
        $state = if ($commandInfo) { $commandInfo.Source } else { 'MISSING' }
        Write-Output ("{0,-12} {1}" -f $name, $state)
    }
    Write-Output ("{0,-12} {1}" -f 'worker', $script:WorkerRoot)
    if (Test-Path (Join-Path $script:WorkerRoot '.git')) {
        Assert-WorkerCheckout
        Write-Output ("{0,-12} {1}" -f 'branch', (& git -C $script:WorkerRoot branch --show-current).Trim())
        Write-Output ("{0,-12} {1}" -f 'origin', (& git -C $script:WorkerRoot remote get-url origin).Trim())
    } else {
        Write-Output ("{0,-12} {1}" -f 'checkout', 'MISSING (run setup)')
    }
    $wrangler = Get-Command wrangler -ErrorAction SilentlyContinue
    Write-Output ("{0,-12} {1}" -f 'wrangler', $(if ($wrangler) { $wrangler.Source } else { 'optional; npx is used by dev' }))
}

switch ($Command) {
    'doctor' {
        Show-Doctor
    }
    'setup' {
        Assert-Command 'gh'
        if (Test-Path $script:WorkerRoot) {
            Assert-WorkerCheckout
            Write-Output "Worker checkout already exists: $script:WorkerRoot"
        } else {
            $parent = Split-Path -Parent $script:WorkerRoot
            New-Item -ItemType Directory -Force -Path $parent | Out-Null
            Invoke-Native 'gh' @('repo', 'clone', $Remote, $script:WorkerRoot)
            Assert-WorkerCheckout
        }
    }
    'path' {
        Write-Output $script:WorkerRoot
    }
    'status' {
        Assert-WorkerCheckout
        Invoke-Native 'git' @('-C', $script:WorkerRoot, 'status', '-sb')
    }
    'sync' {
        Assert-WorkerCheckout
        Invoke-Native 'git' @('-C', $script:WorkerRoot, 'fetch', 'origin', 'main')
        Invoke-Native 'git' @('-C', $script:WorkerRoot, 'rebase', 'origin/main')
    }
    'test' {
        Assert-WorkerCheckout
        Assert-Command 'npm'
        Invoke-Native 'npm' (@('--prefix', $script:WorkerRoot, 'test') + $WorkerArgs)
    }
    'contract' {
        Assert-WorkerCheckout
        $androidFixture = Join-Path (Split-Path -Parent $PSScriptRoot) 'shared\contracts\price-drop\v2\fixtures\discover-response.json'
        $workerFixture = Join-Path $script:WorkerRoot 'test\fixtures\discover-response.json'
        foreach ($fixture in @($androidFixture, $workerFixture)) {
            if (-not (Test-Path $fixture)) { throw "Product discovery fixture is missing: $fixture" }
        }
        $androidHash = (Get-FileHash -Algorithm SHA256 -Path $androidFixture).Hash
        $workerHash = (Get-FileHash -Algorithm SHA256 -Path $workerFixture).Hash
        if ($androidHash -ne $workerHash) {
            throw "Product discovery fixtures differ. Update both repositories in the same coordinated change."
        }
        Write-Output "PriceDrop v2 fixtures match ($androidHash)."
    }
    'dev' {
        Assert-WorkerCheckout
        Assert-Command 'npx'
        Push-Location $script:WorkerRoot
        try {
            Invoke-Native 'npx' (@('wrangler', 'dev') + $WorkerArgs)
        } finally {
            Pop-Location
        }
    }
}
