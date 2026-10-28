package com.dotpet.menu;

import java.util.function.Consumer;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.stage.Popup;
import javafx.stage.Stage;

/** Four function buttons on a clear semicircle centered on the pet. */
public final class RadialMenu {

    private final Popup popup = new Popup();
    private final Consumer<Boolean> visibilityChanged;
    private final Consumer<RadialMenuAction> selectionHandler;
    private PetMenuFan fan;

    public RadialMenu(Consumer<Boolean> visibilityChanged, Consumer<RadialMenuAction> selectionHandler) {
        this.visibilityChanged = visibilityChanged;
        this.selectionHandler = selectionHandler;
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setAutoFix(false);
        popup.setOnShown(event -> visibilityChanged.accept(true));
        popup.setOnHidden(event -> { if (fan != null) fan.stop(); visibilityChanged.accept(false); });
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    public void toggle(Stage owner, double petX, double petY, double petSize, Rectangle2D screenBounds) {
        if (popup.isShowing()) {
            popup.hide();
            return;
        }

        PetMenuLayout.Layout layout = PetMenuLayout.create(petX, petY, petSize, screenBounds);
        Pane content = createContent(layout);
        fan = new PetMenuFan(content, new Point2D(petX + petSize / 2 - layout.bounds().getMinX(),
                petY + petSize / 2 - layout.bounds().getMinY()));
        popup.getContent().setAll(content);
        popup.show(owner, layout.bounds().getMinX(), layout.bounds().getMinY());
        fan.play();
    }

    public void close() {
        if (popup.isShowing()) {
            popup.hide();
        }
    }

    private Pane createContent(PetMenuLayout.Layout layout) {
        Pane root = new Pane();
        root.setPrefSize(layout.bounds().getWidth(), layout.bounds().getHeight());
        root.setStyle("-fx-background-color: transparent;");

        root.setOnMouseClicked(event -> {
            if (event.getTarget() == root) popup.hide();
        });

        RadialMenuAction[] actions = RadialMenuAction.values();
        for (int index = 0; index < actions.length; index++) {
            addButton(root, actions[index], layout.centers().get(index).getX() - layout.bounds().getMinX(),
                    layout.centers().get(index).getY() - layout.bounds().getMinY());
        }
        return root;
    }

    private void addButton(Pane root, RadialMenuAction action, double centerX, double centerY) {
        PetMenuButton.Icon icon = switch (action) {
            case QUICK_ACTIONS -> PetMenuButton.Icon.QUICK;
            case INPUT_SETTINGS -> PetMenuButton.Icon.KEYS;
            case PET_CUSTOMIZATION -> PetMenuButton.Icon.PALETTE;
            case PROGRAM_SETTINGS -> PetMenuButton.Icon.SETTINGS;
        };
        String title = switch (action) {
            case PET_CUSTOMIZATION -> "꾸미기";
            case PROGRAM_SETTINGS -> "설정";
            default -> action.getLabel();
        };
        String hint = switch (action) {
            case QUICK_ACTIONS -> "복사·자동 클릭";
            case INPUT_SETTINGS -> "단축키·제스처";
            case PET_CUSTOMIZATION -> "그림·외형";
            case PROGRAM_SETTINGS -> "프로그램";
        };
        Button button = PetMenuButton.create(icon, title, hint, () -> {
            popup.hide();
            selectionHandler.accept(action);
        });
        button.setId("menu-" + action.name().toLowerCase(java.util.Locale.ROOT));
        button.setLayoutX(centerX - PetMenuLayout.BUTTON_WIDTH / 2);
        button.setLayoutY(centerY - PetMenuLayout.BUTTON_HEIGHT / 2);
        root.getChildren().add(button);
    }

}
