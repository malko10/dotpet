package com.dotpet.actions;

import com.dotpet.input.InputAction;
import com.dotpet.input.WindowsInputService;
import com.dotpet.settings.InputSettings;
import com.dotpet.settings.SettingsService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Shared, explicitly invoked actions for the UI, global shortcuts and gestures. */
public final class QuickActionService implements AutoCloseable {
    private final WindowsInputService input;
    private final Supplier<InputSettings> preferences;
    private final Function<InputSettings, CompletableFuture<Void>> savePreferences;
    private final ClipboardService clipboard = new ClipboardService();
    private final AutoClickService autoClick;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> daemon(task, "dotpet-actions"));
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(task -> daemon(task, "dotpet-action-countdown"));
    private final AtomicBoolean occupied = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private final Object runLock = new Object();
    private final ReadOnlyStringWrapper status = new ReadOnlyStringWrapper("필요한 작업을 선택하세요.");
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper autoActive = new ReadOnlyBooleanWrapper();
    private volatile ScheduledFuture<?> countdown;
    private volatile Future<?> task;
    private volatile boolean closed;
    private boolean ownsInput;

    public QuickActionService(WindowsInputService input, Supplier<InputSettings> preferences,
            Function<InputSettings, CompletableFuture<Void>> savePreferences) {
        this(input, preferences, savePreferences, new AutoClickService());
    }

    QuickActionService(WindowsInputService input, Supplier<InputSettings> preferences,
            Function<InputSettings, CompletableFuture<Void>> savePreferences, AutoClickService autoClick) {
        this.input = input;
        this.preferences = preferences;
        this.savePreferences = savePreferences;
        this.autoClick = autoClick;
        status.set("필요한 작업을 직접 선택하세요. 자동으로 시작되는 작업은 없습니다.");
    }

    public static QuickActionService standalone() {
        AtomicReference<InputSettings> settings = new AtomicReference<>(InputSettings.defaults());
        AtomicReference<QuickActionService> service = new AtomicReference<>();
        WindowsInputService nativeInput = new WindowsInputService(new SettingsService().getDirectory(),
                action -> { if (action == InputAction.STOP_AUTO_CLICK && service.get() != null) service.get().stop(); }, ignored -> {});
        QuickActionService result = new QuickActionService(nativeInput, settings::get, next -> nativeInput.configure(next).thenRun(() -> settings.set(next)));
        result.ownsInput = true; service.set(result);
        return result;
    }

    public InputSettings getPreferences() { return preferences.get(); }
    public ReadOnlyStringProperty statusProperty() { return status.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty busyProperty() { return busy.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty autoActiveProperty() { return autoActive.getReadOnlyProperty(); }
    public boolean isBusy() { return occupied.get() || autoClick.isRunning(); }

    public CompletableFuture<Void> updatePreferences(String text, int interval, int count) {
        return updatePreferences(text, interval, count, preferences.get().infiniteClick());
    }

    public CompletableFuture<Void> updatePreferences(String text, int interval, int count, boolean repeatForever) {
        try {
            InputSettings current = preferences.get();
            InputSettings next = current.withQuickPreferences(text, interval, count, repeatForever);
            return next.equals(current) ? CompletableFuture.completedFuture(null) : savePreferences.apply(next);
        }
        catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
    }

    public void report(String message) { if (!closed) onFx(() -> { if (!closed) status.set(message); }); }

    public void copyText() {
        String text = preferences.get().savedText();
        if (text.isBlank()) { report("먼저 빠른 작업에서 사용할 문구를 입력하고 저장하세요."); return; }
        try { clipboard.copyText(text); report("등록한 문구를 복사했어요."); }
        catch (RuntimeException error) { report("문구 복사 실패: " + error.getMessage()); }
    }

    public void copyFilePath(Stage owner) {
        stop();
        FileChooser chooser = new FileChooser(); chooser.setTitle("경로를 복사할 파일 선택");
        var file = chooser.showOpenDialog(owner);
        if (file != null) {
            try { clipboard.copyText(file.getAbsolutePath()); report("파일 경로를 복사했어요: " + file.getName()); }
            catch (RuntimeException error) { report("파일 경로 복사 실패: " + error.getMessage()); }
        }
    }

    public void copyBrowserUrl(int preparationSeconds) {
        long run = begin(); if (run < 0) return;
        afterCountdown(run, preparationSeconds, "복사할 브라우저를 선택하세요", () -> runWorker(run, () -> {
            new BrowserUrlAction().copyFromFocusedBrowser();
            onFx(() -> {
                if (generation.get() != run || closed) return;
                String value = clipboard.readText();
                report(value != null && value.matches("(?is)^[a-z][a-z0-9+.-]*:.*")
                        ? "브라우저 주소를 복사했어요." : "주소를 읽지 못했어요. 브라우저를 선택한 뒤 다시 실행하세요.");
                finish(run);
            });
        }));
    }

    public void pasteText(int preparationSeconds) {
        String text = preferences.get().savedText();
        if (text.isBlank()) { report("먼저 빠른 작업에서 붙여넣을 문구를 입력하고 저장하세요."); return; }
        long run = begin(); if (run < 0) return;
        try { clipboard.copyText(text); }
        catch (RuntimeException error) { failed(run, error); return; }
        afterCountdown(run, preparationSeconds, "붙여넣을 프로그램을 선택하세요", () -> runWorker(run, () -> {
            new TextPasteAction().pasteClipboardText();
            if (generation.get() == run) { report("등록 문구를 붙여넣었어요."); finish(run); }
        }));
    }

    public void startAutoClick() {
        synchronized (runLock) {
            InputSettings settings = preferences.get();
            long run = begin(); if (run < 0) return;
            onFx(() -> autoActive.set(true));
            report("긴급 중지 단축키 " + settings.hotkeys().get(InputAction.STOP_AUTO_CLICK) + " 준비 중…");
            input.armEmergency(true).whenComplete((ignored, error) -> {
                if (generation.get() != run || closed) return;
                if (error != null) { failed(run, error); return; }
                String mode = settings.infiniteClick() ? "무한 반복" : settings.clickCount() + "회";
                afterCountdown(run, 3, "오토 클릭 " + mode + " · 포인터를 옮기세요 · " + settings.hotkeys().get(InputAction.STOP_AUTO_CLICK) + " 중지", () -> {
                    synchronized (runLock) {
                        if (generation.get() != run || closed) return;
                        try {
                            autoClick.start(settings.clickIntervalMillis(), settings.clickCount(), settings.infiniteClick(),
                                    message -> { if (generation.get() == run) report(message); },
                                    failure -> failed(run, failure), () -> completeAutoClick(run));
                        } catch (RuntimeException failure) { failed(run, failure); }
                    }
                });
            });
        }
    }

    /** Uses the existing clipboard as-is; never reads, replaces or persists its contents. */
    public void pasteClipboard(boolean pressEnter) {
        long run = begin(); if (run < 0) return;
        runWorker(run, () -> {
            new TextPasteAction().pasteClipboardText(pressEnter);
            if (generation.get() == run) {
                report(pressEnter ? "붙여넣고 Enter를 입력했어요." : "클립보드 내용을 붙여넣었어요.");
                finish(run);
            }
        });
    }

    private void completeAutoClick(long run) {
        synchronized (runLock) {
            if (generation.get() != run) return;
            input.armEmergency(false);
            finish(run);
        }
    }

    /** May be invoked directly by the native input thread so a busy UI cannot delay stopping. */
    public void stop() {
        synchronized (runLock) {
            if (closed) return;
            long stopped = generation.incrementAndGet();
            ScheduledFuture<?> timer = countdown; if (timer != null) timer.cancel(false);
            Future<?> currentTask = task; if (currentTask != null) currentTask.cancel(true);
            autoClick.stop(); occupied.set(false);
            input.armEmergency(false);
            onFx(() -> { if (generation.get() == stopped && !closed) {
                busy.set(false); autoActive.set(false); status.set("작업을 중지했어요.");
            } });
        }
    }

    private long begin() {
        synchronized (runLock) {
            if (closed) return -1;
            if (!occupied.compareAndSet(false, true)) { report("진행 중인 작업을 먼저 중지하세요."); return -1; }
            long token = generation.incrementAndGet();
            onFx(() -> busy.set(true));
            return token;
        }
    }

    private void afterCountdown(long run, int seconds, String message, Runnable operation) {
        synchronized (runLock) {
            if (generation.get() != run || closed) return;
            if (seconds == 0) { onFx(() -> { if (generation.get() == run && !closed) operation.run(); }); return; }
            AtomicInteger remaining = new AtomicInteger(seconds);
            AtomicReference<ScheduledFuture<?>> ownTimer = new AtomicReference<>();
            report(message + " · " + seconds + "초");
            countdown = clock.scheduleAtFixedRate(() -> {
                if (generation.get() != run || closed) return;
                int left = remaining.decrementAndGet();
                if (left > 0) report(message + " · " + left + "초");
                else {
                    ScheduledFuture<?> timer = ownTimer.get(); if (timer != null) timer.cancel(false);
                    onFx(() -> { if (generation.get() == run && !closed) operation.run(); });
                }
            }, 1, 1, TimeUnit.SECONDS);
            ownTimer.set(countdown);
        }
    }

    @FunctionalInterface private interface Operation { void run() throws Exception; }
    private void runWorker(long run, Operation operation) {
        task = worker.submit(() -> {
            try { if (generation.get() == run && !closed) operation.run(); }
            catch (Exception error) { failed(run, error); }
        });
    }

    private void failed(long run, Throwable error) {
        synchronized (runLock) {
            if (generation.get() != run) return;
            Throwable root = error; while (root.getCause() != null) root = root.getCause();
            report("작업을 실행하지 못했어요: " + root.getMessage());
            input.armEmergency(false);
            finish(run);
        }
    }

    private void finish(long run) {
        synchronized (runLock) {
            if (generation.get() != run) return;
            occupied.set(false);
            onFx(() -> { if (generation.get() == run) { busy.set(false); autoActive.set(false); } });
        }
    }

    private static void onFx(Runnable operation) { if (Platform.isFxApplicationThread()) operation.run(); else Platform.runLater(operation); }
    private static Thread daemon(Runnable task, String name) { Thread thread = new Thread(task, name); thread.setDaemon(true); return thread; }

    @Override public void close() {
        synchronized (runLock) {
            if (closed) return;
            closed = true; generation.incrementAndGet();
            ScheduledFuture<?> timer = countdown; if (timer != null) timer.cancel(false);
            autoClick.close(); worker.shutdownNow(); clock.shutdownNow();
            if (ownsInput) input.close();
        }
    }
}
