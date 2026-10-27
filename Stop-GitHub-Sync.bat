@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Manage-GitHub-Sync.ps1" -Mode Disable
pause
