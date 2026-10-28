package com.dotpet.input;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.Pointer;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Checks only the foreground process when an explicit automation action is requested. */
public final class WindowsForegroundService {
    private static final Set<String> BROWSERS = Set.of("chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe", "vivaldi.exe");

    public boolean isSupportedBrowserFocused() {
        return BROWSERS.contains(processName());
    }

    public long requireBrowserWindow() {
        var window = User32.INSTANCE.GetForegroundWindow();
        if (window == null || !isSupportedBrowserFocused())
            throw new IllegalStateException("Chrome·Edge·Firefox·Brave·Opera·Vivaldi 중 복사할 브라우저를 먼저 선택하세요.");
        return Pointer.nativeValue(window.getPointer());
    }

    public void requireSameWindow(long handle) {
        var current = User32.INSTANCE.GetForegroundWindow();
        if (current == null || Pointer.nativeValue(current.getPointer()) != handle)
            throw new IllegalStateException("작업 중 대상 창이 바뀌어 입력을 중지했어요. 원하는 창에서 다시 실행하세요.");
    }

    public void requireExternalWindow() {
        requireExternalWindowHandle();
    }

    public long requireForegroundWindowHandle() {
        var window = User32.INSTANCE.GetForegroundWindow();
        if (window == null) throw new IllegalStateException("전환할 창을 선택하세요.");
        return Pointer.nativeValue(window.getPointer());
    }

    public long requireExternalWindowHandle() {
        var window = User32.INSTANCE.GetForegroundWindow();
        if (window == null) throw new IllegalStateException("작업할 대상 창을 선택하세요.");
        IntByReference processId = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(window, processId);
        if (processId.getValue() == Kernel32.INSTANCE.GetCurrentProcessId())
            throw new IllegalStateException("작업할 다른 프로그램을 먼저 선택하세요.");
        return Pointer.nativeValue(window.getPointer());
    }

    public void waitForModifiersReleased() throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while ((User32.INSTANCE.GetAsyncKeyState(0x10) & 0x8000) != 0
                || (User32.INSTANCE.GetAsyncKeyState(0x11) & 0x8000) != 0
                || (User32.INSTANCE.GetAsyncKeyState(0x12) & 0x8000) != 0) {
            if (System.nanoTime() >= deadline) throw new IllegalStateException("단축키에서 손을 뗀 뒤 다시 실행하세요.");
            Thread.sleep(20);
        }
    }

    private String processName() {
        var window = User32.INSTANCE.GetForegroundWindow();
        if (window == null) return "";
        IntByReference processId = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(window, processId);
        var process = Kernel32.INSTANCE.OpenProcess(0x1000, false, processId.getValue());
        if (process == null) return "";
        try {
            char[] buffer = new char[32768]; IntByReference size = new IntByReference(buffer.length);
            if (!Kernel32.INSTANCE.QueryFullProcessImageName(process, 0, buffer, size)) return "";
            return Path.of(new String(buffer, 0, size.getValue())).getFileName().toString().toLowerCase(Locale.ROOT);
        } finally { Kernel32.INSTANCE.CloseHandle(process); }
    }
}
