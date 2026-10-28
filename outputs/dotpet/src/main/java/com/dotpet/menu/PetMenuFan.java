package com.dotpet.menu;

import java.util.List;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

/** Opens readable, upright buttons along a pet-centered arc without crossing the sprite. */
final class PetMenuFan {
    private record Card(Button button, double radius, double angle) { }
    private final DoubleProperty progress = new SimpleDoubleProperty();
    private final Timeline timeline;
    private final List<Card> cards;
    private final Point2D origin;
    private final double heading;

    PetMenuFan(Pane root, Point2D origin) {
        this.origin = origin;
        cards = root.getChildren().stream().map(node -> (Button) node).map(button -> {
            double x = button.getLayoutX() + PetMenuLayout.BUTTON_WIDTH / 2 - origin.getX();
            double y = button.getLayoutY() + PetMenuLayout.BUTTON_HEIGHT / 2 - origin.getY();
            return new Card(button, Math.hypot(x, y), Math.atan2(y, x));
        }).toList();
        heading = Math.atan2(Math.sin(cards.getFirst().angle()) + Math.sin(cards.getLast().angle()),
                Math.cos(cards.getFirst().angle()) + Math.cos(cards.getLast().angle()));
        progress.addListener((observable, before, after) -> position(after.doubleValue()));
        timeline = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(Duration.millis(220), new KeyValue(progress, 1, Interpolator.EASE_OUT)));
        timeline.setOnFinished(event -> cards.forEach(card -> card.button().setMouseTransparent(false)));
        position(0);
    }

    void play() {
        cards.forEach(card -> card.button().setMouseTransparent(true));
        timeline.playFromStart();
    }

    void stop() {
        timeline.stop();
        progress.set(1);
        cards.forEach(card -> card.button().setMouseTransparent(false));
    }

    private void position(double amount) {
        for (Card card : cards) {
            double delta = Math.atan2(Math.sin(card.angle() - heading), Math.cos(card.angle() - heading));
            double angle = heading + delta * (0.35 + 0.65 * amount);
            card.button().relocate(origin.getX() + card.radius() * Math.cos(angle) - PetMenuLayout.BUTTON_WIDTH / 2,
                    origin.getY() + card.radius() * Math.sin(angle) - PetMenuLayout.BUTTON_HEIGHT / 2);
            card.button().setScaleX(0.86 + 0.14 * amount);
            card.button().setScaleY(0.86 + 0.14 * amount);
            card.button().setOpacity(amount);
        }
    }
}
