param([string] $TestDirectory)
$ErrorActionPreference = 'Stop'
if (!$TestDirectory) { $TestDirectory = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) ('work\update-check-' + [guid]::NewGuid().ToString('N')) }
$root = [IO.Path]::GetFullPath($TestDirectory)
New-Item -ItemType Directory -Path $root -Force | Out-Null
$module = Import-Module (Join-Path (Split-Path -Parent $PSScriptRoot) 'DoTPet-Package.psm1') -Force -PassThru
$script:checks = 0
function Check([bool] $Condition, [string] $Message) { $script:checks++; if (!$Condition) { throw $Message } }
function Rejects([scriptblock] $Action) { $script:checks++; try { & $Action | Out-Null } catch { return }; throw 'Invalid update was accepted.' }
function Fixture([string] $Name, [string] $Content, [bool] $OldLibrary = $false) {
    $path = Join-Path $root $Name
    New-Item -ItemType Directory -Path (Join-Path $path 'app'),(Join-Path $path 'runtime\bin') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $path 'DoTPet.exe') -Value $Content
    Set-Content -LiteralPath (Join-Path $path 'runtime\bin\java.exe') -Value 'Never executed: fixture only'
    Set-Content -LiteralPath (Join-Path $path 'app\DoTPet.cfg') -Value 'app.mainclass=com.dotpet.Main'
    $jar = [IO.Compression.ZipFile]::Open((Join-Path $path 'app\dotpet.jar'),[IO.Compression.ZipArchiveMode]::Create); $jar.Dispose()
    if ($OldLibrary) { Set-Content -LiteralPath (Join-Path $path 'app\old.dll') -Value 'old library fixture' }
    return $path
}

