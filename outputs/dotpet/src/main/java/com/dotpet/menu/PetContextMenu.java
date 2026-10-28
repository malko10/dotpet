package com.dotpet.menu;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.stage.Popup;
import javafx.stage.Stage;

/** Clear action names and live state hints on the same adaptive arc as the main menu. */
public final class PetContextMenu {
    private final Popup popup = new Popup();
    private final BooleanSupplier movementEnabled;
    private final BooleanSupplier gravityRemoved;
    private final Runnable toggleMovement;
    private final Runnable toggleGravity;
    private final Runnable exitAction;
    private PetMenuFan fan;

    public PetContextMenu(BooleanSupplier movementEnabled, BooleanSupplier gravityRemoved,
            Runnable toggleMovement, Runnable toggleGravity, Runnable exitAction,
            Consumer<Boolean> visibilityChanged) {
        this.movementEnabled = movementEnabled;
        this.gravityRemoved = gravityRemoved;
        this.toggleMovement = toggleMovement;
        this.toggleGravity = toggleGravity;
        this.exitAction = exitAction;
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setAutoFix(false);
        popup.setOnShown(event -> visibilityChanged.accept(true));
        popup.setOnHidden(event -> { if (fan != null) fan.stop(); visibilityChanged.accept(false); });
    }

    public boolean isShowing() { return popup.isShowing(); }

    public void show(Stage owner, double petX, double petY, double petSize, Rectangle2D screen) {
        if (popup.isShowing()) return;
        PetMenuLayout.Layout layout = PetMenuLayout.create(petX, petY, petSize, screen, 3);
        Pane root = new Pane();
        root.setPrefSize(layout.bounds().getWidth(), layout.bounds().getHeight());
        root.setStyle("-fx-background-color: transparent;");
        root.setOnMouseClicked(event -> { if (event.getTarget() == root) popup.hide(); });

        boolean moving = movementEnabled.getAsBoolean();
        boolean fixed = gravityRemoved.getAsBoolean();
        addButton(root, layout, 0, "menu-movement",
                moving ? PetMenuButton.Icon.PAUSE : PetMenuButton.Icon.WALK,
                moving ? "멈추기" : "걷게 하기", moving ? "자동 이동 켜짐" : "자동 이동 꺼짐", toggleMovement);
        addButton(root, layout, 1, "menu-gravity",
                fixed ? PetMenuButton.Icon.GRAVITY : PetMenuButton.Icon.PIN,
                fixed ? "중력 켜기" : "공중 고정", fixed ? "공중 고정 중" : "중력 사용 중", toggleGravity);
        addButton(root, layout, 2, "menu-exit", PetMenuButton.Icon.EXIT, "종료", "DoT Pet 닫기", exitAction);
        fan = new PetMenuFan(root, new Point2D(petX + petSize / 2 - layout.bounds().getMinX(),
                petY + petSize / 2 - layout.bounds().getMinY()));
        popup.getContent().setAll(root);
        popup.show(owner, layout.bounds().getMinX(), layout.bounds().getMinY());
        fan.play();
    }

    public void close() { if (popup.isShowing()) popup.hide(); }

    private void addButton(Pane root, PetMenuLayout.Layout layout, int index, String id,
            PetMenuButton.Icon icon, String title, String hint, Runnable action) {
        Button button = PetMenuButton.create(icon, title, hint, () -> { popup.hide(); action.run(); });
        button.setId(id);
        button.setLayoutX(layout.centers().get(index).getX() - layout.bounds().getMinX() - PetMenuLayout.BUTTON_WIDTH / 2);
        button.setLayoutY(layout.centers().get(index).getY() - layout.bounds().getMinY() - PetMenuLayout.BUTTON_HEIGHT / 2);
        root.getChildren().add(button);
    }
}
