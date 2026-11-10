param(
    [string] $InstallDirectory = (Join-Path $env:LOCALAPPDATA 'Programs\DoTPet'),
    [switch] $Apply
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'DoTPet-Package.psm1') -Force
$workspace = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$outputs = Join-Path $workspace 'outputs'
$downloads = Join-Path $env:USERPROFILE 'Downloads'
foreach ($boundary in @($outputs,$downloads)) {
    for ($current=$boundary; $current; $current=[IO.Path]::GetDirectoryName($current)) {
        if ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Cleanup refuses redirected/junction directories.' }
        if ([IO.Path]::GetPathRoot($current) -eq $current) { break }
    }
}
$candidates = @()
foreach ($folder in Get-ChildItem -LiteralPath $outputs -Directory | Where-Object Name -match '^DoTPet-Windows-v[0-9.]+$') {
    $candidates += [pscustomobject]@{Path=$folder.FullName;Parent=$outputs;Kind='Directory'}
}
$downloadApp = Join-Path $downloads 'DoTPet-Windows'
if (Test-Path -LiteralPath $downloadApp -PathType Container) {
    $candidates += [pscustomobject]@{Path=$downloadApp;Parent=$downloads;Kind='Directory'}
}
foreach ($file in Get-ChildItem -LiteralPath $downloads -File | Where-Object Name -match '^DoTPet-Windows( \([0-9]+\))?\.zip$') {
    $candidates += [pscustomobject]@{Path=$file.FullName;Parent=$downloads;Kind='Archive'}
}
$safe = foreach ($item in $candidates) {
    if ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($item.Path)) -ne $item.Parent -or
        $item.Path -eq [IO.Path]::GetFullPath($InstallDirectory) -or
        ((Get-Item -LiteralPath $item.Path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Invalid cleanup boundary.' }
    if ($item.Kind -eq 'Directory') {
        if (!(Test-Path -LiteralPath (Join-Path $item.Path 'DoTPet.exe') -PathType Leaf) -or
            !(Test-Path -LiteralPath (Join-Path $item.Path 'app\DoTPet.cfg') -PathType Leaf) -or
            !(Test-Path -LiteralPath (Join-Path $item.Path 'runtime') -PathType Container)) { continue }
        if (!(Get-Content -LiteralPath (Join-Path $item.Path 'app\DoTPet.cfg') -Raw).Contains('app.mainclass=com.dotpet.Main')) { continue }
        $unknown = @(Get-ChildItem -LiteralPath $item.Path -Recurse -Force | Where-Object {
            ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $_.Extension -in '.dotpet','.png' -or
            $_.Name -in 'active-pet.zip','input.properties','program.properties','pets','profiles'
        })
        if ($unknown.Count) { continue }
    } else {
        $zip=[IO.Compression.ZipFile]::OpenRead($item.Path)
        try {
            $entries=@($zip.Entries.FullName | ForEach-Object {$_.Replace('\','/')})
            if ('DoTPet-Windows/DoTPet.exe' -notin $entries -or
                @($entries | Where-Object {$_ -match '(?i)\.dotpet$|/active-pet\.zip$|/(pets|profiles)/'}).Count) { continue }
        } finally {$zip.Dispose()}
    }
    $item
}
if (!$Apply) { $safe | Select-Object Kind,Path; return }

# The user's condition is a verified usable signed latest version. A successful
# launch still has to be checked separately before this maintenance command runs.
$marker=Join-Path $InstallDirectory '.dotpet-install.json'
if (!(Test-Path -LiteralPath $marker -PathType Leaf) -or
    (Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json).application -ne 'malko10/dotpet') { throw 'The fixed latest installation is not ready. Nothing was deleted.' }
$blocked=@(Get-DoTPetSignatureReport -Directory $InstallDirectory | Where-Object Status -ne 'Valid')
if ($blocked.Count) { throw 'The latest installation does not pass signature checks. Nothing was deleted.' }
$running=@(Get-CimInstance Win32_Process -Filter "Name='DoTPet.exe'")
foreach ($item in $safe) {
    if (@($running | Where-Object {$_.ExecutablePath -and $_.ExecutablePath.StartsWith($item.Path+'\',[StringComparison]::OrdinalIgnoreCase)}).Count) {
        throw 'An old DoT Pet is still running. Nothing was deleted.'
    }
}
foreach ($item in $safe) {
    # Re-check the absolute boundary directly before each recursive removal.
    if ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($item.Path)) -ne $item.Parent) { throw 'Cleanup target escaped its parent.' }
    if ($item.Kind -eq 'Directory') { Remove-Item -LiteralPath $item.Path -Recurse -Force }
    else { Remove-Item -LiteralPath $item.Path -Force }
    Write-Output "Removed old package: $($item.Path)"
}
