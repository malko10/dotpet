package com.dotpet.actions;

import com.dotpet.input.WindowsForegroundService;
import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;

/** Sends select-all and optional copy to the explicitly targeted foreground application. */
public final class SelectionAction {
    public void execute(boolean copy) throws AWTException, InterruptedException {
        var foreground = new WindowsForegroundService();
        foreground.waitForModifiersReleased();
        long target = foreground.requireExternalWindowHandle();
        Robot robot = new Robot();
        robot.setAutoDelay(70);
        sendSelection(robot, copy, () -> foreground.requireSameWindow(target));
    }

    static void sendSelection(Robot robot, boolean copy, Runnable verifyTarget) throws InterruptedException {
        chord(robot, KeyEvent.VK_A, verifyTarget);
        if (copy) {
            Thread.sleep(150);
            chord(robot, KeyEvent.VK_C, verifyTarget);
        }
    }

    private static void chord(Robot robot, int key, Runnable verifyTarget) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("전체 선택 작업을 중지했습니다.");
        verifyTarget.run();
        robot.keyPress(KeyEvent.VK_CONTROL);
        try { robot.keyPress(key); }
        finally {
            try { robot.keyRelease(key); }
            finally { robot.keyRelease(KeyEvent.VK_CONTROL); }
        }
    }
}
