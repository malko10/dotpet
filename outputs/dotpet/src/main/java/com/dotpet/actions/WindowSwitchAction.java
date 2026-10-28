package com.dotpet.actions;

import com.dotpet.input.WindowsForegroundService;
import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;

/** Switches once to the previous window; releases Alt even on cancellation or input failure. */
public final class WindowSwitchAction {
    public void execute() throws AWTException, InterruptedException {
        var foreground = new WindowsForegroundService();
        foreground.waitForModifiersReleased();
        long target = foreground.requireForegroundWindowHandle();
        Robot robot = new Robot();
        robot.setAutoDelay(70);
        sendSwitch(robot, () -> foreground.requireSameWindow(target));
    }

    static void sendSwitch(Robot robot, Runnable verifyStart) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("창 전환을 중지했습니다.");
        verifyStart.run();
        robot.keyPress(KeyEvent.VK_ALT);
        try {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("창 전환을 중지했습니다.");
            robot.keyPress(KeyEvent.VK_TAB);
        } finally {
            try { robot.keyRelease(KeyEvent.VK_TAB); }
            finally { robot.keyRelease(KeyEvent.VK_ALT); }
        }
    }
}
