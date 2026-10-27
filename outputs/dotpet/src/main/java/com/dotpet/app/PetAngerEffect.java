package com.dotpet.app;

import com.dotpet.pet.Pet;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Popup;
import javafx.stage.Stage;

/** An original four-stroke red annoyance mark above the current sprite's visible head. */
final class PetAngerEffect implements AutoCloseable {
    private final Stage owner;
    private final Pet pet;
    private final Rectangle2D screen;
    private final Popup popup = new Popup();
    private final Canvas mark = new Canvas();

    PetAngerEffect(Stage owner, Pet pet, Rectangle2D screen) {
        this.owner = owner; this.pet = pet; this.screen = screen;
        mark.setId("pet-anger-effect"); mark.setMouseTransparent(true);
        popup.getContent().add(mark); popup.setAutoFix(false);
    }

    void update(boolean visible, Rectangle2D sprite) {
        if (!visible || !owner.isShowing() || sprite.getWidth() == 0 || sprite.getHeight() == 0) { hide(); return; }
        double size = Math.max(22, Math.min(42, pet.getSize() * 0.28));
        if (mark.getWidth() != size) draw(size);
        double anchorX = pet.getX() + sprite.getMinX() + sprite.getWidth() * (pet.isFacingRight() ? 0.7 : 0.3);
        double x = Math.max(screen.getMinX(), Math.min(anchorX - size / 2, screen.getMaxX() - size));
        double y = pet.getY() + sprite.getMinY() - size - 5;
        if (y < screen.getMinY()) { hide(); return; }
        if (!popup.isShowing()) popup.show(owner, x, y);
        popup.setX(x); popup.setY(y);
    }

    private void draw(double size) {
        mark.setWidth(size); mark.setHeight(size);
        var graphics = mark.getGraphicsContext2D();
        graphics.clearRect(0, 0, size, size);
        graphics.scale(size / 40, size / 40);
        graphics.setStroke(Color.web("#DF695A"));
        graphics.setLineWidth(4.8); graphics.setLineCap(StrokeLineCap.ROUND);
        graphics.beginPath();
        graphics.moveTo(16, 4); graphics.quadraticCurveTo(16, 16, 4, 16);
        graphics.moveTo(24, 4); graphics.quadraticCurveTo(24, 16, 36, 16);
        graphics.moveTo(4, 24); graphics.quadraticCurveTo(16, 24, 16, 36);
        graphics.moveTo(36, 24); graphics.quadraticCurveTo(24, 24, 24, 36);
        graphics.stroke();
        graphics.setTransform(1, 0, 0, 1, 0, 0);
    }

    void hide() { popup.hide(); }
    @Override public void close() { hide(); }
}
