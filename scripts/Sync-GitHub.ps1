param(
    [string] $RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [switch] $Once,
    [string] $Message = 'Update DoT Pet source',
    [int] $QuietSeconds = 25,
    [int] $PollSeconds = 5
)

$ErrorActionPreference = 'Stop'
$env:GIT_TERMINAL_PROMPT = '0'
$env:GCM_INTERACTIVE = 'Never'
$env:GIT_OPTIONAL_LOCKS = '0'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
$RepoRoot = [System.IO.Path]::GetFullPath($RepoRoot)
$gitExe = (Get-Command git.exe -ErrorAction Stop).Source
$stateDirectory = Join-Path $RepoRoot '.git\dotpet-sync'
New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
$logPath = Join-Path $stateDirectory 'sync.log'
$sourcePaths = @('.gitignore', '.gitattributes', 'AGENTS.md', 'README.md', '.github', 'docs', 'scripts',
    'Start-GitHub-Sync.bat', 'Stop-GitHub-Sync.bat', 'GitHub-Sync-Status.bat', 'outputs/dotpet')

function Write-SyncLog([string] $text) {
    $line = [DateTime]::Now.ToString('yyyy-MM-dd HH:mm:ss') + ' ' + $text
    Add-Content -LiteralPath $logPath -Value $line -Encoding UTF8
    if ($Once) { Write-Host $line }
}

function Invoke-RepoGit([string[]] $GitArguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = @(& $gitExe -C $RepoRoot -c core.quotepath=false @GitArguments 2>&1 | ForEach-Object { $_.ToString() })
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    return [pscustomobject]@{ Code = $code; Lines = $lines; Text = ($lines -join "`n") }
}

function Assert-GitSuccess($result, [string] $operation) {
    if ($result.Code -ne 0) { throw ($operation + ': ' + $result.Text) }
}

