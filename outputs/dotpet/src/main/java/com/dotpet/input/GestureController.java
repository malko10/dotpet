package com.dotpet.input;

import java.util.Map;

/** Recognizes one middle-button stroke using only its start and end; no input history. */
public final class GestureController {
    private boolean enabled;
    private boolean pressed;
    private int startX, startY, endX, endY;
    private long startedAt;
    private int threshold = 70;
    private Map<GestureDirection, InputAction> bindings = Map.of();

    public void configure(boolean enabled, int threshold, Map<GestureDirection, InputAction> bindings) {
        this.enabled = enabled;
        this.threshold = threshold;
        this.bindings = Map.copyOf(bindings);
        cancel();
    }

    public void press(int x, int y, long now, boolean blocked) {
        cancel();
        if (!enabled || blocked) return;
        pressed = true; startedAt = now; startX = endX = x; startY = endY = y;
    }

    public void move(int x, int y, long now, boolean blocked) {
        if (!pressed) return;
        if (blocked || now - startedAt > 2_500_000_000L) { cancel(); return; }
        endX = x; endY = y;
    }

    public InputAction release(int x, int y, long now, boolean blocked) {
        if (!pressed) return InputAction.NONE;
        move(x, y, now, blocked);
        if (!pressed) return InputAction.NONE;
        int dx = endX - startX, dy = endY - startY;
        cancel();
        if (Math.max(Math.abs(dx), Math.abs(dy)) < threshold) return InputAction.NONE;
        if (Math.abs(dx) >= Math.abs(dy) * 1.2) return bindings.getOrDefault(dx < 0 ? GestureDirection.LEFT : GestureDirection.RIGHT, InputAction.NONE);
        if (Math.abs(dy) >= Math.abs(dx) * 1.2) return bindings.getOrDefault(dy < 0 ? GestureDirection.UP : GestureDirection.DOWN, InputAction.NONE);
        return InputAction.NONE;
    }

    public void cancel() { pressed = false; startX = startY = endX = endY = 0; startedAt = 0; }
}
