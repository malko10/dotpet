package com.dotpet.input;

import com.dotpet.settings.InputSettings;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** Dedicated Win32 message loop for registered hotkeys and an opt-in mouse gesture hook. */
public final class WindowsInputService implements AutoCloseable {
    private static final int WAKE_MESSAGE = 0x8001;
    private record Pending(Runnable execute, Runnable cancel) { }
    private final ConcurrentLinkedQueue<Pending> pending = new ConcurrentLinkedQueue<>();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final Consumer<InputAction> actionHandler;
    private final Consumer<String> failureHandler;
    private final Path lockDirectory;
    private final GestureController gestures = new GestureController();
    private volatile InputSettings current = InputSettings.defaults();
    private volatile boolean emergencyArmed;
    private volatile boolean petDragging;
    private volatile boolean suspended;
    private volatile boolean keyboardAutomation;
    private volatile boolean closed;
    private volatile int threadId;
    private Thread thread;
    private HotkeyController hotkeys;
    private WinUser.HHOOK mouseHook;
    private WinUser.LowLevelMouseProc mouseCallback;
    private boolean leftPressed;
    private FileChannel lockChannel;
    private FileLock inputLock;

    public WindowsInputService(Path lockDirectory, Consumer<InputAction> actionHandler, Consumer<String> failureHandler) {
        this.lockDirectory = lockDirectory;
        this.actionHandler = actionHandler;
        this.failureHandler = failureHandler;
    }

    public void setPetDragging(boolean dragging) { petDragging = dragging; }
    public CompletableFuture<Void> suspendForEditing(boolean suspended) {
        return submit(() -> {
            this.suspended = suspended;
            if (!suspended) applyConfiguration(current, emergencyArmed);
            else {
                EnumMap<InputAction, Hotkey> onlyEmergency = new EnumMap<>(InputAction.class);
                if (emergencyArmed) onlyEmergency.put(InputAction.STOP_AUTO_CLICK, current.hotkeys().get(InputAction.STOP_AUTO_CLICK));
                hotkeys.apply(onlyEmergency);
                gestures.configure(false, current.gestureThreshold(), current.gestures());
                uninstallMouseHook();
                if (!emergencyArmed) releaseLock();
            }
            return null;
        });
    }
    public InputSettings getCurrentSettings() { return current; }

    /** Unregister shortcuts while sending their underlying editing keys to the target application. */
    public CompletableFuture<Void> pauseForKeyboardAutomation(boolean paused) {
        return submit(() -> {
            keyboardAutomation = paused;
            if (paused || suspended) {
                EnumMap<InputAction, Hotkey> desired = new EnumMap<>(InputAction.class);
                if (emergencyArmed) desired.put(InputAction.STOP_AUTO_CLICK, current.hotkeys().get(InputAction.STOP_AUTO_CLICK));
                hotkeys.apply(desired);
                gestures.cancel();
            } else applyConfiguration(current, emergencyArmed);
            return null;
        });
    }
    public boolean isEmergencyArmed() { return emergencyArmed; }

    public CompletableFuture<Void> configure(InputSettings settings) {
        return submit(() -> { applyConfiguration(settings, emergencyArmed); return null; });
    }

    public CompletableFuture<Void> armEmergency(boolean armed) {
        return submit(() -> { applyConfiguration(current, armed); return null; });
    }