function Get-SourceSnapshot {
    $status = Invoke-RepoGit (@('status', '--porcelain=v1', '--untracked-files=all', '--') + $sourcePaths)
    Assert-GitSuccess $status 'Read source changes'
    if ($status.Lines.Count -eq 0) { return '' }
    $files = Invoke-RepoGit (@('ls-files', '--cached', '--others', '--exclude-standard', '--') + $sourcePaths)
    Assert-GitSuccess $files 'List source files'
    $parts = foreach ($relative in ($files.Lines | Sort-Object -Unique)) {
        $path = Join-Path $RepoRoot $relative
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            $file = Get-Item -LiteralPath $path
            if ($file.Length -gt 10MB) { throw ('Source file exceeds 10 MB: ' + $relative) }
            $stream = [System.IO.File]::OpenRead($path)
            $algorithm = [System.Security.Cryptography.SHA256]::Create()
            try { $hash = [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-', '') }
            finally { $stream.Dispose(); $algorithm.Dispose() }
            $relative + '|' + $hash
        } else { $relative + '|deleted' }
    }
    return $status.Text + "`n" + ($parts -join "`n")
}

function Invoke-SyncCycle([string] $ExpectedSnapshot) {
    if (Test-Path -LiteralPath (Join-Path $stateDirectory 'paused')) { return $false }
    $branch = Invoke-RepoGit @('branch', '--show-current')
    if ($branch.Text.Trim() -ne 'main') { throw 'Auto sync only runs on main. No branch was changed.' }
    foreach ($marker in @('MERGE_HEAD', 'CHERRY_PICK_HEAD', 'rebase-merge', 'rebase-apply')) {
        if (Test-Path -LiteralPath (Join-Path $RepoRoot ('.git\' + $marker))) { throw 'Another Git operation is in progress.' }
    }
    $expectedRemote = Invoke-RepoGit @('config', '--local', '--get', 'dotpet.syncRemoteUrl')
    $remote = Invoke-RepoGit @('remote', 'get-url', 'origin')
    if ($expectedRemote.Code -ne 0 -or $remote.Code -ne 0 -or $remote.Text.Trim() -ne $expectedRemote.Text.Trim()) {
        throw 'GitHub remote is not configured for this workspace.'
    }
    $staged = Invoke-RepoGit @('diff', '--cached', '--quiet')
    if ($staged.Code -eq 1) { Write-SyncLog 'Waiting: manually staged changes are present.'; return $false }
    Assert-GitSuccess $staged 'Check staging area'
    $upstream = Invoke-RepoGit @('rev-parse', '--verify', 'refs/remotes/origin/main')
    if ($upstream.Code -ne 0) { throw 'Push the initial main branch before enabling auto sync.' }
    $pending = Invoke-RepoGit @('rev-list', '--count', 'refs/remotes/origin/main..HEAD')
    Assert-GitSuccess $pending 'Check pending commits'
    if ([int]$pending.Text.Trim() -gt 0) {
        $push = Invoke-RepoGit @('push', 'origin', 'HEAD:refs/heads/main')
        Assert-GitSuccess $push 'Push pending commits (no force or merge)'
        Write-SyncLog 'Pushed pending local commits.'
    }
    $currentSnapshot = Get-SourceSnapshot
    if ([string]::IsNullOrEmpty($currentSnapshot)) { return $true }
    if ($ExpectedSnapshot -and $currentSnapshot -ne $ExpectedSnapshot) { return $false }
    $add = Invoke-RepoGit (@('add', '-A', '--') + $sourcePaths)
    Assert-GitSuccess $add 'Stage source changes'
    $difference = Invoke-RepoGit @('diff', '--cached', '--quiet')
    if ($difference.Code -eq 0) { return $true }
    if ($difference.Code -ne 1) { throw 'Unable to inspect staged source changes.' }
    $commit = Invoke-RepoGit @('commit', '-m', $Message)
    Assert-GitSuccess $commit 'Commit source changes'
    $revision = Invoke-RepoGit @('rev-parse', '--short', 'HEAD')
    Write-SyncLog ('Committed ' + $revision.Text.Trim() + ': ' + $Message)
    $push = Invoke-RepoGit @('push', 'origin', 'HEAD:refs/heads/main')
    Assert-GitSuccess $push 'Push source changes (local commit retained on failure)'
    Write-SyncLog ('GitHub updated to ' + $revision.Text.Trim())
    return $true
}

$top = Invoke-RepoGit @('rev-parse', '--show-toplevel')
Assert-GitSuccess $top 'Locate repository'
if ([System.IO.Path]::GetFullPath($top.Text.Trim()) -ne $RepoRoot) { throw 'RepoRoot must be the repository root.' }

if ($Once) {
    $pausePath = Join-Path $stateDirectory 'paused'
    if (Test-Path -LiteralPath $pausePath) { throw 'Auto sync is paused. Remove .git/dotpet-sync/paused when ready.' }
    $cycleLock = [System.IO.File]::Open((Join-Path $stateDirectory 'cycle.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    try {
        if (!(Invoke-SyncCycle '')) { throw 'Sync was skipped because another operation needs attention.' }
    } finally { $cycleLock.Dispose() }
    exit 0
}

$processLock = $null
try { $processLock = [System.IO.File]::Open((Join-Path $stateDirectory 'process.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
catch [System.IO.IOException] { exit 0 }
try {
    Set-Content -LiteralPath (Join-Path $stateDirectory 'watcher.pid') -Value $PID -Encoding ASCII
    Write-SyncLog ('Watcher started; source quiet period: ' + $QuietSeconds + ' seconds.')
    $lastSnapshot = ''; $lastChange = [DateTime]::UtcNow; $retryAfter = [DateTime]::MinValue
    while (Test-Path -LiteralPath (Join-Path $stateDirectory 'enabled')) {
        try {
            if ((Test-Path -LiteralPath (Join-Path $stateDirectory 'paused')) -or [DateTime]::UtcNow -lt $retryAfter) {
                Start-Sleep -Seconds $PollSeconds; continue
            }
            $snapshot = Get-SourceSnapshot
            if ($snapshot -ne $lastSnapshot) { $lastSnapshot = $snapshot; $lastChange = [DateTime]::UtcNow }
            if (($snapshot -eq '') -or ([DateTime]::UtcNow - $lastChange).TotalSeconds -ge $QuietSeconds) {
                $cycleLock = $null
                try {
                    $cycleLock = [System.IO.File]::Open((Join-Path $stateDirectory 'cycle.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
                    $completed = Invoke-SyncCycle $snapshot
                    if ($completed) { $lastSnapshot = ''; $retryAfter = [DateTime]::MinValue }
                } catch [System.IO.IOException] { }
                finally { if ($null -ne $cycleLock) { $cycleLock.Dispose() } }
            }
        } catch {
            Write-SyncLog ('Waiting; local files preserved: ' + $_.Exception.Message)
            $retryAfter = [DateTime]::UtcNow.AddSeconds(60)
        }
        Start-Sleep -Seconds $PollSeconds
    }
    Write-SyncLog 'Watcher stopped.'
} finally {
    $processLock.Dispose()
    Remove-Item -LiteralPath (Join-Path $stateDirectory 'watcher.pid') -ErrorAction SilentlyContinue
}
