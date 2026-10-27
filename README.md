# DoT Pet · 도트펫

Java 21과 JavaFX로 만든 Windows용 픽셀 데스크톱 펫입니다.

- 작업표시줄 위 걷기, 중력과 포물선 던지기, 벽 충돌과 착지 모션
- 반원형 기능 메뉴, 픽셀·애니메이션 편집, 직접 만든 캐릭터 적용
- 빠른 작업, 전역 단축키와 휠 제스처, 횟수/무한 오토 클릭과 긴급 중지
- 가끔 농담과 잡지식을 말하는 크림색 말풍선

![펫의 수다 말풍선](docs/speech-bubble.png)

## 실행

[Releases](https://github.com/malko10/dotpet/releases)에서 Windows ZIP을 내려받아 **전체 압축을 푼 뒤 `DoTPet.exe`**를 실행하세요. Java를 따로 설치할 필요가 없습니다. EXE와 `app`, `runtime` 폴더를 함께 두세요.

기존 펫을 먼저 종료하고 새 버전을 실행하세요. 오토 클릭은 직접 시작해야 하며 기본 **F8**로 중지합니다. 전역 단축키·제스처는 펫 좌클릭 → 키 설정에서 켤 수 있습니다.

## 개발

소스는 [`outputs/dotpet`](outputs/dotpet)에 있습니다. JDK 21을 준비하고 프로젝트 폴더에서 실행하세요.

```powershell
cd outputs/dotpet
.\gradlew.bat build
.\gradlew.bat run
```

Windows 배포본 생성:

```powershell
.\package-windows.ps1 -JavaHome $env:JAVA_HOME -Destination ..\DoTPet-Windows
```

자세한 기능·제약·검증 내용은 [프로젝트 설명서](outputs/dotpet/README.md)에 있습니다. 일반 프로그램 설정과 펫 위치·선택 복원은 아직 구현되지 않았습니다.

## 이 PC에서 자동 커밋

이 작업 폴더의 소스 수정이 **25초 동안 멈추면 커밋과 GitHub 푸시**를 수행합니다. 대화에서 진행하는 수정은 작업과 확인을 마친 뒤 한 번에 반영합니다. 빌드 결과와 개인 데이터는 제외합니다.

- 켜기: `Start-GitHub-Sync.bat`
- 끄기: `Stop-GitHub-Sync.bat`
- 상태·로그 보기: `GitHub-Sync-Status.bat`

[자동 동기화 설명](docs/github-sync.md)을 참고하세요. 인터넷이나 GitHub 인증 문제가 있으면 변경을 로컬에 보관하고 재시도합니다. 충돌을 강제로 덮어쓰지 않습니다.

## 자동 빌드와 배포

`main`에 반영되면 GitHub Actions가 Windows에서 빌드하고 실행 파일 ZIP을 빌드 산출물로 보관합니다. `v*` 태그를 올리면 해당 버전의 ZIP을 Releases에 공개합니다. 소스 자동 반영과 정식 버전 배포는 각각 기록됩니다.
