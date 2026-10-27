package com.dotpet.settings;

import com.dotpet.actions.AutoClickService;
import com.dotpet.input.GestureDirection;
import com.dotpet.input.Hotkey;
import com.dotpet.input.InputAction;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Immutable user-selected bindings and quick-action preferences. */
public record InputSettings(boolean hotkeysEnabled, boolean gesturesEnabled,
        Map<InputAction, Hotkey> hotkeys, Map<GestureDirection, InputAction> gestures,
        int gestureThreshold, String savedText, int clickIntervalMillis, int clickCount, boolean infiniteClick) {
    /** Older settings/callers retain finite repetition. */
    public InputSettings(boolean hotkeysEnabled, boolean gesturesEnabled,
            Map<InputAction, Hotkey> hotkeys, Map<GestureDirection, InputAction> gestures,
            int gestureThreshold, String savedText, int clickIntervalMillis, int clickCount) {
        this(hotkeysEnabled, gesturesEnabled, hotkeys, gestures, gestureThreshold, savedText, clickIntervalMillis, clickCount, false);
    }
    public InputSettings {
        hotkeys = Map.copyOf(hotkeys);
        gestures = Map.copyOf(gestures);
        savedText = savedText == null ? "" : savedText;
        if (savedText.length() > 20_000) throw new IllegalArgumentException("등록 문구는 20000자까지 사용할 수 있어요.");
        if (gestureThreshold < 32 || gestureThreshold > 200) throw new IllegalArgumentException("제스처 거리는 32~200px로 설정하세요.");
        if (clickIntervalMillis < AutoClickService.MIN_INTERVAL_MILLIS || clickIntervalMillis > AutoClickService.MAX_INTERVAL_MILLIS
                || clickCount < 1 || clickCount > AutoClickService.MAX_CLICKS) throw new IllegalArgumentException("오토 클릭 간격 또는 횟수가 범위를 벗어났어요.");
        if (!hotkeys.containsKey(InputAction.STOP_AUTO_CLICK)) throw new IllegalArgumentException("긴급 중지 단축키는 반드시 지정해야 합니다.");
        Map<Hotkey, InputAction> used = new HashMap<>();
        for (var entry : hotkeys.entrySet()) {
            if (entry.getKey() == InputAction.NONE) throw new IllegalArgumentException("사용 안 함에는 단축키를 지정할 수 없어요.");
            InputAction previous = used.put(entry.getValue(), entry.getKey());
            if (previous != null) throw new IllegalArgumentException(entry.getValue() + "가 ‘" + previous.getLabel() + "’와 ‘" + entry.getKey().getLabel() + "’에 중복되어 있어요.");
        }
    }

    public static InputSettings defaults() {
        EnumMap<InputAction, Hotkey> keys = new EnumMap<>(InputAction.class);
        keys.put(InputAction.OPEN_QUICK_ACTIONS, new Hotkey(3, 'Q'));
        keys.put(InputAction.COPY_BROWSER_URL, new Hotkey(3, 'U'));
        keys.put(InputAction.COPY_FILE_PATH, new Hotkey(3, 'F'));
        keys.put(InputAction.COPY_TEXT, new Hotkey(3, 'T'));
        keys.put(InputAction.PASTE_TEXT, new Hotkey(3, 'P'));
        keys.put(InputAction.START_AUTO_CLICK, new Hotkey(3, 0x75));
        keys.put(InputAction.STOP_AUTO_CLICK, new Hotkey(0, 0x77));
        keys.put(InputAction.TOGGLE_MOVEMENT, new Hotkey(3, 'M'));
        keys.put(InputAction.TOGGLE_GRAVITY, new Hotkey(3, 'G'));
        return new InputSettings(false, false, keys,
                Map.of(GestureDirection.LEFT, InputAction.COPY_BROWSER_URL,
                        GestureDirection.RIGHT, InputAction.OPEN_QUICK_ACTIONS,
                        GestureDirection.UP, InputAction.COPY_TEXT,
                        GestureDirection.DOWN, InputAction.STOP_AUTO_CLICK),
                70, "", 1000, 10);
    }

    public InputSettings withQuickPreferences(String text, int interval, int count) {
        return withQuickPreferences(text, interval, count, infiniteClick);
    }

    public InputSettings withQuickPreferences(String text, int interval, int count, boolean repeatForever) {
        return new InputSettings(hotkeysEnabled, gesturesEnabled, hotkeys, gestures, gestureThreshold, text, interval, count, repeatForever);
    }
}
