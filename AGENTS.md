# DoT Pet workspace

- The Java 21 / JavaFX project is `outputs/dotpet`. Keep generated files and temporary checks under `work/` or ignored build/output directories.
- The user has explicitly authorized ongoing commits and pushes to this repository's GitHub `origin/main`. Finish meaningful changes and appropriate checks, then commit and push without asking again. Never force-push or automatically merge remote changes.
- A local background watcher publishes source after 25 seconds of inactivity. Before making a set of edits, create `.git/dotpet-sync/paused` (create the directory first). Keep the watcher paused while editing and testing. Remove that file when work is ready, then run `scripts/Sync-GitHub.ps1 -Once -Message '<concrete change summary>'`. This prevents publishing a half-finished agent turn.
- Do not publish `work/`, generated app images/ZIPs, local dependencies, credentials, or user profiles. Windows app ZIPs belong in GitHub Releases.
- If the remote has moved ahead, leave local work intact and report the rejected push. Do not reset, rebase, or overwrite it automatically.
- The current auto-sync state and logs are under `.git/dotpet-sync/`; `scripts/Manage-GitHub-Sync.ps1 -Mode Status` reports them. The watcher can be disabled with `-Mode Disable`.
