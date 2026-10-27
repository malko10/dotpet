package com.dotpet.input;

/** One Windows virtual-key combination. No typed text or event history is retained. */
public record Hotkey(int modifiers, int virtualKey) {
    public static final int ALT = 1, CTRL = 2, SHIFT = 4;

    public Hotkey {
        boolean letter = virtualKey >= 'A' && virtualKey <= 'Z';
        boolean number = virtualKey >= '0' && virtualKey <= '9';
        boolean function = virtualKey >= 0x70 && virtualKey <= 0x7A;
        if ((modifiers & ~7) != 0 || (!letter && !number && !function))
            throw new IllegalArgumentException("영문자·숫자·F1~F11 키를 사용하세요. Windows 키와 F12는 사용할 수 없어요.");
        if ((modifiers & (CTRL | ALT)) == 0 && !function)
            throw new IllegalArgumentException("문자·숫자에는 Ctrl 또는 Alt를 함께 지정하세요.");
        if (modifiers == ALT && virtualKey == 0x73)
            throw new IllegalArgumentException("Alt+F4는 창 닫기와 충돌하므로 다른 조합을 사용하세요.");
    }

    public String encode() { return modifiers + ":" + virtualKey; }
    public static Hotkey decode(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length != 2) throw new IllegalArgumentException("잘못된 단축키 설정입니다.");
        return new Hotkey(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    public String localConflictHint() {
        if (modifiers == CTRL && "ACVXSZ".indexOf(virtualKey) >= 0)
            return "다른 앱의 편집 단축키와 겹칠 수 있어요. Ctrl+Alt 조합을 권장합니다.";
        return "";
    }

    @Override public String toString() {
        String key = virtualKey >= 0x70 && virtualKey <= 0x7A ? "F" + (virtualKey - 0x70 + 1) : Character.toString((char) virtualKey);
        return ((modifiers & CTRL) != 0 ? "Ctrl+" : "") + ((modifiers & ALT) != 0 ? "Alt+" : "")
                + ((modifiers & SHIFT) != 0 ? "Shift+" : "") + key;
    }
}
