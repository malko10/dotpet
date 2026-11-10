param(
    [string] $InstallDirectory = (Join-Path $env:LOCALAPPDATA 'Programs\DoTPet'),
    [string] $PackageZip,
    [string] $ExpectedSha256
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'DoTPet-Package.psm1') -Force
$taskRoot = Split-Path -Parent $PSScriptRoot
$scratch = Join-Path $taskRoot ('work\dotpet-update-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch -Force | Out-Null
if (!$PackageZip) {
    $release = Invoke-RestMethod -Uri 'https://api.github.com/repos/malko10/dotpet/releases/latest' -Headers @{'User-Agent'='DoTPet-Updater';Accept='application/vnd.github+json'}
    $asset = @($release.assets | Where-Object name -eq 'DoTPet-Windows.zip')
    if ($asset.Count -ne 1 -or $asset[0].digest -notmatch '^sha256:[a-fA-F0-9]{64}$' -or
        !$asset[0].browser_download_url.StartsWith('https://github.com/malko10/dotpet/releases/download/', [StringComparison]::Ordinal)) {
        throw 'The official release does not have a verifiable Windows ZIP.'
    }
    $ExpectedSha256 = $asset[0].digest.Substring(7)
    $PackageZip = Join-Path $scratch 'DoTPet-Windows.zip'
    Invoke-WebRequest -Uri $asset[0].browser_download_url -OutFile $PackageZip
}
$app = Expand-DoTPetArchive -ZipPath $PackageZip -Destination (Join-Path $scratch 'extracted') -ExpectedSha256 $ExpectedSha256
$exe = Install-DoTPetPackage -PackageDirectory $app -InstallDirectory $InstallDirectory
$shell = New-Object -ComObject WScript.Shell
$linkPath = Join-Path ([Environment]::GetFolderPath('Desktop')) 'DoT Pet.lnk'
$link = $shell.CreateShortcut($linkPath)
if ((Test-Path -LiteralPath $linkPath) -and $link.TargetPath -ne $exe) { throw 'The existing desktop shortcut points elsewhere; it was preserved.' }
$link.TargetPath = $exe
$link.WorkingDirectory = Split-Path -Parent $exe
$link.Description = 'DoT Pet'
$link.Save()
Write-Output "Updated DoT Pet at $exe. The desktop shortcut keeps the same target. No application was launched."
