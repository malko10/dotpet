Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Assert-PlainDirectory([string] $Path) {
    $full = [IO.Path]::GetFullPath($Path)
    for ($current = $full; $current; $current = [IO.Path]::GetDirectoryName($current)) {
        if (Test-Path -LiteralPath $current) {
            $item = Get-Item -LiteralPath $current -Force
            if (!$item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
                throw "Not a plain directory: $current"
            }
        }
        if ([IO.Path]::GetPathRoot($current) -eq $current) { break }
    }
    return $full.TrimEnd('\', '/')
}

function Assert-DoTPetLayout([string] $Directory) {
    $root = Assert-PlainDirectory $Directory
    $allowed = @('DoTPet.exe','app','runtime','실행 방법.txt','LICENSE','THIRD_PARTY_NOTICES.md','Code-signing-policy.md','.dotpet-install.json')
    foreach ($item in Get-ChildItem -LiteralPath $root -Force) {
        if ($item.Name -notin $allowed) { throw "Unmanaged content in the application folder: $($item.Name)" }
    }
    foreach ($name in @('DoTPet.exe', 'app\dotpet.jar', 'app\DoTPet.cfg', 'runtime\bin\java.exe')) {
        if (!(Test-Path -LiteralPath (Join-Path $root $name) -PathType Leaf)) { throw "Incomplete DoT Pet package: $name" }
    }
    if (!(Get-Content -LiteralPath (Join-Path $root 'app\DoTPet.cfg') -Raw).Contains('app.mainclass=com.dotpet.Main')) {
        throw 'The package is not a DoT Pet application.'
    }
    foreach ($item in Get-ChildItem -LiteralPath $root -Recurse -Force) {
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Package contains a link: $($item.FullName)" }
        if (!$item.PSIsContainer -and ($item.Extension -eq '.dotpet' -or $item.Name -in @('active-pet.zip','input.properties','program.properties'))) {
            throw 'A package/install directory must not contain user-created profiles or settings.'
        }
    }
}

function Get-DoTPetSignatureReport {
    param([Parameter(Mandatory)][string] $Directory)
    Assert-DoTPetLayout $Directory
    $root = [IO.Path]::GetFullPath($Directory)
    foreach ($file in Get-ChildItem -LiteralPath $root -Recurse -File | Where-Object { $_.Extension -in '.exe', '.dll' }) {
        $signature = Get-AuthenticodeSignature -LiteralPath $file.FullName
        [pscustomobject]@{ File = $file.FullName.Substring($root.TrimEnd('\').Length + 1); Status = $signature.Status.ToString() }
    }
    # Only the Windows x64 payload is relevant for the current Windows x64 app.
    $scratch = Join-Path ([IO.Path]::GetTempPath()) ('dotpet-signature-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $scratch | Out-Null
    try {
        foreach ($jar in Get-ChildItem -LiteralPath (Join-Path $root 'app') -Filter '*.jar') {
            $archive = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
            try {
                foreach ($entry in $archive.Entries | Where-Object { $_.FullName -match '(?i)^[^/]+\.dll$|/win32-x86-64/[^/]+\.dll$' }) {
                    if ($entry.Length -gt 100MB) { throw 'Unexpected native library size.' }
                    $path = Join-Path $scratch ([guid]::NewGuid().ToString('N') + '.dll')
                    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $path)
                    try {
                        $signature = Get-AuthenticodeSignature -LiteralPath $path
                        [pscustomobject]@{ File = ('app\' + $jar.Name + '!/' + $entry.FullName); Status = $signature.Status.ToString() }
                    } finally { Remove-Item -LiteralPath $path -Force }
                }
            } finally { $archive.Dispose() }
        }
    } finally { Remove-Item -LiteralPath $scratch -Force }
}

function Expand-DoTPetArchive {
    param([Parameter(Mandatory)][string] $ZipPath, [Parameter(Mandatory)][string] $Destination,
          [Parameter(Mandatory)][string] $ExpectedSha256)
    if ($ExpectedSha256 -notmatch '^[a-fA-F0-9]{64}$') { throw 'An expected SHA-256 digest is required.' }
    if ((Get-FileHash -LiteralPath $ZipPath -Algorithm SHA256).Hash -ne $ExpectedSha256) { throw 'The download SHA-256 digest does not match.' }
    $root = Assert-PlainDirectory $Destination
    if (Test-Path -LiteralPath $root) { throw 'Extraction destination already exists.' }
    $archive = [IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($ZipPath))
    try {
        if ($archive.Entries.Count -gt 5000) { throw 'Too many archive entries.' }
        $names = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        [long] $total = 0
        foreach ($entry in $archive.Entries) {
            $name = $entry.FullName.Replace('\', '/')
            if (!$name.StartsWith('DoTPet-Windows/', [StringComparison]::Ordinal) -or $name.Contains(':') -or
                $name -match '[\x00-\x1f<>"|?*]' -or $name -match '(^|/)\.\.?(/|$)' -or
                @($name.Split('/') | Where-Object {$_ -and ($_ -match '[. ]$' -or $_ -match '^(?i:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)')}).Count -or
                !$names.Add($name)) { throw "Unsafe archive path: $name" }
            if ((($entry.ExternalAttributes -shr 16) -band 0xF000) -eq 0xA000) { throw 'Symbolic links are not allowed in the package.' }
            $total += $entry.Length
            if ($total -gt 600MB -or $entry.Length -gt 200MB) { throw 'Archive is too large.' }
        }
    } finally { $archive.Dispose() }
    [IO.Compression.ZipFile]::ExtractToDirectory([IO.Path]::GetFullPath($ZipPath), $root)
    $app = Join-Path $root 'DoTPet-Windows'
    Assert-DoTPetLayout $app
    return $app
}

function Install-DoTPetPackage {
    param([Parameter(Mandatory)][string] $PackageDirectory, [Parameter(Mandatory)][string] $InstallDirectory)
    $source = Assert-PlainDirectory $PackageDirectory
    $target = Assert-PlainDirectory $InstallDirectory
    if ($source -eq $target -or $source.StartsWith($target + '\', [StringComparison]::OrdinalIgnoreCase) -or
        $target.StartsWith($source + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Package and installation must be separate directories.' }
    Assert-DoTPetLayout $source
    $blocked = @(Get-DoTPetSignatureReport -Directory $source | Where-Object Status -ne 'Valid')
    if ($blocked.Count) { throw ("Installation stopped: {0} native files lack a trusted signature. First: {1}. The current installation is unchanged." -f $blocked.Count,$blocked[0].File) }
    $parent = Assert-PlainDirectory ([IO.Path]::GetDirectoryName($target))
    $marker = Join-Path $target '.dotpet-install.json'
    if (Test-Path -LiteralPath $target) {
        if (!(Test-Path -LiteralPath $marker -PathType Leaf)) { throw 'Refusing to overwrite an unmanaged folder.' }
        $installed = Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json
        if ($installed.application -ne 'malko10/dotpet') {
            throw 'Refusing to overwrite an unmanaged folder.'
        }
        Assert-DoTPetLayout $target
        $known = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        foreach ($name in $installed.files) { [void] $known.Add($name) }
        foreach ($file in Get-ChildItem -LiteralPath $target -Recurse -File -Force) {
            $relative = $file.FullName.Substring($target.Length + 1)
            if ($relative -ne '.dotpet-install.json' -and !$known.Contains($relative)) { throw "Preserving an extra user file; update stopped: $relative" }
        }
    }
    $running = @(Get-CimInstance Win32_Process -Filter "Name='DoTPet.exe'" | Where-Object { $_.ExecutablePath -eq (Join-Path $target 'DoTPet.exe') })
    if ($running.Count) { throw 'Close the running DoT Pet before updating. No process was stopped.' }
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    $backup = Join-Path $parent ('.dotpet-backup-' + [guid]::NewGuid().ToString('N'))
    if ([IO.Path]::GetDirectoryName($backup) -ne $parent) { throw 'Invalid backup destination.' }
    $hadPrevious = Test-Path -LiteralPath $target
    try {
        if ($hadPrevious) { Move-Item -LiteralPath $target -Destination $backup }
        Move-Item -LiteralPath $source -Destination $target
        $files = @(Get-ChildItem -LiteralPath $target -Recurse -File -Force | ForEach-Object {$_.FullName.Substring($target.Length + 1)} | Where-Object {$_ -ne '.dotpet-install.json'})
        @{application='malko10/dotpet';version=(Get-Item -LiteralPath (Join-Path $target 'DoTPet.exe')).VersionInfo.ProductVersion;files=$files} |
            ConvertTo-Json | Set-Content -LiteralPath $marker -Encoding UTF8
    } catch {
        # Keep both copies if a rollback cannot complete; do not erase either.
        if (Test-Path -LiteralPath $target) {
            if (!(Test-Path -LiteralPath $source)) { Move-Item -LiteralPath $target -Destination $source }
        }
        if ($hadPrevious -and (Test-Path -LiteralPath $backup) -and !(Test-Path -LiteralPath $target)) { Move-Item -LiteralPath $backup -Destination $target }
        throw
    }
    if ($hadPrevious) {
        Assert-DoTPetLayout $backup
        if ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($backup)) -ne $parent) { throw 'Backup is outside the intended directory.' }
        Remove-Item -LiteralPath $backup -Recurse -Force
    }
    return (Join-Path $target 'DoTPet.exe')
}

Export-ModuleMember -Function Get-DoTPetSignatureReport, Expand-DoTPetArchive, Install-DoTPetPackage
