package com.dotpet.actions;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;

/** Runs an explicitly started sequence of left clicks on a worker thread until count or stop. */
public final class AutoClickService implements AutoCloseable {
    public static final int MIN_INTERVAL_MILLIS = 100;
    public static final int MAX_INTERVAL_MILLIS = 60_000;
    public static final int MAX_CLICKS = 10_000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "dotpet-auto-click");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private volatile Future<?> currentTask;
    private final Supplier<Robot> robotFactory;

    public AutoClickService() {
        this(() -> {
            try { return new Robot(); }
            catch (AWTException failure) { throw new IllegalStateException("마우스 자동화를 시작하지 못했습니다.", failure); }
        });
    }

    /** Allows deterministic execution checks without sending clicks to the desktop. */
    AutoClickService(Supplier<Robot> robotFactory) { this.robotFactory = robotFactory; }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void start(int intervalMillis, int clickCount, Consumer<String> status,
                                   Consumer<Throwable> errorHandler) {
        start(intervalMillis, clickCount, status, errorHandler, () -> {});
    }

    public synchronized void start(int intervalMillis, int clickCount, Consumer<String> status,
                                   Consumer<Throwable> errorHandler, Runnable onFinished) {
        start(intervalMillis, clickCount, false, status, errorHandler, onFinished);
    }

    public synchronized void start(int intervalMillis, int clickCount, boolean repeatForever,
                                   Consumer<String> status, Consumer<Throwable> errorHandler, Runnable onFinished) {
        if (intervalMillis < MIN_INTERVAL_MILLIS || intervalMillis > MAX_INTERVAL_MILLIS) {
            throw new IllegalArgumentException("클릭 간격은 100~60000ms로 설정해 주세요.");
        }
        if (clickCount < 1 || clickCount > MAX_CLICKS) {
            throw new IllegalArgumentException("반복 횟수는 1~10000회로 설정해 주세요.");
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("오토 클릭이 이미 실행 중입니다.");
        }

        long runId = generation.incrementAndGet();
        currentTask = executor.submit(() -> {
            long completed = 0;
            Robot robot = null;
            boolean buttonPressed = false;
            try {
                robot = robotFactory.get();
                while (running.get() && generation.get() == runId && (repeatForever || completed < clickCount)) {
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    buttonPressed = true;
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    buttonPressed = false;
                    completed++;
                    if ((!repeatForever && completed == clickCount) || completed % 10 == 0) {
                        publish(status, repeatForever ? "오토 클릭 실행 중 · 무한 반복 · " + completed + "회"
                                : "오토 클릭 실행 중 · " + completed + "/" + clickCount);
                    }
                    if ((repeatForever || completed < clickCount) && running.get() && generation.get() == runId) {
                        TimeUnit.MILLISECONDS.sleep(intervalMillis);
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException failure) {
                publish(errorHandler, failure);
            } finally {
                if (buttonPressed && robot != null) {
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                }
                if (generation.get() == runId) {
                    running.set(false);
                    publish(status, !repeatForever && completed == clickCount
                        ? "오토 클릭 완료 · " + completed + "회"
                        : "오토 클릭 중지 · " + completed + "회 실행");
                }
                if (onFinished != null) Platform.runLater(onFinished);
            }
        });
        publish(status, repeatForever ? "오토 클릭을 시작했습니다 · 무한 반복" : "오토 클릭을 시작했습니다.");
    }

    public synchronized void stop() {
        generation.incrementAndGet();
        if (!running.getAndSet(false)) return;
        Future<?> task = currentTask;
        if (task != null) task.cancel(true);
    }

    @Override
    public void close() {
        stop();
        executor.shutdownNow();
    }

    private static void publish(Consumer<String> callback, String value) {
        if (callback != null) Platform.runLater(() -> callback.accept(value));
    }

    private static void publish(Consumer<Throwable> callback, Throwable failure) {
        if (callback != null) Platform.runLater(() -> callback.accept(failure));
    }
}
