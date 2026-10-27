package com.dotpet.app;

import com.dotpet.menu.RadialMenu;
import com.dotpet.menu.RadialMenuAction;
import com.dotpet.menu.PetContextMenu;
import com.dotpet.editor.PixelEditorWindow;
import com.dotpet.customization.PetCustomization;
import com.dotpet.customization.PetCustomizationWindow;
import com.dotpet.pet.Pet;
import com.dotpet.pet.PetMovementController;
import com.dotpet.pet.PetRenderer;
import com.dotpet.pet.PetSpeechController;
import com.dotpet.pet.PetState;
import com.dotpet.actions.QuickActionsWindow;
import com.dotpet.actions.QuickActionService;
import com.dotpet.input.InputAction;
import com.dotpet.input.InputSettingsWindow;
import com.dotpet.input.WindowsInputService;
import com.dotpet.settings.InputSettings;
import com.dotpet.settings.SettingsService;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.Cursor;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/** Connects JavaFX input, menus, and window placement to the pet model. */
final class DesktopPetController {
    private static final double DRAG_THRESHOLD = 4;
    private static final long DRAG_SAMPLE_WINDOW_NANOS = 140_000_000L;
    private static final long CLICK_SUPPRESSION_NANOS = 240_000_000L;
    private static final double DEFAULT_PET_SIZE = 112;

