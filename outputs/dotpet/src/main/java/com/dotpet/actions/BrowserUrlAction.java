package com.dotpet.actions;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import com.dotpet.input.WindowsForegroundService;

/** Copies the focused browser's address bar using the documented Ctrl+L, Ctrl+C flow. */
public final class BrowserUrlAction {
    public void copyFromFocusedBrowser() throws AWTException, InterruptedException {
        WindowsForegroundService foreground = new WindowsForegroundService();
        foreground.waitForModifiersReleased();
        long target = foreground.requireBrowserWindow();
        Robot robot = new Robot();
        robot.setAutoDelay(70);
        pressShortcut(robot, KeyEvent.VK_L);
        Thread.sleep(180);
        foreground.requireSameWindow(target);
        pressShortcut(robot, KeyEvent.VK_C);
        Thread.sleep(250);
    }

    private static void pressShortcut(Robot robot, int keyCode) {
        robot.keyPress(KeyEvent.VK_CONTROL);
        try { robot.keyPress(keyCode); }
        finally { robot.keyRelease(keyCode); robot.keyRelease(KeyEvent.VK_CONTROL); }
    }
}
