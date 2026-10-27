package com.dotpet.app;

import com.dotpet.pet.Pet;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.stage.Popup;
import javafx.stage.Stage;

/** Warm cream chatter is visually separate from the blue menus and operation notices. */
final class PetSpeechBubble implements AutoCloseable {
    private final Stage owner;
    private final Pet pet;
    private final Rectangle2D screen;
    private final Popup popup = new Popup();
    private final Label line = new Label();
    private final VBox card;
    private final Polygon tail = new Polygon(0, 0, 14, 0, 7, 9);
    private final VBox content;
    private String currentText;
    private boolean above = true;

    PetSpeechBubble(Stage owner, Pet pet, Rectangle2D screen) {
        this.owner = owner; this.pet = pet; this.screen = screen;
        Label name = new Label("도트의 혼잣말");
        name.setStyle("-fx-text-fill: #936438; -fx-font-size: 10px; -fx-font-weight: bold;");
        line.setId("pet-speech-text");
        line.setWrapText(true); line.setPrefWidth(216);
        line.setStyle("-fx-text-fill: #594039; -fx-font-size: 13px; -fx-font-family: 'Malgun Gothic';");
        card = new VBox(5, name, line);
        card.setId("pet-speech-card");
        card.setStyle("-fx-background-color: #FFF3CB; -fx-border-color: #D6A15D; -fx-border-width: 2; "
                + "-fx-background-radius: 7; -fx-border-radius: 7; -fx-padding: 10;");
        tail.setFill(Color.web("#FFF3CB")); tail.setStroke(Color.web("#D6A15D"));
        content = new VBox(card, tail); content.setAlignment(Pos.CENTER);
        content.setOnMousePressed(event -> { popup.hide(); event.consume(); });
        popup.getContent().add(content); popup.setAutoFix(false);
    }

    void update(String text) {
        if (!Objects.equals(text, currentText)) {
            currentText = text;
            if (text == null || !owner.isShowing()) popup.hide();
            else {
                line.setText(text);
                content.applyCss(); content.autosize();
                if (!popup.isShowing()) popup.show(owner, pet.getX(), pet.getY());
            }
        }
        reposition();
    }

    void hide() { currentText = null; popup.hide(); }
    boolean isShowing() { return popup.isShowing(); }

    private void reposition() {
        if (!popup.isShowing()) return;
        double width = popup.getWidth(), height = popup.getHeight();
        double x = Math.max(screen.getMinX(), Math.min(pet.getX() + pet.getSize() / 2 - width / 2, screen.getMaxX() - width));
        double y = pet.getY() - height - 10;
        boolean placeAbove = y >= screen.getMinY();
        if (!placeAbove) y = pet.getY() + pet.getSize() + 10;
        if (width > screen.getWidth() || y + height > screen.getMaxY()) { popup.hide(); return; }
        if (above != placeAbove) {
            above = placeAbove;
            tail.setRotate(above ? 0 : 180);
            content.getChildren().setAll(above ? card : tail, above ? tail : card);
        }
        tail.setTranslateX(Math.max(-width / 2 + 20, Math.min(pet.getX() + pet.getSize() / 2 - x - width / 2, width / 2 - 20)));
        popup.setX(x); popup.setY(y);
    }

    @Override public void close() { hide(); }
}
