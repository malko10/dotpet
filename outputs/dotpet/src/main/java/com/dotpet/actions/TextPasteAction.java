package com.dotpet.actions;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import com.dotpet.input.WindowsForegroundService;

/** Pastes text already placed on the clipboard into the currently focused application. */
public final class TextPasteAction {
    public void pasteClipboardText() throws AWTException, InterruptedException {
        WindowsForegroundService foreground = new WindowsForegroundService();
        foreground.waitForModifiersReleased();
        foreground.requireExternalWindow();
        Robot robot = new Robot();
        robot.setAutoDelay(70);
        robot.keyPress(KeyEvent.VK_CONTROL);
        try { robot.keyPress(KeyEvent.VK_V); }
        finally { robot.keyRelease(KeyEvent.VK_V); robot.keyRelease(KeyEvent.VK_CONTROL); }
    }
}
