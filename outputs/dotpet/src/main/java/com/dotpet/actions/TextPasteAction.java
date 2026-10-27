package com.dotpet.actions;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import com.dotpet.input.WindowsForegroundService;

/** Pastes text already placed on the clipboard into the currently focused application. */
public final class TextPasteAction {
    public void pasteClipboardText() throws AWTException, InterruptedException {
        pasteClipboardText(false);
    }

    public void pasteClipboardText(boolean pressEnter) throws AWTException, InterruptedException {
        WindowsForegroundService foreground = new WindowsForegroundService();
        foreground.waitForModifiersReleased();
        long target = foreground.requireExternalWindowHandle();
        Robot robot = new Robot();
        robot.setAutoDelay(70);
        sendPaste(robot, pressEnter, () -> foreground.requireSameWindow(target));
    }

    /** Kept separate so input ordering and cancellation can be checked without desktop injection. */
    static void sendPaste(Robot robot, boolean pressEnter, Runnable verifyTarget) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("붙여넣기를 중지했습니다.");
        verifyTarget.run();
        robot.keyPress(KeyEvent.VK_CONTROL);
        try { robot.keyPress(KeyEvent.VK_V); }
        finally { robot.keyRelease(KeyEvent.VK_V); robot.keyRelease(KeyEvent.VK_CONTROL); }
        if (pressEnter) {
            Thread.sleep(250);
            verifyTarget.run();
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Enter 입력을 중지했습니다.");
            try { robot.keyPress(KeyEvent.VK_ENTER); }
            finally { robot.keyRelease(KeyEvent.VK_ENTER); }
        }
    }
}
