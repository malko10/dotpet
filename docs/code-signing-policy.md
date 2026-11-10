# Code signing policy

## Current status

**Signing support is being requested; it has not been approved or activated.**
Releases through v0.6.2 are unsigned. No release currently claims a SignPath
signature or verified Smart App Control compatibility.

After acceptance, the intended attribution is:

> Free code signing provided by [SignPath.io](https://about.signpath.io),
> certificate by [SignPath Foundation](https://signpath.org).

The certificate remains controlled by SignPath Foundation. Applying does not
guarantee acceptance, certificate issuance, or that Windows will allow every
component of the application.

## Roles and approval

- Author, committer, and reviewer: [malko10](https://github.com/malko10).
- Release/signing approver: [malko10](https://github.com/malko10).
- Each signing request requires a manual approval in SignPath. The source sync
  watcher does not approve signing requests.
- The maintainer must enable MFA for GitHub and SignPath before activation.
  MFA status has not been verified by this repository.

Only artifacts built by this repository's GitHub-hosted Windows workflow are
eligible. Product and version metadata must match the requested release. Tokens
belong in GitHub Actions secrets and must not be committed.

## Signing scope and dependencies

The initial request covers the generated DoTPet.exe application launcher in the
portable Windows ZIP. OpenJDK, OpenJFX and JNA are third-party components and will
not be signed under the project's Foundation certificate.

The current portable ZIP contains unsigned OpenJFX and JNA native DLLs inside
JARs. Signing the EXE alone does **not** establish that the whole app can run under
Smart App Control. A signed package format or trusted upstream distributions
must also be reviewed, implemented, and tested before a compatible release is
advertised. See [Microsoft's signing guidance](https://learn.microsoft.com/en-us/windows/apps/develop/smart-app-control/code-signing-for-smart-app-control).

## Privacy

DoT Pet has no background telemetry or automatic data uploads. Network access is
limited to an action explicitly requested by its user or operator.

Pet artwork, settings, shortcuts and saved text remain in the local user data
directory. Clipboard contents and normal keyboard input are not continuously
collected or uploaded. Explicit web searches open the user's default browser and
send the query to the selected search engine; that browser and search provider's
privacy policies apply. Global shortcuts, optional mouse gestures and automation
operate locally. The maintenance updater contacts GitHub only when explicitly
run to download a release. GitHub's network/privacy policies then apply.
