@echo off
setlocal
chcp 65001 >nul
set "DOTPET_EXE=%~dp0..\DoTPet-Windows-v0.3\DoTPet.exe"
if not exist "%DOTPET_EXE%" goto missing_package
start "" "%DOTPET_EXE%"
exit /b 0

:missing_package
echo DoTPet-Windows-v0.3 폴더 안의 DoTPet.exe를 실행해 주세요.
echo Java를 설치할 필요는 없습니다. 실행 파일과 app, runtime 폴더를 함께 두세요.
pause
exit /b 1
