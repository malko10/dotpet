package com.dotpet.app;

import com.dotpet.menu.RadialMenu;
import com.dotpet.menu.RadialMenuAction;
import com.dotpet.menu.PetContextMenu;
import com.dotpet.customization.PetCustomization;
import com.dotpet.customization.PetCustomizationWindow;
import com.dotpet.pet.Pet;
import com.dotpet.pet.PetMovementController;
import com.dotpet.pet.PetRenderer;
import com.dotpet.pet.PetSpeechController;
import com.dotpet.pet.PetState;
import com.dotpet.pet.PetAngerController;
import com.dotpet.actions.QuickActionsWindow;
import com.dotpet.actions.QuickActionService;
import com.dotpet.input.InputAction;
import com.dotpet.input.InputSettingsWindow;
import com.dotpet.input.WindowsInputService;
import com.dotpet.settings.InputSettings;
import com.dotpet.settings.SettingsService;
import com.dotpet.settings.ProgramSettings;
import com.dotpet.settings.ProgramSettingsWindow;
import com.dotpet.storage.ActivePetRepository;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.Cursor;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
        return new Thread(task, "dotpet-settings");
    });
    private final WindowsInputService inputService;
    private final QuickActionService quickActions;
    private final DesktopStatusView statusView;
    private final PetSpeechController speech = new PetSpeechController(System.nanoTime());
    private final PetSpeechBubble speechBubble;
    private final PetAngerController anger = new PetAngerController();
    private final PetAngerEffect angerEffect;
    private volatile InputSettings inputSettings;
    private volatile String inputNotice = "";
    private Stage featureStage;
    private volatile boolean closing;
    private boolean ready;
    private ProgramSettings programSettings = ProgramSettings.defaults();
    private final ActivePetRepository activePetRepository = new ActivePetRepository(settingsService.getDirectory());
    private PetCustomization currentCustomization;
    private final ReadOnlyStringWrapper persistenceStatus = new ReadOnlyStringWrapper("설정을 불러오는 중이에요…");
    private final AtomicReference<ProgramSettings> pendingProgram = new AtomicReference<>();
    private final AtomicBoolean programWriteQueued = new AtomicBoolean();
    private volatile ProgramSettings lastRequestedProgram;
    private long lastCheckpoint;
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
        this.inputSettings = InputSettings.defaults();
        this.inputService = new WindowsInputService(settingsService.getDirectory(), this::handleGlobalAction, this::reportInputFailure);
        this.quickActions = new QuickActionService(inputService, () -> inputSettings, this::applyInputSettings);
        this.statusView = new DesktopStatusView(stage, pet, movement.getScreenBounds());
        this.speechBubble = new PetSpeechBubble(stage, pet, movement.getScreenBounds());
        this.angerEffect = new PetAngerEffect(stage, pet, movement.getScreenBounds());
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
                updateAngerEffect();
                if (now - lastCheckpoint >= 2_000_000_000L) { lastCheckpoint = now; saveProgramSnapshot(); }
            }
        };
        this.radialMenu = new RadialMenu(this::updateMenuPause, this::openFeatureWindow);
        this.contextMenu = new PetContextMenu(
                pet::isMovementEnabled,
                movement::isGravityRemoved,
                () -> setMovementEnabled(!pet.isMovementEnabled()),
                () -> {movement.setGravityRemoved(!movement.isGravityRemoved());saveProgramSnapshot();},
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
        CompletableFuture.supplyAsync(() -> new StartupData(settingsService.loadInputSettings(),
                settingsService.loadProgramSettings(), activePetRepository.load().orElse(null)), settingsExecutor)
                .whenComplete((loaded, failure) -> Platform.runLater(() -> {
                    if (closing) return;
                    StartupData restored = failure == null ? loaded : new StartupData(InputSettings.defaults(), ProgramSettings.defaults(), null);
                    inputSettings = restored.input();inputNotice = settingsService.getLoadNotice();programSettings = restored.program();
                    if (restored.profile() != null)
                        applyAppearance(restored.profile().resolveBuiltIn(renderer.createBuiltInEditableAnimations()));
                    applyProgramPreferences(programSettings);
                    if (programSettings.rememberPosition() && programSettings.hasPosition()) {
                        movement.beginDrag();pet.moveToClamped(programSettings.x(),programSettings.y(),movement.getScreenBounds());movement.endDrag(0,0);
                    } else returnToGround();
                    placeWindowAtPetPosition();
                    ready = true;renderer.render(pet,System.nanoTime());stage.show();timer.start();
                    persistenceStatus.set("설정과 적용한 캐릭터를 자동으로 저장합니다.");
                    String startupNotice = String.join(" ", inputNotice,settingsService.getProgramLoadNotice(),activePetRepository.getLoadNotice()).trim();
                    if (failure != null) startupNotice = "설정을 불러오지 못해 기본값으로 시작했어요. 기존 데이터는 보존했습니다.";
                    final String notice = startupNotice;
                    inputService.configure(inputSettings).whenComplete((ignored, inputFailure) -> {
                        if (inputFailure != null) reportInputFailure("전역 입력 시작 실패: " + failureMessage(inputFailure));
                        else quickActions.report(notice.isBlank() ? "설정은 자동 저장됩니다. 필요한 기능은 펫 메뉴에서 열어 보세요." : notice);
                    });
                }));
    }

    void stop() {
        if (closing) return; closing = true;
        radialMenu.close();
        contextMenu.close();
        timer.stop();
        quickActions.close();
        inputService.close();
        if (ready) {
            ProgramSettings snapshot = captureProgramSettings();PetCustomization profile = currentCustomization;
            settingsExecutor.execute(() -> {try {if(profile!=null)activePetRepository.save(profile);settingsService.saveProgramSettings(snapshot);}
                catch(Exception failure){settingsService.recordFailure("종료 설정 저장",failure);}});
        }
        settingsExecutor.shutdown();
        statusView.close();
        speechBubble.close();
        angerEffect.close();
    }

    private void handleMousePressed(MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        primaryPressed = true;
        speechBubble.hide();
        angerEffect.hide();
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
            saveProgramSnapshot();
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
        saveProgramSnapshot();
    }

    private void showContextMenu() {
        radialMenu.close();
        contextMenu.show(stage, pet.getX(), pet.getY(), pet.getSize(), movement.getScreenBounds());
    }

    private void updateMenuPause(boolean ignored) {
        speechBubble.hide();
        angerEffect.hide();
        movement.setInteractionPaused(radialMenu.isShowing() || contextMenu.isShowing()
                || (featureStage != null && featureStage.isShowing()));
    }

    private void openFeatureWindow(RadialMenuAction action) {
        speechBubble.hide();
        angerEffect.hide();
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
            PetCustomization active = currentCustomization == null ? PetCustomization.builtIn(
                    renderer.createBuiltInEditableAnimations(),pet.getSize()/DEFAULT_PET_SIZE,false,Color.WHITE) : currentCustomization;
            PetCustomizationWindow window = new PetCustomizationWindow(stage,
                    renderer.createBuiltInEditableAnimations(),active,this::applyCustomization,this::featureClosed);
            featureStage = window.getStage(); window.show();
            return;
        }
        if (action == RadialMenuAction.PROGRAM_SETTINGS) {
            ProgramSettingsWindow window = new ProgramSettingsWindow(stage,this::captureProgramSettings,this::changeProgramSettings,
                    persistenceStatus.getReadOnlyProperty(),settingsService.getDirectory(),this::returnToGround,this::featureClosed);
            featureStage = window.getStage();window.show();return;
        }
        throw new IllegalArgumentException("알 수 없는 펫 메뉴입니다.");
    }

    private void featureClosed() {
        featureStage = null;
        if (closing) return;
        stage.setAlwaysOnTop(programSettings.alwaysOnTop());
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
                case PASTE_CLIPBOARD -> quickActions.pasteClipboard(false);
                case PASTE_CLIPBOARD_ENTER -> quickActions.pasteClipboard(true);
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
                    saveProgramSnapshot();
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
        boolean busy = !programSettings.speechEnabled() || closing || primaryPressed || dragging || radialMenu.isShowing() || contextMenu.isShowing()
                || (featureStage != null && featureStage.isShowing()) || quickActions.isBusy() || statusView.isShowing()
                || pet.getState() == PetState.FALLING || pet.getState() == PetState.WALL_HIT
                || pet.getState() == PetState.LANDING || pet.getState() == PetState.SLEEPING;
        speechBubble.update(speech.update(now, busy));
    }

    private void updateAngerEffect() {
        boolean blocked = closing || primaryPressed || dragging || radialMenu.isShowing() || contextMenu.isShowing()
                || (featureStage != null && featureStage.isShowing()) || quickActions.isBusy() || statusView.isShowing();
        angerEffect.update(anger.update(pet.getState(), movement.getLandingProgress(), blocked), renderer.getSpriteBounds());
    }

    private static String failureMessage(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return failure.getMessage();
    }
    private void applyCustomization(PetCustomization customization) {
        applyAppearance(customization);
        settingsExecutor.execute(() -> {try {activePetRepository.save(customization);}
            catch(Exception failure){reportPersistenceFailure("펫 저장",failure);}});
        saveProgramSnapshot();
    }

    private void applyAppearance(PetCustomization customization) {
        currentCustomization = customization;
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

    private ProgramSettings captureProgramSettings() {
        boolean remember = programSettings.rememberPosition();
        return new ProgramSettings(pet.isMovementEnabled(),!movement.isGravityRemoved(),programSettings.speechEnabled(),
                programSettings.alwaysOnTop(),remember,remember,remember?pet.getX():0,remember?pet.getY():0,pet.getSize()/DEFAULT_PET_SIZE);
    }

    private void changeProgramSettings(ProgramSettings next) {
        applyProgramPreferences(next);saveProgramSnapshot();
    }

    private void applyProgramPreferences(ProgramSettings next) {
        programSettings = next;
        movement.setMovementEnabled(next.movementEnabled());
        if (movement.isGravityRemoved() == next.gravityEnabled()) movement.setGravityRemoved(!next.gravityEnabled());
        double size = DEFAULT_PET_SIZE * next.sizeScale();pet.resize(size,movement.getScreenBounds());canvas.setWidth(size);canvas.setHeight(size);
        if(stage.getScene().getRoot() instanceof StackPane root)root.setPrefSize(size,size);
        stage.sizeToScene();placeWindowAtPetPosition();
        stage.setAlwaysOnTop(next.alwaysOnTop() && (featureStage==null || !featureStage.isShowing()));
        if(!next.speechEnabled())speechBubble.hide();
        if(currentCustomization!=null)currentCustomization=new PetCustomization(currentCustomization.id(),currentCustomization.name(),
                currentCustomization.animations(),next.sizeScale(),currentCustomization.horizontalFlip(),currentCustomization.tint());
    }

    private void returnToGround() {
        movement.beginDrag();pet.moveToClamped(pet.getX(),movement.getGroundY(),movement.getScreenBounds());movement.endDrag(0,0);
        placeWindowAtPetPosition();saveProgramSnapshot();
    }

    private void saveProgramSnapshot() {
        if(!ready || closing)return;
        ProgramSettings snapshot=captureProgramSettings();
        if(snapshot.equals(lastRequestedProgram))return;
        lastRequestedProgram=snapshot;pendingProgram.set(snapshot);queueProgramWrite();
    }

    private void queueProgramWrite() {
        if(closing || !programWriteQueued.compareAndSet(false,true))return;
        settingsExecutor.execute(() -> {
            try {ProgramSettings snapshot;while((snapshot=pendingProgram.getAndSet(null))!=null){settingsService.saveProgramSettings(snapshot);}
                if(!closing)Platform.runLater(() -> {if(!closing)persistenceStatus.set("변경 사항을 자동 저장했어요.");});
            }catch(Exception failure){lastRequestedProgram=null;reportPersistenceFailure("설정 저장",failure);}
            finally {programWriteQueued.set(false);if(pendingProgram.get()!=null && !closing)queueProgramWrite();}
        });
    }

    private void reportPersistenceFailure(String operation,Exception failure) {
        settingsService.recordFailure(operation,failure);
        if(!closing)Platform.runLater(() -> {if(!closing){persistenceStatus.set(operation+" 실패: "+failure.getMessage());quickActions.report(operation+"에 실패했어요. 사용자 폴더의 저장 공간과 권한을 확인하세요.");}});
    }

    private record StartupData(InputSettings input,ProgramSettings program,PetCustomization profile) { }

    private record DragSample(double x, double y, long timestampNanos) {
    }
}
