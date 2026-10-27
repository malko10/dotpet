param([ValidateSet('Enable', 'Disable', 'Status')] [string] $Mode = 'Status')
$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$stateDirectory = Join-Path $repoRoot '.git\dotpet-sync'
New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
$enabledPath = Join-Path $stateDirectory 'enabled'
$pidPath = Join-Path $stateDirectory 'watcher.pid'
$shortcutPath = Join-Path ([Environment]::GetFolderPath('Startup')) 'DoT Pet GitHub Sync.lnk'
$powershellExe = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$syncScript = Join-Path $PSScriptRoot 'Sync-GitHub.ps1'
$arguments = '-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $syncScript + '" -RepoRoot "' + $repoRoot + '"'
if ($Mode -eq 'Enable') {
    $remote = & git -C $repoRoot config --local --get dotpet.syncRemoteUrl
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($remote)) { throw 'Configure and push the GitHub remote first.' }
    Set-Content -LiteralPath $enabledPath -Value 'enabled' -Encoding ASCII
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = $powershellExe; $shortcut.Arguments = $arguments
    $shortcut.WorkingDirectory = $repoRoot; $shortcut.WindowStyle = 7
    $shortcut.Description = 'Automatically commit and push DoT Pet source changes.'
    $shortcut.Save()
    Start-Process -FilePath $powershellExe -ArgumentList $arguments -WorkingDirectory $repoRoot -WindowStyle Hidden
    Write-Output 'GitHub source sync enabled. It will restart at Windows sign-in.'
} elseif ($Mode -eq 'Disable') {
    Remove-Item -LiteralPath $enabledPath -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $shortcutPath -ErrorAction SilentlyContinue
    Write-Output 'GitHub source sync disabled; the watcher exits after its current operation.'
} else {
    Write-Output ('Enabled: ' + (Test-Path -LiteralPath $enabledPath))
    Write-Output ('Paused: ' + (Test-Path -LiteralPath (Join-Path $stateDirectory 'paused')))
    Write-Output ('Startup shortcut: ' + (Test-Path -LiteralPath $shortcutPath))
    if (Test-Path -LiteralPath $pidPath) {
        $watcherPid = [int](Get-Content -LiteralPath $pidPath)
        $running = $null -ne (Get-Process -Id $watcherPid -ErrorAction SilentlyContinue)
        Write-Output ('Watcher running: ' + $running + '; PID: ' + $watcherPid)
    } else { Write-Output 'Watcher running: False' }
    if (Test-Path -LiteralPath (Join-Path $stateDirectory 'sync.log')) {
        Get-Content -LiteralPath (Join-Path $stateDirectory 'sync.log') -Tail 12
    }
}
