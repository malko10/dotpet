package com.dotpet.input;

public enum GestureDirection {
    LEFT("← 왼쪽"), RIGHT("오른쪽 →"), UP("↑ 위쪽"), DOWN("↓ 아래쪽");
    private final String label;
    GestureDirection(String label) { this.label = label; }
    public String getLabel() { return label; }
}
