package com.dotpet.menu;

/** The four top-level actions shown around the desktop pet. */
public enum RadialMenuAction {
    QUICK_ACTIONS("빠른 작업", "⚡"),
    INPUT_SETTINGS("키 설정", "⌨"),
    PET_CUSTOMIZATION("캐릭터 꾸미기", "✦"),
    PROGRAM_SETTINGS("프로그램 설정", "⚙");

    private final String label;
    private final String icon;

    RadialMenuAction(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }
}
