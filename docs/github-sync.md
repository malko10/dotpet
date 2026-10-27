# GitHub 자동 동기화

이 PC의 현재 작업 폴더와 `https://github.com/malko10/dotpet`의 `main`을 연결합니다.

소스와 설명서를 저장하고 25초 동안 추가 변경이 없으면 자동 커밋·푸시합니다. Windows 로그인 때 같은 감시 작업이 다시 실행됩니다. 이 기능은 이 PC에 설치한 작업이며, 저장소를 내려받은 다른 PC에는 자동 설치되지 않습니다.

## 제어

작업 폴더에서 `Start-GitHub-Sync.bat`, `Stop-GitHub-Sync.bat`, `GitHub-Sync-Status.bat`를 사용하세요. 끄면 자동 실행용 바로가기도 제거합니다. 한 번만 즉시 반영하려면 다음 명령을 실행하세요.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\Sync-GitHub.ps1 -Once -Message "Describe the change"
```

감시 주기와 마지막 성공·실패 기록은 `.git/dotpet-sync/sync.log`에 남깁니다. 인증 정보는 Git Credential Manager가 Windows에서 관리하며 저장소나 스크립트에 저장하지 않습니다.

## 반영 범위

- `outputs/dotpet`의 소스·리소스·Gradle 설정·배포 스크립트
- 루트 설명서·Git 설정 파일·`AGENTS.md`
- `docs`, `scripts`, `.github`의 개발·자동 빌드 설정

`work`, 빌드 폴더, Java 런타임, EXE/ZIP 배포본, 내려받은 라이브러리, 개인 설정·캐릭터 데이터는 소스 커밋에서 제외합니다. ZIP은 GitHub Releases로 배포합니다.

## 작업 중 잠시 멈추기

`.git/dotpet-sync/paused` 파일이 있으면 새 커밋과 푸시를 하지 않습니다. Codex는 수정 전에 이 파일을 만들고 검증을 마친 뒤 제거합니다. 수동으로 Git에서 파일을 스테이징한 경우에도 감시 작업은 기다립니다.

원격 저장소가 앞서 있거나 충돌·병합·리베이스가 진행 중이면 자동으로 덮어쓰지 않습니다. 네트워크 오류일 때는 로컬 커밋을 보관하고 나중에 다시 푸시합니다. 원격 작업과 통합이 필요하면 상태 로그를 확인하고 해당 변경을 먼저 검토하세요.

이 폴더를 옮기면 이전 감시 작업을 끄고 새 위치에서 `Start-GitHub-Sync.bat`를 실행하세요. 이미 커밋한 소스 변경은 저장소의 커밋 기록에서 복원할 수 있습니다.

## 검증 기록

2026-10-28 기준으로 소스만 커밋·푸시하는지, 빌드·임시 파일을 제외하는지, 수동 스테이징과 일시 중지를 존중하는지 검사했습니다. 백그라운드 변경 감지와 대기 시간, 연결 오류 시 로컬 커밋 보존, 연결 복구 후 재전송도 별도 테스트 저장소에서 확인했습니다.