    private final Stage stage;
    private final Canvas canvas;
    private final Pet pet;
    private final PetMovementController movement;
    private final PetRenderer renderer;
    private final AnimationTimer timer;
    private final RadialMenu radialMenu;
    private final PetContextMenu contextMenu;
    private final SettingsService settingsService = new SettingsService();
    private final ExecutorService settingsExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "dotpet-settings"); thread.setDaemon(true); return thread;
    });
    private final WindowsInputService inputService;
    private final QuickActionService quickActions;
    private final DesktopStatusView statusView;
    private final PetSpeechController speech = new PetSpeechController(System.nanoTime());
    private final PetSpeechBubble speechBubble;
    private volatile InputSettings inputSettings;
    private volatile String inputNotice = "";
    private Stage featureStage;
    private boolean closing;
    private final ArrayDeque<DragSample> dragSamples = new ArrayDeque<>();
    private boolean primaryPressed;
    private boolean dragging;
    private double pressScreenX;
    private double pressScreenY;
    private double pressWindowX;
    private double pressWindowY;
    private long pressTimeNanos;
    private long dragReleaseTimeNanos;

    DesktopPetController(
            Stage stage,
            Canvas canvas,
            Pet pet,
            PetMovementController movement,
            PetRenderer renderer) {
        this.stage = stage;
        this.canvas = canvas;
        this.pet = pet;
        this.movement = movement;
        this.renderer = renderer;
        this.inputSettings = settingsService.loadInputSettings();
        this.inputNotice = settingsService.getLoadNotice();
        this.inputService = new WindowsInputService(settingsService.getDirectory(), this::handleGlobalAction, this::reportInputFailure);
        this.quickActions = new QuickActionService(inputService, () -> inputSettings, this::applyInputSettings);
        this.statusView = new DesktopStatusView(stage, pet, movement.getScreenBounds());
        this.speechBubble = new PetSpeechBubble(stage, pet, movement.getScreenBounds());
        quickActions.statusProperty().addListener((observable, previous, value) -> showActionStatus());
        quickActions.autoActiveProperty().addListener((observable, previous, value) -> showActionStatus());
        this.timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                movement.update(now);
                placeWindowAtPetPosition();
                renderer.render(pet, now);
                statusView.reposition();
                updateSpeech(now);
            }
        };
        this.radialMenu = new RadialMenu(this::updateMenuPause, this::openFeatureWindow);
        this.contextMenu = new PetContextMenu(
                pet::isMovementEnabled,
                movement::isGravityRemoved,
                () -> movement.setMovementEnabled(!pet.isMovementEnabled()),
                () -> movement.setGravityRemoved(!movement.isGravityRemoved()),
                stage::close,
                this::updateMenuPause);

        canvas.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            showContextMenu();
            event.consume();
        });

        canvas.setCursor(Cursor.OPEN_HAND);
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> handleMousePressed(event));
        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> handleMouseDragged(event));
        canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> handleMouseReleased(event));
        canvas.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> handleMouseClicked(event));

        Scene scene = stage.getScene();
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                if (radialMenu.isShowing()) {
                    radialMenu.close();
                } else if (contextMenu.isShowing()) {
                    contextMenu.close();
                } else {
                    stage.close();
                }
            } else if (event.getCode() == KeyCode.SPACE) {
                setMovementEnabled(!pet.isMovementEnabled());
            }
        });

        stage.setOnCloseRequest(event -> stop());
    }

    void start() {
        renderer.render(pet, System.nanoTime());
        timer.start();
        inputService.configure(inputSettings).whenComplete((ignored, failure) -> {
            if (failure != null) reportInputFailure("전역 입력 시작 실패: " + failureMessage(failure));
            else if (!inputNotice.isBlank()) quickActions.report(inputNotice);
            else quickActions.report(inputSettings.hotkeysEnabled() || inputSettings.gesturesEnabled()
                    ? "설정한 전역 입력을 사용할 수 있어요." : "전역 입력은 꺼져 있어요. 키 설정에서 켤 수 있습니다.");
        });
    }

    void stop() {
        if (closing) return; closing = true;
        radialMenu.close();
        contextMenu.close();
        timer.stop();
        quickActions.close();
        inputService.close();
        settingsExecutor.shutdownNow();
        statusView.close();
        speechBubble.close();
    }

    private void handleMousePressed(MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        primaryPressed = true;
        speechBubble.hide();
        dragging = false;
        pressScreenX = event.getScreenX();
        pressScreenY = event.getScreenY();
        pressWindowX = stage.getX();
        pressWindowY = stage.getY();
        pressTimeNanos = System.nanoTime();
        dragSamples.clear();
        recordDragSample(pressScreenX, pressScreenY, pressTimeNanos);
        canvas.setCursor(Cursor.CLOSED_HAND);
    }

    private void handleMouseDragged(MouseEvent event) {
        if (!primaryPressed || !event.isPrimaryButtonDown()) {
            return;
        }

        double dx = event.getScreenX() - pressScreenX;
        double dy = event.getScreenY() - pressScreenY;
        if (!dragging && Math.hypot(dx, dy) >= DRAG_THRESHOLD) {
            dragging = true;
            inputService.setPetDragging(true);
            radialMenu.close();
            contextMenu.close();
            movement.beginDrag();
            dragSamples.clear();
            recordDragSample(pressScreenX, pressScreenY, pressTimeNanos);
        }
        if (dragging) {
            long now = System.nanoTime();
            recordDragSample(event.getScreenX(), event.getScreenY(), now);
            Rectangle2D bounds = movement.getScreenBounds();
            pet.moveToClamped(pressWindowX + dx, pressWindowY + dy, bounds);
            placeWindowAtPetPosition();
        }
        event.consume();
    }

    private void handleMouseReleased(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        primaryPressed = false;
        inputService.setPetDragging(false);
        canvas.setCursor(Cursor.OPEN_HAND);
        if (dragging) {
            long now = System.nanoTime();
            recordDragSample(event.getScreenX(), event.getScreenY(), now);
            double[] velocity = estimateThrowVelocity();
            movement.endDrag(velocity[0], velocity[1]);
            dragging = false;
            dragReleaseTimeNanos = now;
        }
    }

    private void handleMouseClicked(MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY) {
            showContextMenu();
            event.consume();
            return;
        }
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (dragReleaseTimeNanos != 0
                && System.nanoTime() - dragReleaseTimeNanos < CLICK_SUPPRESSION_NANOS) {
            event.consume();
            return;
        }
        contextMenu.close();
        radialMenu.toggle(stage, pet.getX(), pet.getY(), pet.getSize(), movement.getScreenBounds());
        event.consume();
    }

    private void recordDragSample(double x, double y, long timestamp) {
        dragSamples.addLast(new DragSample(x, y, timestamp));
        while (dragSamples.size() > 2
                && timestamp - dragSamples.peekFirst().timestampNanos() > DRAG_SAMPLE_WINDOW_NANOS) {
            dragSamples.removeFirst();
        }
    }

    private double[] estimateThrowVelocity() {
        if (dragSamples.size() < 2) {
            return new double[] {0, 0};
        }
        Iterator<DragSample> samples = dragSamples.iterator();
        DragSample first = samples.next();
        DragSample last = first;
        while (samples.hasNext()) {
            last = samples.next();
        }
        double elapsedSeconds = (last.timestampNanos() - first.timestampNanos()) / (double) 1_000_000_000L;
        if (elapsedSeconds < 0.025) {
            return new double[] {0, 0};
        }

        double velocityX = (last.x() - first.x()) / elapsedSeconds;
        double velocityY = (last.y() - first.y()) / elapsedSeconds;
        if (Math.hypot(velocityX, velocityY) < 180) {
            return new double[] {0, 0};
        }
        return new double[] {velocityX, velocityY};
    }

    private void placeWindowAtPetPosition() {
        if (Math.abs(stage.getX() - pet.getX()) > 0.05) {
            stage.setX(pet.getX());
        }
        if (Math.abs(stage.getY() - pet.getY()) > 0.05) {
            stage.setY(pet.getY());
        }
    }

    private void setMovementEnabled(boolean enabled) {
        movement.setMovementEnabled(enabled);
    }

    private void showContextMenu() {
        radialMenu.close();
        contextMenu.show(stage, pet.getX(), pet.getY(), pet.getSize(), movement.getScreenBounds());
    }

    private void updateMenuPause(boolean ignored) {
        speechBubble.hide();
        movement.setInteractionPaused(radialMenu.isShowing() || contextMenu.isShowing()
                || (featureStage != null && featureStage.isShowing()));
    }

    private void openFeatureWindow(RadialMenuAction action) {
        speechBubble.hide();
        if (featureStage != null && featureStage.isShowing()) {
            featureStage.toFront();
            quickActions.report("열려 있는 창을 먼저 사용하거나 닫아 주세요.");
            return;
        }
        quickActions.stop();
        radialMenu.close();
        contextMenu.close();
        stage.setAlwaysOnTop(false);
        movement.setInteractionPaused(true);
        if (action == RadialMenuAction.QUICK_ACTIONS) {
            QuickActionsWindow window = new QuickActionsWindow(stage, this::featureClosed, quickActions);
            featureStage = window.getStage(); window.show();
            return;
        }
        if (action == RadialMenuAction.INPUT_SETTINGS) {
            InputSettingsWindow window = new InputSettingsWindow(stage, () -> inputSettings, this::applyInputSettings,
                    inputService, inputNotice, quickActions::report, this::featureClosed);
            featureStage = window.getStage(); window.show();
            return;
        }
        if (action == RadialMenuAction.PET_CUSTOMIZATION) {
            PetCustomizationWindow window = new PetCustomizationWindow(stage,
                    renderer.createBuiltInEditableAnimations(), this::applyCustomization, this::featureClosed);
            featureStage = window.getStage(); window.show();
            return;
        }
        Stage window = new Stage();
        featureStage = window;
        window.initOwner(stage);
        window.setOnHidden(event -> featureClosed());
        window.setTitle(action.getLabel());
        Label title = new Label(action.getLabel());
        title.setStyle("-fx-text-fill: #F3F6FC; -fx-font-size: 20px; -fx-font-weight: bold;");
        Label explanation = new Label("이 항목의 실제 도구는 다음 개발 단계에서 연결됩니다.");
        explanation.setWrapText(true);
        explanation.setStyle("-fx-text-fill: #C5D0E0; -fx-font-size: 13px;");
        Button close = new Button("닫기"); close.setOnAction(event -> window.close());
        VBox content = new VBox(14, title, explanation, close);
        content.setStyle("-fx-background-color: #1A2233; -fx-padding: 24px;");
        window.setScene(new Scene(content, 330, 150, Color.web("#1A2233")));
        window.show(); window.toFront();
    }

    private void featureClosed() {
        featureStage = null;
        if (closing) return;
        stage.setAlwaysOnTop(true);
        stage.toFront();
        updateMenuPause(false);
    }

    private CompletableFuture<Void> applyInputSettings(InputSettings next) {
        if (closing) return CompletableFuture.failedFuture(new IllegalStateException("프로그램이 종료 중입니다."));
        return CompletableFuture.runAsync(() -> {
            InputSettings previous = inputSettings;
            boolean configured = false;
            try {
                inputService.configure(next).get(5, TimeUnit.SECONDS);
                configured = true;
                settingsService.saveInputSettings(next);
                inputSettings = next;
                inputNotice = "";
            } catch (Exception failure) {
                if (configured) {
                    try { inputService.configure(previous).get(5, TimeUnit.SECONDS); }
                    catch (Exception rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                }
                throw new CompletionException(failure);
            }
        }, settingsExecutor);
    }

    private void handleGlobalAction(InputAction action) {
        if (action == InputAction.STOP_AUTO_CLICK) { quickActions.stop(); return; }
        Platform.runLater(() -> {
            if (closing) return;
            switch (action) {
                case NONE, STOP_AUTO_CLICK -> { }
                case OPEN_QUICK_ACTIONS -> openFeatureWindow(RadialMenuAction.QUICK_ACTIONS);
                case COPY_BROWSER_URL -> quickActions.copyBrowserUrl(0);
                case COPY_TEXT -> quickActions.copyText();
                case PASTE_TEXT -> quickActions.pasteText(0);
                case START_AUTO_CLICK -> quickActions.startAutoClick();
                case COPY_FILE_PATH -> {
                    movement.setInteractionPaused(true);
                    try { quickActions.copyFilePath(featureStage == null ? stage : featureStage); }
                    finally { updateMenuPause(false); }
                }
                case TOGGLE_MOVEMENT -> {
                    setMovementEnabled(!pet.isMovementEnabled());
                    quickActions.report(pet.isMovementEnabled() ? "펫 자동 이동을 켰어요." : "펫 자동 이동을 껐어요.");
                }
                case TOGGLE_GRAVITY -> {
                    movement.setGravityRemoved(!movement.isGravityRemoved());
                    quickActions.report(movement.isGravityRemoved() ? "현재 위치에 펫을 고정했어요." : "중력을 다시 켰어요.");
                }
            }
        });
    }

    private void reportInputFailure(String message) {
        inputNotice = message;
        quickActions.report(message);
    }

    private void showActionStatus() {
        speechBubble.hide();
        statusView.show(quickActions.statusProperty().get(), quickActions.autoActiveProperty().get(),
                inputSettings.hotkeys().get(InputAction.STOP_AUTO_CLICK).toString());
    }

    private void updateSpeech(long now) {
        boolean busy = closing || primaryPressed || dragging || radialMenu.isShowing() || contextMenu.isShowing()
                || (featureStage != null && featureStage.isShowing()) || quickActions.isBusy() || statusView.isShowing()
                || pet.getState() == PetState.FALLING || pet.getState() == PetState.WALL_HIT
                || pet.getState() == PetState.LANDING || pet.getState() == PetState.SLEEPING;
        speechBubble.update(speech.update(now, busy));
    }

    private static String failureMessage(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return failure.getMessage();
    }
    private void applyCustomization(PetCustomization customization) {
        renderer.applyUserAnimations(customization.animations());
        renderer.setTintColor(customization.tint());
        renderer.setHorizontalFlip(customization.horizontalFlip());

        double newSize = DEFAULT_PET_SIZE * customization.sizeScale();
        pet.resize(newSize, movement.getScreenBounds());
        canvas.setWidth(newSize);
        canvas.setHeight(newSize);
        if (stage.getScene().getRoot() instanceof StackPane root) root.setPrefSize(newSize, newSize);
        stage.sizeToScene();
        placeWindowAtPetPosition();
    }

    private record DragSample(double x, double y, long timestampNanos) {
    }
}
