# 개발 참고 및 이전 검증 기록

실제 사용 안내는 [사용 설명서](../README.md)에 있습니다. 아래 내용은 0.3 개발 시점의 기록을 보관한 문서입니다.

## 0.4 / 10단계

- `PASTE_CLIPBOARD`, `PASTE_CLIPBOARD_ENTER`를 추가했습니다. 기존 등록 문구 기능과 저장된 입력 매핑은 유지하며 새 동작은 기본 단축키를 지정하지 않습니다. 실제 클립보드를 읽거나 덮어쓰지 않고 Ctrl+V를 보내고, Enter 직전에 같은 대상 창인지 다시 확인합니다. 키 수정자 해제와 취소를 처리하며 키 순서 검사는 실제 데스크톱 입력 없이 모의 키보드로 수행합니다.
- 프로그램 설정 메뉴를 실제 제어 창으로 교체했습니다. 자동 이동·중력·수다·항상 위·크기·위치 기억을 적용/저장하고 마지막 위치와 적용한 캐릭터를 복원합니다. 화면 밖 위치는 보정하며 저장된 오토 클릭 실행 상태는 복원하지 않습니다.
- `program.properties`는 가벼운 설정/위치를, `active-pet.zip`은 선택한 캐릭터의 ID·이름·색조·반전·네 애니메이션 전체를 저장합니다. 원자적 교체와 백업을 사용하고 손상된 원본은 읽는 동안 덮어쓰지 않습니다. 입력 설정도 백업 복원을 지원합니다.
- 파일 읽기·프로필 저장과 자동 체크포인트는 백그라운드에서 처리합니다. 위치는 약 2초 간격으로 기록하고 종료 시 최종 상태를 저장한 뒤 저장 작업을 마칩니다. 캐릭터 목록은 비동기로 읽고 손상된 항목만 건너뜁니다. 편집용 프로젝트와 프로필 메타데이터도 원자적으로 저장합니다.

검증: 모의 키보드로 Ctrl+V → 수정자 해제 → Enter 순서, 대상 창 변경 시 Enter 차단, 취소 시 키 해제를 검사했습니다. 실제 앱 시작·종료·재시작을 반복해 커스텀 픽셀·색조·반전·애니메이션 FPS·크기·위치·옵션 복원을 확인했습니다. 위치 기억 해제와 화면 밖 좌표 보정, 설정 창의 실제 제어, 두 새 동작의 단축키/제스처 선택과 기본 조합 복원도 통과했습니다. 원본 손상 후 백업 복구와 다음 저장 시 손상 원본의 `.corrupt-*` 별도 보존, 라이브러리 손상 항목 격리도 검사합니다. 저장한 무한 반복 설정이 앱 실행만으로 시작하지 않는지 확인했습니다.

0.4 배포본에 포함된 Java로 새 기능 및 기존 낙하·반전·메뉴·말풍선·무한 클릭 회귀 검사를 통과했습니다. EXE와 ZIP은 생성했으나 이 PC에서 EXE 직접 실행 검사는 Windows 앱 제어 정책에 의해 차단됐습니다. `Get-AuthenticodeSignature` 결과는 `NotSigned`입니다. 보안 정책은 변경하지 않았으며 EXE 실행 성공으로 보고하지 않습니다.

## 0.3.1 변경

- README 두 곳에서 개발·자동 동기화 설명을 빼고 사용자 조작 안내만 남겼습니다.
- 말풍선은 제목 없이 하나의 얇은 윤곽선과 연결된 꼬리로 표시합니다.
- 낙하 중 발의 최고 높이를 추적합니다. 작업 영역 높이의 50% 이상에서 내려온 경우에만 넘어집니다. 낮은 위치에서 빠르게 아래로 던져도 넘어지지 않으며, 위로 던져 높이 올라간 경우는 반영합니다.
- 넘어짐 회복의 40~100% 구간에 40% 확률로 붉은 화남 효과를 표시합니다. 같은 착지에서 확률을 다시 뽑지 않으며 메뉴·드래그·자동화 중에는 숨깁니다. 현재 프레임의 투명하지 않은 실제 픽셀 경계 위에 배치합니다.