$first = Fixture 'first' 'v1' $true
$zip = Join-Path $root 'valid.zip'
$archive = [IO.Compression.ZipFile]::Open($zip,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem -LiteralPath $first -Recurse -File) {
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,('DoTPet-Windows/' + $file.FullName.Substring($first.Length+1).Replace('\','/')))
    }
} finally { $archive.Dispose() }
$hash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash
$expanded = Expand-DoTPetArchive -ZipPath $zip -Destination (Join-Path $root 'expanded') -ExpectedSha256 $hash
Check ((Get-Content -LiteralPath (Join-Path $expanded 'DoTPet.exe') -Raw).Trim() -eq 'v1') 'Archive extraction changed data.'
Rejects { Expand-DoTPetArchive -ZipPath $zip -Destination (Join-Path $root 'wrong-hash') -ExpectedSha256 ('0'*64) }
Check (!(Test-Path -LiteralPath (Join-Path $root 'wrong-hash'))) 'Bad hash created a destination.'
foreach ($badName in @('DoTPet-Windows/../outside.txt','/absolute.txt','DoTPet-Windows/C:/outside.txt','DoTPet-Windows/.. /outside.txt','DoTPet-Windows/CON/file.txt')) {
    $badZip = Join-Path $root ([guid]::NewGuid().ToString('N')+'.zip')
    $archive=[IO.Compression.ZipFile]::Open($badZip,[IO.Compression.ZipArchiveMode]::Create)
    try { [void]$archive.CreateEntry($badName) } finally {$archive.Dispose()}
    Rejects { Expand-DoTPetArchive -ZipPath $badZip -Destination (Join-Path $root 'unsafe') -ExpectedSha256 (Get-FileHash -LiteralPath $badZip -Algorithm SHA256).Hash }
}
Check (!(Test-Path -LiteralPath (Join-Path $root 'outside.txt'))) 'Archive traversal wrote an outside file.'
$target = Join-Path $root 'installed'
Rejects { Install-DoTPetPackage -PackageDirectory $first -InstallDirectory $target }
Check (!(Test-Path -LiteralPath $target)) 'Unsigned package changed the installed directory.'

# Test-only boundary: pretend generated fixture bytes have trusted signatures.
# Production exposes no skip-verification flag. No fixture EXE is ever executed.
& $module { function script:Get-AuthenticodeSignature { param([string] $LiteralPath) [pscustomobject]@{Status='Valid'} } }
try {
    $data = Join-Path $root 'user-data'; New-Item -ItemType Directory -Path $data | Out-Null
    Set-Content -LiteralPath (Join-Path $data 'my-pet.dotpet') -Value 'preserve'
    $exe = Install-DoTPetPackage -PackageDirectory $first -InstallDirectory $target
    Check ($exe -eq (Join-Path $target 'DoTPet.exe')) 'Installation did not use the fixed target.'
    Check (Test-Path -LiteralPath (Join-Path $target '.dotpet-install.json')) 'Ownership manifest missing.'
    $second = Fixture 'second' 'v2'
    Install-DoTPetPackage -PackageDirectory $second -InstallDirectory $target | Out-Null
    Check ((Get-Content -LiteralPath $exe -Raw).Trim() -eq 'v2') 'Second installation did not replace the executable.'
    Check (!(Test-Path -LiteralPath (Join-Path $target 'app\old.dll'))) 'Old runtime/library was left behind.'
    Check (@(Get-ChildItem -LiteralPath $root -Directory -Filter '.dotpet-backup-*').Count -eq 0) 'Successful update left an executable backup.'
    Check ((Get-Content -LiteralPath (Join-Path $data 'my-pet.dotpet') -Raw).Trim() -eq 'preserve') 'User data changed.'
    $third = Fixture 'third' 'v3'
    $unmanaged = Join-Path $root 'unmanaged'; New-Item -ItemType Directory -Path $unmanaged | Out-Null
    Set-Content -LiteralPath (Join-Path $unmanaged 'notes.txt') -Value 'keep'
    Rejects { Install-DoTPetPackage -PackageDirectory $third -InstallDirectory $unmanaged }
    Check ((Get-Content -LiteralPath (Join-Path $unmanaged 'notes.txt') -Raw).Trim() -eq 'keep') 'Unmanaged folder was modified.'
    $note = Join-Path $target 'app\notes.txt'; Set-Content -LiteralPath $note -Value 'extra user file'
    Rejects { Install-DoTPetPackage -PackageDirectory $third -InstallDirectory $target }
    Check (Test-Path -LiteralPath $note) 'Extra user file was deleted.'
    Remove-Item -LiteralPath $note
    Set-Content -LiteralPath (Join-Path $third 'custom.dotpet') -Value 'user profile'
    Rejects { Install-DoTPetPackage -PackageDirectory $third -InstallDirectory $target }
    Remove-Item -LiteralPath (Join-Path $third 'custom.dotpet')
    & $module {
        param($FailureSource)
        $script:FailureSource=$FailureSource
        function script:Move-Item {
            param([string] $LiteralPath,[string] $Destination)
            if ($LiteralPath -eq $script:FailureSource) { throw 'Simulated disk/rename error' }
            Microsoft.PowerShell.Management\Move-Item -LiteralPath $LiteralPath -Destination $Destination
        }
    } $third
    Rejects { Install-DoTPetPackage -PackageDirectory $third -InstallDirectory $target }
    Check ((Get-Content -LiteralPath $exe -Raw).Trim() -eq 'v2') 'Failed update did not restore the previous install.'
    Check (Test-Path -LiteralPath (Join-Path $third 'DoTPet.exe')) 'Failed update lost its new package.'
    & $module { Remove-Item -LiteralPath Function:script:Move-Item }
    & $module {
        param($ExePath)
        $script:RunningExe=$ExePath
        function script:Get-CimInstance { param($ClassName,$Filter) [pscustomobject]@{ExecutablePath=$script:RunningExe} }
    } $exe
    Rejects { Install-DoTPetPackage -PackageDirectory $third -InstallDirectory $target }
    Check ((Get-Content -LiteralPath $exe -Raw).Trim() -eq 'v2') 'Running installation was overwritten.'
} finally { Remove-Module $module }
Write-Output "Passed $script:checks updater checks: archive/hash, unsigned refusal, fixed path, old-file replacement, user-data preservation, rollback, and running-process protection. Generated fixture files only; no executable launched."
