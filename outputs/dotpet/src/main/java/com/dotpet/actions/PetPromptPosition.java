package com.dotpet.actions;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Places a compact prompt above its pet, or below when the top edge has no room. */
final class PetPromptPosition {
    private PetPromptPosition() { }

    static void place(Stage prompt, Stage pet) {
        var screens = Screen.getScreensForRectangle(pet.getX(), pet.getY(), pet.getWidth(), pet.getHeight());
        Rectangle2D screen = (screens.isEmpty() ? Screen.getPrimary() : screens.getFirst()).getVisualBounds();
        double x = pet.getX() + (pet.getWidth() - prompt.getWidth()) / 2;
        double y = pet.getY() - prompt.getHeight() - 10;
        if (y < screen.getMinY()) y = pet.getY() + pet.getHeight() + 10;
        prompt.setX(Math.max(screen.getMinX(), Math.min(x, screen.getMaxX() - prompt.getWidth())));
        prompt.setY(Math.max(screen.getMinY(), Math.min(y, screen.getMaxY() - prompt.getHeight())));
    }
}
