@echo off
setlocal
chcp 65001 >nul
set "DOTPET_EXE=%LOCALAPPDATA%\Programs\DoTPet\DoTPet.exe"
if not exist "%DOTPET_EXE%" goto missing_package
start "" "%DOTPET_EXE%"
exit /b 0

:missing_package
echo 서명 검증을 통과한 DoT Pet을 아직 고정 폴더에 설치하지 않았습니다.
echo 설치 대상: %LOCALAPPDATA%\Programs\DoTPet
echo 기존 ZIP을 실행하는 경우 폴더 전체를 압축 해제한 뒤 DoTPet.exe를 실행하세요.
pause
exit /b 1
