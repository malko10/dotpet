package com.dotpet.app;

import com.dotpet.pet.Pet;
import com.dotpet.pet.PetMovementController;
import com.dotpet.pet.PetRenderer;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Starts the transparent desktop pet window. */
public final class DotPetApplication extends Application {
    private static final double PET_WINDOW_SIZE = com.dotpet.pet.PixelSizing.BASE_SIZE;

    @Override
    public void start(Stage stage) {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double startX = screenBounds.getMinX() + Math.max(0, screenBounds.getWidth() - PET_WINDOW_SIZE - 56);
        double startY = Math.max(screenBounds.getMinY(), screenBounds.getMaxY() - PET_WINDOW_SIZE);

        Pet pet = new Pet(startX, startY, PET_WINDOW_SIZE);
        Canvas canvas = new Canvas(PET_WINDOW_SIZE, PET_WINDOW_SIZE);
        StackPane root = new StackPane(canvas);
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, PET_WINDOW_SIZE, PET_WINDOW_SIZE, Color.TRANSPARENT);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("DoT Pet");
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
        stage.setScene(scene);
        stage.setX(startX);
        stage.setY(startY);

        PetRenderer renderer = new PetRenderer(canvas);
        PetMovementController movement = new PetMovementController(pet, screenBounds);
        DesktopPetController controller = new DesktopPetController(stage, canvas, pet, movement, renderer);

        stage.setOnHidden(event -> controller.stop());
        controller.start();
    }
}
