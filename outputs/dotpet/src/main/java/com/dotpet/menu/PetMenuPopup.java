package com.dotpet.menu;

import java.util.function.Consumer;
import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;
import javafx.stage.Popup;
import javafx.stage.Stage;

/** Keeps the pet paused until a dismissal animation has actually finished. */
final class PetMenuPopup extends Popup {
    private PetMenuFan fan;
    private boolean closing;
    private Runnable afterHidden;

    PetMenuPopup(Consumer<Boolean> visibilityChanged) {
        setAutoHide(true);
        setHideOnEscape(true);
        setAutoFix(false);
        setOnShown(event -> visibilityChanged.accept(true));
        setOnHidden(event -> {
            if (fan != null) fan.stop();
            closing = false;
            visibilityChanged.accept(false);
            Runnable action = afterHidden;
            afterHidden = null;
            if (action != null) action.run();
        });
    }

    void showFan(Stage owner, Pane content, Point2D origin, double x, double y) {
        fan = new PetMenuFan(content, origin);
        getContent().setAll(content);
        // Owner-node presses reach the pet's toggle handler instead of auto-hiding first.
        show(owner.getScene().getRoot(), x, y);
        fan.play();
    }

    @Override public void hide() {
        if (!isShowing() || closing) return;
        if (getOwnerWindow() == null || !getOwnerWindow().isShowing()) {
            hideImmediately();
            return;
        }
        closing = true;
        fan.close(super::hide);
    }

    void closeAfter(Runnable action) {
        if (!isShowing() || closing) return;
        afterHidden = action;
        hide();
    }

    void hideImmediately() {
        afterHidden = null;
        if (fan != null) fan.stop();
        super.hide();
    }
}
