package com.dotpet.input;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Native registrations on the Windows input thread; never hooks ordinary typing. */
final class HotkeyController implements AutoCloseable {
    private record Registration(int id, InputAction action) { }
    private final Map<Hotkey, Registration> registered = new HashMap<>();
    private final Consumer<InputAction> handler;
    private int nextId = 100;

    HotkeyController(Consumer<InputAction> handler) { this.handler = handler; }

    void apply(Map<InputAction, Hotkey> desired) {
        Map<Hotkey, Registration> next = new HashMap<>();
        ArrayList<Integer> added = new ArrayList<>();
        try {
            for (var entry : desired.entrySet()) {
                Hotkey combo = entry.getValue();
                if (next.containsKey(combo)) throw new IllegalArgumentException("단축키가 중복되어 있어요: " + combo);
                Registration previous = registered.get(combo);
                int id = previous == null ? nextId++ : previous.id();
                if (previous == null) {
                    if (id > 0xBFFF) throw new IllegalStateException("단축키 설정 횟수가 너무 많습니다. 앱을 다시 실행하세요.");
                    if (!User32.INSTANCE.RegisterHotKey(null, id, combo.modifiers() | 0x4000, combo.virtualKey()))
                        throw new IllegalArgumentException(combo + "를 등록하지 못했어요. 다른 프로그램이나 Windows에서 사용 중일 수 있습니다. (오류 "
                                + Kernel32.INSTANCE.GetLastError() + ")");
                    added.add(id);
                }
                next.put(combo, new Registration(id, entry.getKey()));
            }
        } catch (RuntimeException failure) {
            for (int id : added) User32.INSTANCE.UnregisterHotKey(null, id);
            throw failure;
        }
        for (var entry : registered.entrySet()) if (!next.containsKey(entry.getKey())) User32.INSTANCE.UnregisterHotKey(null, entry.getValue().id());
        registered.clear(); registered.putAll(next);
    }

    void dispatch(int id) {
        for (Registration registration : registered.values()) {
            if (registration.id() == id) { handler.accept(registration.action()); return; }
        }
    }

    @Override public void close() {
        for (Registration registration : registered.values()) User32.INSTANCE.UnregisterHotKey(null, registration.id());
        registered.clear();
    }
}
