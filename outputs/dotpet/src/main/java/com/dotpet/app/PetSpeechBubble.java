package com.dotpet.app;

import com.dotpet.pet.Pet;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.StrokeType;
import javafx.stage.Popup;
import javafx.stage.Stage;

/** One continuous cream outline and a short tail; the conversation itself is the only text. */
final class PetSpeechBubble implements AutoCloseable {
    private static final double WIDTH = 240, TAIL = 8, RADIUS = 10;
    private final Stage owner;
    private final Pet pet;
    private final Rectangle2D screen;
    private final Popup popup = new Popup();
    private final Label line = new Label();
    private final Path outline = new Path();
    private final StackPane card = new StackPane(line);
    private final Pane content = new Pane(outline, card);
    private String currentText;
    private double bodyHeight;
    private double drawnTail = Double.NaN;
    private boolean above;

    PetSpeechBubble(Stage owner, Pet pet, Rectangle2D screen) {
        this.owner = owner; this.pet = pet; this.screen = screen;
        line.setId("pet-speech-text"); line.setWrapText(true); line.setPrefWidth(WIDTH - 28);
        line.setStyle("-fx-text-fill: #594039; -fx-font-size: 13px; -fx-font-family: 'Malgun Gothic';");
        card.setId("pet-speech-card"); card.setPadding(new Insets(11, 14, 11, 14));
        card.setManaged(false); outline.setManaged(false);
        outline.setFill(Color.web("#FFF5D9")); outline.setStroke(Color.web("#D8BB89"));
        outline.setStrokeWidth(1.2); outline.setStrokeType(StrokeType.INSIDE);
        content.setId("pet-speech-bubble");
        content.setOnMousePressed(event -> { popup.hide(); event.consume(); });
        popup.getContent().add(content); popup.setAutoFix(false);
    }

    void update(String text) {
        if (!Objects.equals(text, currentText)) {
            currentText = text;
            if (text == null || !owner.isShowing()) popup.hide();
            else {
                line.setText(text);
                if (!popup.isShowing()) popup.show(owner, pet.getX(), pet.getY());
                content.applyCss();
                bodyHeight = Math.ceil(line.prefHeight(WIDTH - 28)) + 22;
                card.resize(WIDTH, bodyHeight); card.layout();
                content.setPrefSize(WIDTH, bodyHeight + TAIL); content.autosize();
                drawnTail = Double.NaN;
            }
        }
        reposition();
    }

    void hide() { currentText = null; popup.hide(); }
    boolean isShowing() { return popup.isShowing(); }

    private void reposition() {
        if (!popup.isShowing()) return;
        double height = bodyHeight + TAIL;
        double x = Math.max(screen.getMinX(), Math.min(pet.getX() + pet.getSize() / 2 - WIDTH / 2, screen.getMaxX() - WIDTH));
        double y = pet.getY() - height - 10;
        boolean placeAbove = y >= screen.getMinY();
        if (!placeAbove) y = pet.getY() + pet.getSize() + 10;
        if (WIDTH > screen.getWidth() || y + height > screen.getMaxY()) { popup.hide(); return; }
        double tailX = Math.max(22, Math.min(pet.getX() + pet.getSize() / 2 - x, WIDTH - 22));
        if (placeAbove != above || tailX != drawnTail) drawOutline(placeAbove, tailX);
        popup.setX(x); popup.setY(y);
    }

    private void drawOutline(boolean placeAbove, double tailX) {
        above = placeAbove; drawnTail = tailX;
        double top = above ? 0 : TAIL, bottom = top + bodyHeight;
        var elements = outline.getElements(); elements.clear();
        elements.add(new MoveTo(RADIUS, top));
        if (!above) elements.addAll(new LineTo(tailX - 7, top), new LineTo(tailX, 0), new LineTo(tailX + 7, top));
        elements.addAll(new LineTo(WIDTH - RADIUS, top), new QuadCurveTo(WIDTH, top, WIDTH, top + RADIUS),
                new LineTo(WIDTH, bottom - RADIUS), new QuadCurveTo(WIDTH, bottom, WIDTH - RADIUS, bottom));
        if (above) elements.addAll(new LineTo(tailX + 7, bottom), new LineTo(tailX, bottom + TAIL), new LineTo(tailX - 7, bottom));
        elements.addAll(new LineTo(RADIUS, bottom), new QuadCurveTo(0, bottom, 0, bottom - RADIUS),
                new LineTo(0, top + RADIUS), new QuadCurveTo(0, top, RADIUS, top), new ClosePath());
        card.relocate(0, top);
    }

    @Override public void close() { hide(); }
}