배포본의 Java 런타임으로 낙하 높이·펫 크기·화면 원점 조합 45개, 말풍선 위치 조합 27개, 기존 메뉴 위치 조합 600개를 통과했습니다. 낮은 고속 낙하, 위로 던진 후 최고점, 중력 고정 후 높이 초기화, 회복 중 메뉴 정지, 화남 선택·숨김, 실제 그림 경계 추적, 포커스 유지도 확인했습니다. 기존 커스텀 모션·좌우 반전·에디터·수다 타이머·무한 클릭 및 긴급 중지 검사도 통과했습니다. 클릭 검사는 데스크톱에 클릭을 보내지 않는 모의 마우스를 사용합니다.

Java 21 + JavaFX desktop pet project for Windows 10/11.

## 0.3 새 기능

- **무한 오토 클릭:** 펫 좌클릭 → 빠른 작업 → `무한 반복` 체크 후 직접 시작하세요. 횟수 입력은 잠기고 기존 횟수 값은 유지됩니다. 기본값은 10회 유한 반복입니다. 저장한 무한 반복 설정을 불러와도 클릭은 자동 시작하지 않습니다.
- **중지:** 기본 F8(변경 가능), 긴급 중지 버튼, 빠른 작업 창 닫기, 앱 종료로 멈춥니다. 전역 중지 키 등록에 실패하면 시작하지 않습니다. 준비 중에도 중지할 수 있습니다.
- **펫 수다:** 직접 만든 농담과 잡지식 28개 중 하나를 가끔 말합니다. 같은 문장은 연속으로 나오지 않습니다. 조용한 상태에서는 첫 말풍선까지 약 12~20초, 이후에는 말풍선이 닫힌 뒤 35~75초를 쉽니다. 말풍선은 7초간 표시되며 눌러서 닫을 수 있습니다.
- **구별되는 디자인:** 메뉴와 작업 상태는 파란색, 수다 말풍선은 크림색 배경과 짙은 갈색 글자를 사용합니다. 펫을 가리지 않으며 화면 가장자리에서는 위아래 배치를 바꿉니다. 메뉴·도구 창·드래그·낙하·잠자기·자동화·작업 알림 중에는 숨기고, 상호작용이 끝난 뒤 잠시 기다렸다가 다시 말합니다. 화면 포커스를 가져오거나 소리를 내지 않습니다.

Changed files: `AutoClickService`, `QuickActionService`, `QuickActionsWindow` add the explicit infinite mode and its UI/status; `InputSettings`, `SettingsService`, `InputSettingsWindow` preserve it without changing legacy finite defaults. New `PetSpeechController` schedules chatter, `PetRemarks` contains local lines, and `PetSpeechBubble` renders the separate palette. `DesktopPetController` and `DesktopStatusView` coordinate chatter with menus, movement and automation. Build/package/launcher files now identify version 0.3.

