package com.dotpet.editor;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** Small drawn tool icons, independent of installed symbol fonts. */
final class EditorIcons {
    enum Tool { PENCIL, ERASER, UNDO, REDO, COPY, PASTE, CLEAR, ADD, DELETE, LEFT, RIGHT, PLAY, STOP }

    private EditorIcons() { }

    static Canvas create(Tool tool) {
        Canvas canvas = new Canvas(24, 24);
        canvas.setMouseTransparent(true);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setStroke(Color.web("#E6EDF7"));
        g.setFill(Color.web("#E6EDF7"));
        g.setLineWidth(1.8);
        switch (tool) {
            case PENCIL -> {
                g.setFill(Color.web("#F5C96A"));
                g.fillPolygon(new double[] {6, 15, 20, 11}, new double[] {15, 6, 11, 20}, 4);
                g.setFill(Color.web("#9AE66E"));
                g.fillPolygon(new double[] {15, 18, 23, 20}, new double[] {6, 3, 8, 11}, 4);
                g.setFill(Color.web("#E6EDF7"));
                g.fillPolygon(new double[] {6, 11, 3}, new double[] {15, 20, 23}, 3);
                g.setFill(Color.web("#26384C")); g.fillRect(3, 20, 3, 3);
                g.strokeLine(8, 16, 17, 7);
            }
            case ERASER -> {
                g.setFill(Color.web("#EFA2B8"));
                g.fillPolygon(new double[] {3, 13, 22, 12}, new double[] {14, 3, 11, 22}, 4);
                g.setFill(Color.web("#C8BEF4"));
                g.fillPolygon(new double[] {3, 8, 17, 12}, new double[] {14, 9, 17, 22}, 4);
                g.strokeLine(8, 9, 17, 17); g.strokeLine(3, 22, 22, 22);
            }
            case UNDO, REDO -> {
                if (tool == Tool.REDO) { g.translate(24, 0); g.scale(-1, 1); }
                g.beginPath(); g.moveTo(5, 8); g.bezierCurveTo(20, 4, 24, 18, 11, 20); g.stroke();
                g.strokePolyline(new double[] {9, 4, 8}, new double[] {3, 8, 13}, 3);
            }
            case COPY -> { g.strokeRect(8, 3, 12, 14); g.strokeRect(3, 8, 12, 14); }
            case PASTE -> {
                g.strokeRoundRect(5, 5, 15, 17, 3, 3); g.setFill(Color.web("#9AE66E"));
                g.fillRoundRect(9, 2, 7, 6, 2, 2); g.strokeLine(9, 13, 16, 13); g.strokeLine(9, 17, 16, 17);
            }
            case CLEAR, DELETE -> {
                g.strokeLine(4, 6, 20, 6); g.strokeRect(9, 2, 6, 4);
                g.strokePolyline(new double[] {6, 7, 17, 18}, new double[] {7, 22, 22, 7}, 4);
                g.strokeLine(10, 10, 10, 18); g.strokeLine(14, 10, 14, 18);
            }
            case ADD -> { g.strokeLine(12, 4, 12, 20); g.strokeLine(4, 12, 20, 12); }
            case LEFT -> { g.strokePolyline(new double[] {15, 7, 15}, new double[] {4, 12, 20}, 3); }
            case RIGHT -> { g.strokePolyline(new double[] {9, 17, 9}, new double[] {4, 12, 20}, 3); }
            case PLAY -> { g.setFill(Color.web("#9AE66E")); g.fillPolygon(new double[] {7, 20, 7}, new double[] {3, 12, 21}, 3); }
            case STOP -> { g.fillRoundRect(5, 5, 14, 14, 2, 2); }
        }
        return canvas;
    }
}
