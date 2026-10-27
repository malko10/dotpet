package com.dotpet.settings;

/** Last applied desktop choices; velocity and automation execution are deliberately not restored. */
public record ProgramSettings(boolean movementEnabled, boolean gravityEnabled, boolean speechEnabled,
        boolean alwaysOnTop, boolean rememberPosition, boolean hasPosition, double x, double y, double sizeScale) {
    public ProgramSettings {
        if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("펫 위치가 올바르지 않습니다.");
        if (!Double.isFinite(sizeScale) || sizeScale < 0.5 || sizeScale > 2)
            throw new IllegalArgumentException("펫 크기는 50~200%로 설정하세요.");
    }
    public static ProgramSettings defaults() { return new ProgramSettings(true, true, true, true, true, false, 0, 0, 1); }
}