    private synchronized void ensureStarted() {
        if (thread != null) return;
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            ready.completeExceptionally(new IllegalStateException("전역 입력은 Windows 10/11에서 지원됩니다.")); return;
        }
        thread = new Thread(this::messageLoop, "dotpet-windows-input");
        thread.setDaemon(true); thread.start();
    }

    private synchronized <T> CompletableFuture<T> submit(Callable<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("입력 서비스가 종료되었습니다."));
        // Queue before startup: CompletableFuture startup listeners do not preserve submission order.
        Pending job = new Pending(() -> {
            try { result.complete(operation.call()); } catch (Throwable error) { result.completeExceptionally(error); }
        }, () -> result.completeExceptionally(new IllegalStateException("입력 서비스가 종료되었습니다.")));
        pending.add(job);
        ensureStarted();
        ready.whenComplete((ignored, startupError) -> {
            if (startupError != null) { pending.remove(job); result.completeExceptionally(startupError); return; }
            if (closed) { pending.remove(job); job.cancel().run(); return; }
            if (User32.INSTANCE.PostThreadMessage(threadId, WAKE_MESSAGE, new WPARAM(0), new LPARAM(0)) == 0) {
                pending.remove(job); result.completeExceptionally(new IllegalStateException("Windows 입력 스레드에 요청하지 못했습니다."));
            }
        });
        return result;
    }

    private void messageLoop() {
        try {
            threadId = Kernel32.INSTANCE.GetCurrentThreadId();
            WinUser.MSG message = new WinUser.MSG();
            User32.INSTANCE.PeekMessage(message, null, 0, 0, 0);
            hotkeys = new HotkeyController(this::dispatch);
            ready.complete(null);
            while (!closed) {
                int received = User32.INSTANCE.GetMessage(message, null, 0, 0);
                if (received == 0) break;
                if (received < 0) throw new IllegalStateException("Windows 입력 메시지를 읽지 못했습니다.");
                if (message.message == WAKE_MESSAGE) {
                    Pending job; while ((job = pending.poll()) != null) job.execute().run();
                } else if (message.message == WinUser.WM_HOTKEY) hotkeys.dispatch(message.wParam.intValue());
                else { User32.INSTANCE.TranslateMessage(message); User32.INSTANCE.DispatchMessage(message); }
            }
        } catch (Throwable failure) {
            ready.completeExceptionally(failure);
            report("전역 입력을 시작하지 못했어요: " + failure.getMessage());
        } finally {
            closed = true;
            uninstallMouseHook();
            if (hotkeys != null) hotkeys.close();
            releaseLock();
            Pending job; while ((job = pending.poll()) != null) job.cancel().run();
        }
    }

    private void applyConfiguration(InputSettings settings, boolean armEmergency) throws IOException {
        boolean active = settings.hotkeysEnabled() || settings.gesturesEnabled() || armEmergency;
        boolean newlyLocked = active && inputLock == null;
        if (newlyLocked) acquireLock();
        boolean installed = false;
        try {
            if (settings.gesturesEnabled() && mouseHook == null) { installMouseHook(); installed = true; }
            EnumMap<InputAction, Hotkey> desired = new EnumMap<>(InputAction.class);
            if (settings.hotkeysEnabled() && !keyboardAutomation) desired.putAll(settings.hotkeys());
            if (armEmergency) desired.put(InputAction.STOP_AUTO_CLICK, settings.hotkeys().get(InputAction.STOP_AUTO_CLICK));
            hotkeys.apply(desired);
            gestures.configure(settings.gesturesEnabled(), settings.gestureThreshold(), settings.gestures());
            current = settings;
            emergencyArmed = armEmergency;
            if (!settings.gesturesEnabled()) uninstallMouseHook();
            if (!active) releaseLock();
        } catch (RuntimeException | Error failure) {
            if (installed) uninstallMouseHook();
            if (newlyLocked) releaseLock();
            throw failure;
        }
    }

    private void installMouseHook() {
        mouseCallback = (code, message, data) -> {
            try {
                if (code >= 0) {
                    int event = message.intValue();
                    boolean blocked = suspended || keyboardAutomation || petDragging || leftPressed;
                    long now = System.nanoTime();
                    if (event == 0x0201) { leftPressed = true; gestures.cancel(); }
                    else if (event == 0x0202) leftPressed = false;
                    else if (event == 0x0204) gestures.cancel();
                    else if (event == 0x0207) gestures.press(data.pt.x, data.pt.y, now, blocked);
                    else if (event == 0x0200) gestures.move(data.pt.x, data.pt.y, now, blocked);
                    else if (event == 0x0208) dispatch(gestures.release(data.pt.x, data.pt.y, now, blocked));
                }
            } catch (RuntimeException error) { gestures.cancel(); report("제스처 처리 오류: " + error.getMessage()); }
            return User32.INSTANCE.CallNextHookEx(mouseHook, code, message,
                    new LPARAM(data == null ? 0 : Pointer.nativeValue(data.getPointer())));
        };
        mouseHook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_MOUSE_LL, mouseCallback, Kernel32.INSTANCE.GetModuleHandle(null), 0);
        if (mouseHook == null) { mouseCallback = null; throw new IllegalStateException("마우스 제스처 감지를 시작하지 못했어요. (Windows 오류 " + Kernel32.INSTANCE.GetLastError() + ")"); }
    }

    private void uninstallMouseHook() {
        gestures.cancel(); leftPressed = false;
        if (mouseHook != null && (User32.INSTANCE.UnhookWindowsHookEx(mouseHook) || Kernel32.INSTANCE.GetLastError() == 1404)) {
            mouseHook = null; mouseCallback = null;
        }
    }

    private void dispatch(InputAction action) {
        if (closed || action == InputAction.NONE || ((suspended || keyboardAutomation) && action != InputAction.STOP_AUTO_CLICK)) return;
        try { actionHandler.accept(action); } catch (RuntimeException failure) { report("입력 동작 오류: " + failure.getMessage()); }
    }

    private void acquireLock() throws IOException {
        Files.createDirectories(lockDirectory);
        lockChannel = FileChannel.open(lockDirectory.resolve("global-input.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            try { inputLock = lockChannel.tryLock(); }
            catch (OverlappingFileLockException inUse) { inputLock = null; }
        } catch (IOException | RuntimeException failure) {
            lockChannel.close(); lockChannel = null;
            throw failure;
        }
        if (inputLock == null) { lockChannel.close(); lockChannel = null; throw new IllegalStateException("다른 DoT Pet에서 전역 입력을 사용 중이에요. 그 앱의 입력 설정을 먼저 꺼 주세요."); }
    }

    private void releaseLock() {
        try { if (inputLock != null) inputLock.release(); } catch (IOException ignored) { }
        try { if (lockChannel != null) lockChannel.close(); } catch (IOException ignored) { }
        inputLock = null; lockChannel = null;
    }

    private void report(String text) { if (failureHandler != null) failureHandler.accept(text); }

    @Override public void close() {
        closed = true;
        if (threadId != 0) User32.INSTANCE.PostThreadMessage(threadId, 0x0012, new WPARAM(0), new LPARAM(0));
    }
}