Chatter is stored locally in the app; it does not inspect screen content, clipboard, or typing, and makes no runtime network requests. Trivia was checked against [NASA Venus](https://science.nasa.gov/venus/venus-facts/), [NASA Uranus](https://science.nasa.gov/uranus/facts/), [NOAA octopus hearts](https://www.noaa.gov/stories/love-is-in-air-and-sea-5-facts-about-affections-of-marine-creatures), and [MBL California two-spot octopus](https://www.mbl.edu/research/research-organisms/california-two-spot-octopus). The Venus line explicitly means one rotation, rather than a solar day.

## Development environment

- JDK 21 (LTS)
- Gradle Wrapper 9.8.2 (downloaded automatically on first use)
- JavaFX 21.0.13 (`javafx.controls`, resolved by Gradle)
- JNA + JNA Platform 5.19.1 (Windows hotkeys, foreground-window checks, optional mouse gestures)
- IntelliJ IDEA or VS Code

The source is compiled with `--release 21`. The build can run on JDK 21 or a newer JDK supported by Gradle. If Java is not on PATH, set `JAVA_HOME` to a JDK 21 installation before running the wrapper.

## Run

For normal Windows use, open the sibling `DoTPet-Windows-v0.3` folder and double-click `DoTPet.exe`. Java 21, JavaFX, and JNA are bundled inside that folder; Java/JDK installation, JAVA_HOME, Gradle, and the development `work` folder are not required. Move or extract the **whole folder**, keeping `DoTPet.exe`, `app`, and `runtime` together. Close the previous app before launching the new version. `Start-DoTPet.bat` in this source folder redirects to that portable executable.

`DoTPet-Windows-v0.3.zip` contains the same ready-to-run folder. Extract it before launching; moving only the EXE or the old BAT file is insufficient. Previous versions in the original workspace are untouched.

To rebuild the portable app from source, use `package-windows.ps1` with JDK 21 and the JavaFX dependencies resolved in the Gradle cache. JNA jars are included in `libs/`; the packaging script also supports the Gradle dependency cache. It compiles the source, builds a local Java runtime with jlink, and creates an app image with jpackage. It does not install software or change system settings. Choose a new `-Destination` when a previous app folder already exists.

From PowerShell in the project folder:

```powershell
.\gradlew.bat run
```

The current milestone targets Windows and includes `gradlew.bat`.

To build without opening the application window:

```powershell
.\gradlew.bat build
```

## Milestones 2–9

- The pet is shown in a borderless, transparent, always-on-top JavaFX window.
- It starts on the primary monitor's work-area bottom edge (above the taskbar) and walks horizontally along that baseline, pausing between walks and turning at screen edges.
- Drag the pet with the left mouse button. A quick diagonal fling follows a gravity-driven arc and lands on the work-area baseline.
- Right-click for three labeled icon cards. Movement shows `멈추기` or `걷게 하기`; gravity shows `공중 고정` or `중력 켜기`, with a smaller current-state hint under each action. Exit has a distinct red power icon. Restoring gravity resumes a fall if the pet is airborne. Press Space to toggle walking; press Escape to close the pet or a menu.
- Default idle, walk, run, and sleep animations are connected to pet state, with separate `Animation` and immutable `Frame` data classes. Wall collisions trigger a four-frame squash/rebound reaction (320 ms). A sufficiently fast landing plays eight impact, knockdown, and get-up frames (one second), then returns to idle. Dragging can interrupt a reaction, and menus pause its movement/recovery timer.
- Left-click for four labeled icon cards: quick actions, key settings, customization, and settings, each with an always-visible name and short description. Both menus use the same pet-centered upper arc with adaptive spacing for pet size. Near screen edges the arc rotates inward and can shorten at a corner. Each card is checked against the pet, screen, and other cards; the popup is not shifted over the sprite. There is no enclosing circle or background panel.
- The character menu opens a customization window with built-in and saved character selection, `.dotpet` import, size scaling, horizontal flip, color tint, live preview, save, and apply. Saved profiles live under `%APPDATA%\DoTPet\pets` with a `.dotpet` animation project and a small appearance metadata file.
- The customization window opens the pixel and animation editor. Drawing tools use drawn pencil, eraser, undo/redo, copy/paste, and clear icons with tooltips. The canvas, color palette, and crisp preview are visible immediately. Animation details are initially collapsed; opening them reveals frame thumbnails, behavior selection, FPS, onion skin, and frame operations.
- `새로 만들기` creates an independent blank character with its own ID. It is available in customization and inside the editor. In the simple view, drawing and applying a new pet uses that drawing in every behavior automatically. Empty characters cannot be applied. Existing projects keep their animations unless explicitly replaced.
- `모든 프레임에 복사` copies the current drawing to every frame in idle, walk, run, and sleep while retaining frame counts and speeds. This bulk action supports undo and redo. Individual frames can also be copied to and pasted from the clipboard. The editor retains 16×16, 32×32, and 64×64 canvases, PNG export, and editable `.dotpet` JSON save/load.
- Idle, walk, run, and sleep have separate timelines, initially containing 2, 4, 6, and 2 frames. Frames can be added, removed, copied, reordered, drawn, previewed with per-behavior 1–24 FPS, and applied to matching pet states. The `펫에 적용 후 닫기` button applies all four timelines and closes the editor so the result is immediately visible.
- Draw walk/run frames facing **left** only. The renderer automatically reflects the same timeline for rightward movement; separate left/right frame sets are unnecessary. The editor includes the direction guide and original/reflected preview switches. `.dotpet` files mark their source direction as `LEFT`. Existing right-facing artwork can use the customization window's horizontal-flip control as source correction.
- Falling resolves its animation from the currently applied idle appearance. Wall and landing reaction frames are regenerated from that appearance, retaining the selected character's pixels, colors, and transparency. These automatic reactions do not add drawing work to the four editable timelines. The ground baseline follows size changes.
- The editor shows a short usage guide. Input settings now opens the working global-input configuration window. Program settings is still a clearly labeled placeholder; persistent automatic startup pet selection/position and secondary-monitor roaming remain later work.
- The quick-actions window now copies a selected file's full path, copies user-entered text to the clipboard, and can paste that text into the active app after a short switch window. Browser URL capture gives five seconds to focus the browser before sending Ctrl+L and Ctrl+C.
- Auto click defaults to an explicit finite run of 10 clicks at 1-second intervals, bounded to 100–60,000 ms and 1–10,000 clicks. Version 0.3 adds a separately selected infinite mode that runs until stopped. A three-second preparation countdown lets the user move the pointer to the target. The window includes a stop button; a global emergency key is registered before starting. If that key is unavailable, the run does not start. Text snippets, interval, count, and infinite-mode preference are saved when used.

## Global input: 사용 방법

1. 펫 좌클릭 → **키 설정**을 여세요.
2. **전역 단축키 사용** 또는 **휠 드래그 제스처 사용**을 직접 켜세요. 첫 실행에서는 둘 다 꺼져 있습니다.
3. 단축키 입력 칸을 누르고 원하는 키 조합을 누르세요. 중복 조합은 저장할 수 없으며, Windows나 다른 프로그램의 등록 충돌은 저장 시 안내합니다. 편집 중에는 기존 전역 입력을 잠시 해제합니다.
4. 네 방향 휠 드래그에 원하는 기능을 지정하고 **저장하고 닫기**를 누르세요. 휠 버튼을 누른 채 설정한 거리 이상 드래그한 뒤 놓으면 실행됩니다.
5. 오토 클릭은 직접 시작해야 하며, 기본 **F8**로 긴급 중지합니다. 전역 입력이 꺼져 있어도 명시적으로 시작한 오토 클릭 동안에는 긴급 중지 키가 작동합니다.

| 기능 | 기본 단축키 |
| --- | --- |
| 빠른 작업 창 | Ctrl+Alt+Q |
| 현재 브라우저 URL 복사 | Ctrl+Alt+U |
| 파일 선택 후 경로 복사 | Ctrl+Alt+F |
| 등록 문구 복사 / 붙여넣기 | Ctrl+Alt+T / Ctrl+Alt+P |
| 오토 클릭 시작 / 중지 | Ctrl+Alt+F6 / F8 |
| 자동 이동 / 중력 전환 | Ctrl+Alt+M / Ctrl+Alt+G |

The shared `QuickActionService` connects menu buttons, hotkeys, and gestures to the same operations. Hotkeys use Windows `RegisterHotKey`; ordinary typing is not hooked or recorded. Gestures use an opt-in dedicated `WH_MOUSE_LL` thread and retain only the current middle-button stroke's coordinates/time. Left-button/pet dragging cancels a gesture. Tiny, diagonal, or stale strokes are ignored. The mouse hook passes events through, so a browser or other app may also perform its normal middle-button/autoscroll behavior. Disable gestures if that conflicts with your workflow. F12 and Windows-key combinations are not offered. Detection does not guarantee the absence of application-local shortcut conflicts.

URL capture checks that Chrome, Edge, Firefox, Brave, Opera, or Vivaldi is foreground and rechecks its window before copying. Hotkeys act on the currently focused app; quick-action buttons provide a preparation countdown. Keyboard actions wait for the trigger's modifier keys to be released. Elevated apps, secure desktops, fullscreen apps, or OS restrictions can block automation; the app never requests elevation. Browser-specific Ctrl+L/Ctrl+C behavior still needs user-environment compatibility testing.

Input preferences, the explicitly registered text snippet, and click settings are atomically stored in `%APPDATA%\DoTPet\input.properties`, with a backup. Missing or corrupt settings use disabled defaults and show a notice; a corrupt source is retained until an explicit save. Clipboard contents and input history are not stored or transmitted. A per-user file lock prevents two app instances from capturing configured global input at once.

The actual Gradle dependencies are `net.java.dev.jna:jna:5.19.1` and `net.java.dev.jna:jna-platform:5.19.1`. They call documented Windows APIs; see [JNA](https://github.com/java-native-access/jna), [RegisterHotKey](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-registerhotkey), and [LowLevelMouseProc](https://learn.microsoft.com/en-us/windows/win32/winmsg/lowlevelmouseproc). Both bundled dependency jars retain their `META-INF/LICENSE` notices.

### Stage 9 changed files and roles

| Files under `src/main/java/com/dotpet/` unless stated | Reason |
| --- | --- |
| `input/Hotkey.java`, `HotkeyController.java`, `InputAction.java` | Validate user combinations, reserve emergency stop, register exact Windows hotkeys and roll back conflicts. |
| `input/GestureDirection.java`, `GestureController.java`, `WindowsInputService.java` | Recognize four directions, isolate the native message loop, opt in/out, exclude pet dragging and preserve startup request order. |
| `input/InputSettingsWindow.java`, `WindowsForegroundService.java` | Provide real binding controls, pause detection while editing, and check automation targets. |
| `settings/InputSettings.java`, `SettingsService.java` | Validate and persist selected bindings, snippets and bounded-click preferences with atomic replacement and backup. |
| `actions/QuickActionService.java`, `QuickActionsWindow.java`, `BrowserUrlAction.java`, `TextPasteAction.java`, `AutoClickService.java` | Share operations across UI/global inputs, save preferences, release modifier keys, and stop automation independently of the UI. |
| `app/DesktopPetController.java`, `DesktopStatusView.java`, `customization/PetCustomizationWindow.java` | Connect real menus/actions, preserve foreground focus, show run/stop status, and track the open feature window. |
| `build.gradle`, `package-windows.ps1`, `Start-DoTPet.bat`, `libs/`, `README.md` | Include verified JNA dependencies in version 0.2, produce a portable runtime, point the launcher at the new package, and document use/limits. |

## Verification in the authoring environment

Version 0.3 was compiled with Java 21 and tested using its bundled runtime. The infinite/finite loop, restart, saving, legacy defaults, and emergency-key conflict were checked with a simulated mouse to avoid injecting clicks into other desktop apps. The actual native emergency shortcut stopped an infinite run while JavaFX was blocked. UI checks verified the checkbox, disabled finite-count field, persistence through input settings, and absence of automatic startup.

The live desktop controller displayed chatter through its real animation timer and hid it when quick actions opened. Timing checks covered the first delay, seven-second duration, quiet interval, no consecutive repeats, and interaction pause/resume. Actual popup checks covered 27 screen-edge/size combinations, pet visibility, the cream palette, and preserved foreground focus. Existing physics/reaction/reflection and 600 menu-layout combinations also passed. Gradle still fails before compilation with this environment's Windows loopback error; direct JDK compilation and the standalone runtime tests passed.

- The Gradle `help` task completed successfully with JDK 21.
- The Java sources compiled successfully with the JDK 21 compiler and JavaFX 21.0.13 modules.
- The version 0.2 bundled runtime passed native-input/controller integration and pet/menu regression checks. Its relocated EXE launched and closed cleanly with JAVA_HOME/JAVAFX_HOME absent, Java excluded from PATH, and an unrelated working directory. The previous user app was left untouched.
- Stage 9 passed actual Windows registration/dispatch, OS-conflict rollback, single-owner locking, native middle-button gestures, editing suspension/resume, and pet-drag exclusion. A finite auto-click run stopped via the native emergency key while the JavaFX thread was deliberately blocked; no subsequent clicks arrived. A conflicting emergency key prevented starting.
- The actual pet controller received a configured hotkey, preserved the other window's foreground focus while showing its result, rejected URL capture on a nonbrowser window, routed both input settings and quick-action menus, and saved preferences. Startup configuration now preserves request order even before the Windows message thread is ready.
- Four-direction recognition/cancellation, duplicate/reserved-key validation, Korean snippet round-trip, backup, corrupt-file fallback, and the input-settings capture/disable controls passed checks. The settings-window snapshot was inspected for layout.
- The self-contained Windows EXE launched its pet window after the whole app folder was moved to a path containing spaces, with JAVA_HOME/JAVAFX_HOME removed, Java excluded from PATH, and a working directory outside the app. It uses the bundled runtime and local JavaFX jars.
- The labeled left/right menus passed 600 screen-origin/edge/size combinations and eight actual popup placement checks. Visible labels, current-state hints, movement/gravity toggles, exit, and character-menu routing passed UI callbacks; scene previews were inspected.
- Ground and airborne wall collisions, rebound direction, landing knockdown/recovery, menu pause, drag interruption, and gravity removal passed simulation checks. Generated wall/landing frames and the live renderer retained custom sprite colors. An asymmetric walk/run sprite verified left originals, automatic right reflection, manual correction, and the editor's reflected preview.
- The revised left-click layout passed 300 combinations of screen origin, edge/corner location, and pet size (50%–200%) with no button/pet or button/button overlap.
- Four actual JavaFX popups at the taskbar baseline and right edge (100% and 200% pet sizes) also passed screen-coordinate overlap checks. The current application was relaunched after verification.
- The revised editor check exercised icon tools, simple and expanded layouts, blank New, drawing, all-frame copy/undo/redo, real `.dotpet` write/read, simple new-pet Apply, and independent profile creation. Scene snapshots were inspected for layout and sharp previews. Forty falling frames across two appearance applications retained the selected sprite.
- Pixel document drawing, transparent pixels, undo, redo, and conversion to an animation frame passed a Java 21 runtime check.
- A JavaFX runtime check fired the editor's apply button, verified the four default timelines, and confirmed that the applied pixel appeared in the live renderer. Animation project frame-count/FPS serialization and reload also passed.
- The live falling renderer kept the edited sprite; customization controls applied size, flip, and tint; a profile saved and reloaded successfully from a temporary user-data folder.
- The live right-click menu showed three labeled buttons in an arc, with no outer circle and no overlap with the pet. The customization window exposed its profile selector and appearance controls.
- The right-click arc layout passed checks at nine screen-edge and center positions. The live right-click menu had no enclosing circle and left the pet visible.
- The live quick-actions window exposed URL capture, file-path copy, text copy/paste, bounded click interval/count controls, and an emergency stop button. The URL and paste actions expose an explicit countdown to let the user focus the intended application.
- The quick-actions UI smoke check verified all controls and rejected an unsafe click interval before starting. A separate JavaFX test window received exactly three auto-clicks and the service stopped at the configured count.
- The editor accessibility view exposed the short usage guide; the updated radial menu screenshot showed only four function buttons and kept the pet visible between them.
- The updated app launched successfully; the radial menu was opened and visually checked with the pet visible in its transparent center, and the editor opened with its drawing, palette, export, and apply controls.
- Grounded walking, drag pause, diagonal throw and landing, gravity freeze/resume, screen clamping, movement toggle, animation frame timing, and radial menu selection/edge placement passed Java 21 checks. The transparent JavaFX pet window launched and remained responsive.
- Gradle `build` could not complete in the authoring environment because Gradle failed to establish a Windows loopback connection while starting its daemon. Direct compilation with the JDK 21 compiler succeeded; the Gradle daemon limitation still needs verification on the user's machine.
