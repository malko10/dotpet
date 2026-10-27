package com.dotpet.input;

/** Only implemented actions can be assigned to global input. */
public enum InputAction {
    NONE("사용 안 함"),
    OPEN_QUICK_ACTIONS("빠른 작업 열기"),
    COPY_BROWSER_URL("브라우저 주소 복사"),
    COPY_FILE_PATH("파일 선택 후 경로 복사"),
    COPY_TEXT("등록 문구 복사"),
    PASTE_TEXT("등록 문구 붙여넣기"),
    PASTE_CLIPBOARD("붙여넣기"),
    PASTE_CLIPBOARD_ENTER("붙여넣고 Enter"),
    START_AUTO_CLICK("오토 클릭 시작"),
    STOP_AUTO_CLICK("긴급 중지"),
    TOGGLE_MOVEMENT("펫 자동 이동 켜기/끄기"),
    TOGGLE_GRAVITY("펫 공중 고정 켜기/끄기");

    private final String label;
    InputAction(String label) { this.label = label; }
    public String getLabel() { return label; }
    @Override public String toString() { return label; }
}
