# Windows signing and fixed-folder installation

## State on 2026-11-10

- The public SignPath application is drafted, not submitted. The maintainer still
  needs to enter their name/email and review the required agreements.
- Original code and slime artwork now have the maintainer-authorized MIT license.
- No signing service, certificate, token or signature is available yet.
- The v0.6.2 package audit found 117 valid native signatures and 9 unsigned files:
  the application EXE, seven OpenJFX DLLs, and JNA's Windows x64 `jnidispatch.dll`.
- The download currently extracted on this PC is v0.6.1. Its EXE hash matches the
  archived official v0.6.1 release. Updating to another unsigned release does not
  resolve the signing problem.

## SignPath activation

The [policy](code-signing-policy.md) explains scope and privacy. The initial
[artifact configuration](signpath-artifact.xml) signs only DoTPet.exe, preserving
all upstream binaries. The root ZIP is GitHub's workflow artifact archive; its
inner `DoTPet-Windows.zip` is the release ZIP.

After Foundation acceptance, the maintainer must enable MFA, link the repository
using the SignPath GitHub integration, and configure:

| Setting | Value / location |
| --- | --- |
| Organization ID | GitHub repository variable `SIGNPATH_ORGANIZATION_ID` |
| CI token | GitHub Actions secret `SIGNPATH_API_TOKEN` (never in chat/source) |
| Project slug | `dotpet` |
| Signing policy | `release-signing`, manual approval required |
| Artifact configuration | `windows-portable`, reviewed XML from this repository |

The Windows workflow invokes the pinned official SignPath v3 action only for a
release tag with an organization configured. It waits for manual approval and
fails publication if any native file, including a DLL inside a JAR, does not have
a valid signature. There is no unsigned fallback after a signing failure.
Before activation, ordinary builds remain unsigned and no compatibility claim is
made. Existing release assets are not replaced or relabeled as signed.

**The upstream DLL trust problem still needs a compatible distribution solution.**
Foundation rules prohibit signing upstream libraries as our own. A signed MSIX
package is one option to investigate: Microsoft documents common catalog signing
for [packaged apps](https://learn.microsoft.com/en-us/windows/security/threat-protection/windows-defender-application-control/manage-packaged-apps-with-windows-defender-application-control).
This is not implemented or verified for DoT Pet; in particular, JavaFX/JNA's
runtime extraction of DLLs must be accounted for before relying on that approach.

## Installation and updates

After an approved, fully verified release is available, the maintainer can run
this from the workspace in an already authorized maintenance environment:

```powershell
.\scripts\Update-DoTPet.ps1
```

These are maintenance scripts, not a new end-user launcher. The current Windows
PowerShell 5.1 policy refuses `.ps1` execution; that check stopped without changing
the policy or retrying with a bypass. The 27 filesystem checks passed in the
existing PowerShell 7 development environment and will run on GitHub's `pwsh`
runner. PowerShell 5.1 execution has not been validated. End users will keep using
the same desktop shortcut to the trusted application EXE once setup is complete.

This explicitly downloads the latest release from `malko10/dotpet`, verifies the
GitHub SHA-256 digest and package layout, then checks signatures for the EXE,
runtime and native DLLs inside JARs. Any failure stops before replacing the
installation. There is no bypass flag or OS security configuration change.

The fixed target is `%LOCALAPPDATA%\Programs\DoTPet\DoTPet.exe`. Updates replace the
whole app/runtime together; a manifest prevents overwriting an unrelated folder
or deleting extra user files. A failed move restores the previous installation.
An already running pet must be closed first. The desktop shortcut stays `DoT Pet`;
neither the updater nor the cleanup tool launches an application.

User data stays at `%APPDATA%\DoTPet`, outside the installation. To uninstall, close
the pet, remove only the fixed `Programs\DoTPet` folder and its desktop shortcut.
Keep `%APPDATA%\DoTPet` if you want to retain characters and preferences.

## Old-package cleanup

The user authorized cleanup **after signing/setup is complete and the new app is
verified usable**. No old packages have been deleted during preparation.

```powershell
.\scripts\Clean-OldDoTPet.ps1          # review candidates only
.\scripts\Clean-OldDoTPet.ps1 -Apply   # only after a successful signed-app launch
```

Candidates are limited to generated `outputs/DoTPet-Windows-v*` folders and known
DoTPet-Windows folders/ZIPs directly in Downloads. Sources and exported artwork
are excluded. The apply step also requires a managed fixed installation and valid
native signatures. A maintainer still must verify a successful normal app launch
before running it. Suspicious folders or embedded user profiles are skipped.
Generated build-check artifacts under `work/` are separate developer scratch data.

Verification: the updater's filesystem tests use generated non-executed fixtures
and mock only the signing boundary for transaction tests. Actual unsigned bytes
are rejected first. Hash/path checks, fixed path, complete replacement, preservation
of profiles/extra files, rollback and running-process protection are covered.
The real v0.6.2 audit detects all nine blockers; no signed installation/cleanup or
successful normal launch is claimed.
