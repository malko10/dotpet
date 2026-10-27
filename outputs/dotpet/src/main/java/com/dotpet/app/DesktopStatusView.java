package com.dotpet.app;

import com.dotpet.pet.Pet;
import javafx.animation.PauseTransition;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

/** A brief result toast, or a persistent emergency-stop reminder during auto click. */
final class DesktopStatusView implements AutoCloseable {
    private final Stage owner;
    private final Pet pet;
    private final Rectangle2D screen;
    private final Popup popup = new Popup();
    private final Label message = new Label();
    private final PauseTransition hide = new PauseTransition(Duration.seconds(3));

    DesktopStatusView(Stage owner, Pet pet, Rectangle2D screen) {
        this.owner = owner; this.pet = pet; this.screen = screen;
        message.setWrapText(true); message.setMaxWidth(280);
        message.setMouseTransparent(true);
        message.setStyle("-fx-background-color: #203148; -fx-text-fill: #EAF3FF; -fx-border-color: #6685A7; "
                + "-fx-background-radius: 7; -fx-border-radius: 7; -fx-padding: 8 11; -fx-font-family: 'Segoe UI'; -fx-font-size: 12;");
        popup.getContent().add(message); popup.setAutoFix(false);
        hide.setOnFinished(event -> popup.hide());
    }

    void show(String text, boolean autoClickActive, String stopKey) {
        if (!owner.isShowing() || text == null || text.isBlank()) return;
        hide.stop();
        message.setText(autoClickActive ? "● 오토 클릭 · " + stopKey + " 긴급 중지\n" + text : text);
        if (!popup.isShowing()) popup.show(owner, pet.getX(), pet.getY());
        reposition();
        if (!autoClickActive) hide.playFromStart();
    }

    void reposition() {
        if (!popup.isShowing()) return;
        double width = Math.max(100, popup.getWidth()), height = Math.max(34, popup.getHeight());
        double x = Math.max(screen.getMinX(), Math.min(pet.getX() + pet.getSize() / 2 - width / 2, screen.getMaxX() - width));
        double y = pet.getY() - height - 10;
        if (y < screen.getMinY()) y = pet.getY() + pet.getSize() + 10;
        popup.setX(x); popup.setY(Math.max(screen.getMinY(), Math.min(y, screen.getMaxY() - height)));
    }

    boolean isShowing() { return popup.isShowing(); }

    @Override public void close() { hide.stop(); popup.hide(); }
}
