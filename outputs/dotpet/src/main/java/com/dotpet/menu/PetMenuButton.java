package com.dotpet.menu;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** A readable icon card with a visible action name and a short explanation. */
final class PetMenuButton {
    enum Icon { QUICK, KEYS, PALETTE, SETTINGS, WALK, PAUSE, GRAVITY, PIN, EXIT }

    private PetMenuButton() { }

    static Button create(Icon icon, String title, String hint, Runnable action) {
        Label name = new Label(title);
        name.setStyle("-fx-text-fill: #F3F6FC; -fx-font-family: 'Segoe UI'; -fx-font-size: 12px; -fx-font-weight: bold;");
        Label subtitle = new Label(hint);
        subtitle.setStyle("-fx-text-fill: #B4C7DE; -fx-font-family: 'Segoe UI'; -fx-font-size: 10px;");
        VBox content = new VBox(2, graphic(icon), name, subtitle);
        content.setAlignment(Pos.CENTER);
        content.setMouseTransparent(true);
        Button button = new Button("", content);
        button.setAccessibleText(title + " · " + hint);
        button.setTooltip(new Tooltip(title + " · " + hint));
        button.setFocusTraversable(false);
        button.setMinSize(PetMenuLayout.BUTTON_WIDTH, PetMenuLayout.BUTTON_HEIGHT);
        button.setPrefSize(PetMenuLayout.BUTTON_WIDTH, PetMenuLayout.BUTTON_HEIGHT);
        button.setMaxSize(PetMenuLayout.BUTTON_WIDTH, PetMenuLayout.BUTTON_HEIGHT);
        String border = icon == Icon.EXIT ? "#B76B7A" : "#597493";
        String style = "-fx-background-color: #202F43; -fx-background-radius: 14; -fx-border-radius: 14; "
                + "-fx-border-width: 1.2; -fx-border-color: " + border + "; -fx-padding: 6; -fx-cursor: hand;";
        button.setStyle(style);
        button.setOnMouseEntered(event -> button.setStyle(style.replace("#202F43", "#354D69")));
        button.setOnMouseExited(event -> button.setStyle(style));
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Canvas graphic(Icon icon) {
        Canvas canvas = new Canvas(28, 28);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setLineWidth(2.2);
        g.setStroke(Color.web("#BCD8F4"));
        g.setFill(Color.web("#9AE66E"));
        switch (icon) {
            case QUICK -> {
                g.setFill(Color.web("#FFD16F"));
                g.fillPolygon(new double[] {16, 5, 13, 10, 24, 16}, new double[] {1, 16, 16, 27, 10, 10}, 6);
            }
            case KEYS -> {
                g.setFill(Color.web("#7DBCE9")); g.fillRoundRect(1, 6, 26, 18, 5, 5);
                g.setFill(Color.web("#23374C"));
                for (int row = 0; row < 2; row++) for (int col = 0; col < 5; col++) g.fillRect(4 + col * 4.5, 9 + row * 4.5, 2.5, 2.5);
                g.fillRoundRect(7, 19, 14, 2, 1, 1);
            }
            case PALETTE -> {
                g.setFill(Color.web("#E8B28C")); g.fillOval(1, 2, 26, 24);
                Color[] colors = {Color.web("#F284A1"), Color.web("#FFD16F"), Color.web("#9AE66E"), Color.web("#77BDEF")};
                for (int i = 0; i < colors.length; i++) {
                    double angle = Math.toRadians(160 + i * 48);
                    g.setFill(colors[i]); g.fillOval(11 + Math.cos(angle) * 8, 10 + Math.sin(angle) * 8, 5, 5);
                }
                g.setFill(Color.web("#202F43")); g.fillOval(17, 16, 7, 7);
            }
            case SETTINGS -> {
                g.setStroke(Color.web("#BED0E8")); g.setLineWidth(4);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    g.strokeLine(14 + Math.cos(angle) * 8, 14 + Math.sin(angle) * 8,
                            14 + Math.cos(angle) * 12, 14 + Math.sin(angle) * 12);
                }
                g.strokeOval(6, 6, 16, 16); g.setLineWidth(2); g.strokeOval(11, 11, 6, 6);
            }
            case WALK -> {
                g.save(); g.translate(8, 10); g.rotate(-20); g.fillOval(-3, -6, 7, 12); g.fillOval(-2, 8, 6, 5); g.restore();
                g.save(); g.translate(20, 15); g.rotate(20); g.fillOval(-3, -6, 7, 12); g.fillOval(-2, 8, 6, 5); g.restore();
            }
            case PAUSE -> { g.fillRoundRect(5, 3, 6, 22, 2, 2); g.fillRoundRect(17, 3, 6, 22, 2, 2); }
            case GRAVITY -> {
                g.setFill(Color.web("#80C6F0"));
                g.fillRect(11, 2, 6, 13);
                g.fillPolygon(new double[] {4, 24, 14}, new double[] {13, 13, 24}, 3);
                g.setStroke(Color.web("#FFD16F")); g.strokeLine(2, 27, 26, 27);
            }
            case PIN -> {
                g.setFill(Color.web("#B7A3F3")); g.fillRoundRect(8, 2, 12, 5, 2, 2); g.fillRect(10, 6, 8, 8);
                g.fillPolygon(new double[] {5, 23, 14}, new double[] {17, 17, 10}, 3);
                g.setStroke(Color.web("#E9DFF9")); g.strokeLine(14, 16, 14, 27);
            }
            case EXIT -> {
                g.setStroke(Color.web("#FA9CA9")); g.setLineWidth(3);
                g.strokeArc(4, 4, 20, 21, 135, 270, javafx.scene.shape.ArcType.OPEN);
                g.strokeLine(14, 1, 14, 14);
            }
        }
        return canvas;
    }
}
