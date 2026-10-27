package com.dotpet.actions;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/** Reads or writes clipboard text only when an explicit quick action requests it. */
public final class ClipboardService {
    public void copyText(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        if (!Clipboard.getSystemClipboard().setContent(content)) {
            throw new IllegalStateException("클립보드에 내용을 복사하지 못했습니다.");
        }
    }

    public String readText() {
        return Clipboard.getSystemClipboard().getString();
    }
}
