package com.dotpet.customization;

import com.dotpet.animation.Animation;
import com.dotpet.animation.Frame;
import com.dotpet.editor.PetAnimationProject;
import com.dotpet.editor.PixelEditorWindow;
import com.dotpet.pet.PetState;
import com.dotpet.storage.PetProfileRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.Set;
import java.util.stream.Collectors;
import com.dotpet.pet.PixelSizing;
import java.util.function.Consumer;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/** Character selection, appearance controls, preview, and user-profile storage. */
public final class PetCustomizationWindow {
    private final Stage stage = new Stage();
    private final PetProfileRepository repository = new PetProfileRepository();
    private final ComboBox<PetCustomization> profilePicker = new ComboBox<>();
    private final TextField nameField = new TextField();
    private final Slider sizeSlider = new Slider(0.5, 2.0, 1.0);
    private final CheckBox flipCheck = new CheckBox("좌우 반전");
    private final ColorPicker tintPicker = new ColorPicker(Color.WHITE);
    private final Label colorLabel = new Label();
    private final FlowPane colorPresets = new FlowPane(7, 7);
    private final Canvas preview = new Canvas(176, 176);
    private final EnumMap<PetAccessory, CheckBox> accessoryChoices = new EnumMap<>(PetAccessory.class);
    private final Label status = new Label();
    private final List<PetCustomization> profiles = new ArrayList<>();
    private final Consumer<PetCustomization> applyHandler;
    private final Runnable onHidden;
    private PetCustomization selected;
    private boolean updating;
    private boolean recolorBody;
    private boolean closed, loadingStarted;
    private final ExecutorService files = Executors.newSingleThreadExecutor(task -> new Thread(task,"dotpet-profile-files"));

    public PetCustomizationWindow(Stage owner, Map<PetState, Animation> builtInAnimations,
            Consumer<PetCustomization> applyHandler, Runnable onHidden) {
        this(owner,builtInAnimations,null,applyHandler,onHidden);
    }

    public PetCustomizationWindow(Stage owner, Map<PetState, Animation> builtInAnimations, PetCustomization active,
            Consumer<PetCustomization> applyHandler, Runnable onHidden) {
        stage.initOwner(owner);
        stage.setTitle("DoT Pet · 캐릭터 꾸미기");
        this.applyHandler = applyHandler;
        this.onHidden = onHidden;
        preview.setId("appearance-preview");
        profiles.add(PetCustomization.builtIn(builtInAnimations, 1, false, Color.WHITE));
        if(active!=null)active=active.resolveBuiltIn(builtInAnimations);
        if(active!=null){if(active.id().equals("builtin"))profiles.set(0,active);else profiles.add(active);}
        selected = active==null?profiles.getFirst():active;
        stage.setScene(new Scene(createContent(), 840,
                Math.min(700, javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() - 50), Color.web("#111824")));
        stage.setMinWidth(800);
        stage.setMinHeight(540);
        stage.setOnHidden(event -> {closed=true;files.shutdown();onHidden.run();});
        loadSelectedProfile();
    }

    public void show() {
        stage.show();stage.toFront();
        if(!loadingStarted){loadingStarted=true;loadProfiles();}
    }
    public Stage getStage() { return stage; }

    private BorderPane createContent() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #111824; -fx-padding: 18;");

        profilePicker.getItems().setAll(profiles);
        profilePicker.setConverter(new StringConverter<>() {
            @Override public String toString(PetCustomization profile) { return profile == null ? "" : profile.name(); }
            @Override public PetCustomization fromString(String value) { return null; }
        });
        profilePicker.setOnAction(event -> {
            if (!updating && profilePicker.getValue() != null) {
                selected = profilePicker.getValue();
                loadSelectedProfile();
            }
        });
        Button importButton = button("캐릭터 프로젝트 불러오기");
        importButton.setOnAction(event -> importProject());
        Button createButton = button("새로 만들기");
        createButton.setId("new-pet");
        createButton.setOnAction(event -> {
            Frame blank = new Frame(32, 32, new int[32 * 32]);
            registerNewProfile(new PetAnimationProject(blank).toAnimations());
            openEditor();
        });
        HBox selectionRow = new HBox(9, new Label("캐릭터"), profilePicker, createButton, importButton);
        ((Label) selectionRow.getChildren().getFirst()).setStyle(labelStyle());
        HBox.setHgrow(profilePicker, Priority.ALWAYS);

        sizeSlider.setShowTickMarks(true);
        sizeSlider.setMajorTickUnit(0.5);
        sizeSlider.setMinorTickCount(1);
        sizeSlider.setBlockIncrement(0.25);
        sizeSlider.setSnapToTicks(true);
        sizeSlider.setId("appearance-size");
        sizeSlider.valueProperty().addListener((observable, oldValue, newValue) -> drawPreview());
        flipCheck.setStyle(labelStyle());
        flipCheck.setTooltip(new javafx.scene.control.Tooltip("걷기·달리기 원본은 왼쪽 기준입니다. 오른쪽 방향으로 그린 기존 캐릭터는 이 옵션으로 보정하세요."));
        flipCheck.setOnAction(event -> drawPreview());
        tintPicker.setId("pet-color");
        tintPicker.setOnAction(event -> {
            if (selected.id().equals("builtin") || selected.recolorBody()) recolorBody = true;
            drawPreview();
        });
        colorLabel.setStyle(labelStyle());
        String[] colors = {"#75B9FF", "#78D877", "#F08DB5", "#F3C35F", "#B69AF0", "#FFFFFF"};
        for (String hex : colors) {
            Button swatch = button("");
            swatch.setId("slime-color-" + hex.substring(1));
            swatch.setMinSize(27, 27); swatch.setPrefSize(27, 27);
            swatch.setStyle("-fx-background-color: " + hex + "; -fx-border-color: #B4C7DE; -fx-background-radius: 5; -fx-border-radius: 5;");
            swatch.setAccessibleText("슬라임 색상 " + hex);
            swatch.setTooltip(new javafx.scene.control.Tooltip("슬라임 몸체 색상 " + hex));
            swatch.setOnAction(event -> { recolorBody = true; tintPicker.setValue(Color.web(hex)); drawPreview(); });
            colorPresets.getChildren().add(swatch);
        }
        Button original = button("기본 파랑"); original.setId("slime-color-reset");
        original.setOnAction(event -> { recolorBody = false; tintPicker.setValue(Color.WHITE); drawPreview(); });
        colorPresets.getChildren().add(original);
        nameField.setPromptText("저장할 캐릭터 이름");

        VBox equipment = new VBox(7);
        Label equipmentTitle = new Label("장식품 · 왕관/모자 중 하나 + 안대·수염"); equipmentTitle.setStyle(labelStyle());
        equipment.getChildren().add(equipmentTitle);
        for (PetAccessory item : PetAccessory.values()) {
            CheckBox choice = new CheckBox(item.getLabel());
            choice.setId("accessory-" + item.name()); choice.setStyle(labelStyle());
            choice.setOnAction(event -> {
                if (choice.isSelected() && item == PetAccessory.CROWN) accessoryChoices.get(PetAccessory.HAT).setSelected(false);
                if (choice.isSelected() && item == PetAccessory.HAT) accessoryChoices.get(PetAccessory.CROWN).setSelected(false);
                drawPreview();
            });
            accessoryChoices.put(item, choice); equipment.getChildren().add(choice);
        }

        VBox controls = new VBox(14,
                labeled("캐릭터 이름", nameField),
                labeled("크기 (50%–200%)", sizeSlider),
                flipCheck,
                new VBox(8, colorLabel, tintPicker, colorPresets), equipment);
        controls.setStyle("-fx-padding: 8 0 0 4;");
        StackPane previewSpace = new StackPane(preview);
        previewSpace.setMinSize(280, 280); previewSpace.setPrefSize(280, 280); previewSpace.setMaxSize(280, 280);
        previewSpace.setStyle("-fx-background-color: #1B2839;");
        VBox previewPane = new VBox(10, new Label("미리보기 · 외형 전체 확대"), previewSpace);
        ((Label) previewPane.getChildren().getFirst()).setStyle(labelStyle());
        HBox center = new HBox(28, previewPane, controls);
        HBox.setHgrow(controls, Priority.ALWAYS);
        root.setTop(selectionRow);
        ScrollPane scroll = new ScrollPane(center); scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #111824; -fx-background-color: #111824;");
        root.setCenter(scroll);

        Label help = new Label("캐릭터를 선택하거나 .dotpet 프로젝트를 불러온 뒤 크기·방향·색조를 조정하세요.\n"
                + "‘캐릭터 저장’은 목록에 보관하고, ‘펫에 적용’은 현재 펫과 다음 실행에 반영합니다.");
        help.setWrapText(true);
        help.setStyle("-fx-text-fill: #AEBBD0; -fx-padding: 8 0;");
        Button edit = button("픽셀 / 애니메이션 편집");
        edit.setOnAction(event -> openEditor());
        Button save = button("캐릭터 저장");
        save.setOnAction(event -> saveProfile());
        Button apply = button("펫에 적용 후 닫기");
        apply.setId("apply-appearance");
        apply.setOnAction(event -> {
            PetCustomization customization = fromControls(selected.id(), displayName(), selected.animations());
            applyHandler.accept(customization);
            stage.close();
        });
        HBox actions = new HBox(9, edit, save, apply, status);
        actions.setStyle("-fx-alignment: center-left;");
        VBox bottom = new VBox(6, help, actions);
        root.setBottom(bottom);
        return root;
    }

    private VBox labeled(String title, javafx.scene.Node control) {
        Label label = new Label(title);
        label.setStyle(labelStyle());
        return new VBox(6, label, control);
    }

    private void loadSelectedProfile() {
        updating = true;
        try {
            profilePicker.setValue(selected);
            nameField.setText(selected.name());
            sizeSlider.setValue(selected.sizeScale());
            flipCheck.setSelected(selected.horizontalFlip());
            tintPicker.setValue(selected.tint());
            recolorBody = selected.recolorBody();
            accessoryChoices.forEach((item, choice) -> choice.setSelected(selected.accessories().contains(item)));
            boolean slime = selected.id().equals("builtin") || selected.recolorBody();
            colorLabel.setText(slime ? "슬라임 몸체 색상" : "캐릭터 색조");
            colorPresets.setVisible(slime); colorPresets.setManaged(slime);
        } finally { updating = false; }
        drawPreview();
    }

    private void drawPreview() {
        if (selected == null) return;
        double extent = PixelSizing.BASE_SIZE * sizeSlider.getValue();
        preview.setWidth(extent); preview.setHeight(extent);
        GraphicsContext g = preview.getGraphicsContext2D();
        g.clearRect(0, 0, preview.getWidth(), preview.getHeight());
        double checker = 11;
        for (int y = 0; y < preview.getHeight(); y += (int) checker) {
            for (int x = 0; x < preview.getWidth(); x += (int) checker) {
                g.setFill(((x / 11 + y / 11) & 1) == 0 ? Color.web("#DCE2E8") : Color.web("#BBC5D0"));
                g.fillRect(x, y, checker, checker);
            }
        }
        Frame frame = PetAppearance.compose(selected.animations().get(PetState.IDLE).getFrame(0),
                tintPicker.getValue(), recolorBody, chosenAccessories());
        double output = stage.isShowing() ? Math.min(stage.getRenderScaleX(), stage.getRenderScaleY()) : 1;
        var layout = PixelSizing.fit(frame.getWidth(), frame.getHeight(), extent, extent, output);
        double scale = layout.unit(), offsetX = layout.x(), offsetY = layout.y();
        double mirrorWidth = Math.round(extent * output) / output;
        boolean mirror = flipCheck.isSelected();
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                int argb = frame.getPixel(x, y);
                if ((argb >>> 24) == 0) continue;
                g.setFill(Color.rgb((argb >>> 16) & 255, (argb >>> 8) & 255, argb & 255, (argb >>> 24) / 255.0));
                double left = offsetX + x * scale, top = offsetY + y * scale;
                g.fillRect(mirror ? mirrorWidth - left - scale : left, top, scale, scale);
            }
        }
    }

    private void importProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("DoT Pet 캐릭터 프로젝트 선택");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("DoT Pet 프로젝트", "*.dotpet"));
        var file = chooser.showOpenDialog(stage);
        if (file == null) return;
        runFileTask("캐릭터 불러오기",() -> {
            PetAnimationProject project = PixelEditorWindow.loadProject(file.toPath());
            String name = file.getName().replaceFirst("(?i)\\.dotpet$", "");
            String id = "user-" + name.toLowerCase().replaceAll("[^a-z0-9가-힣_-]+", "-");
            return repository.save(new PetCustomization(id, name, project.toAnimations(),
                    1.0, false, Color.WHITE));
        },imported -> {
            profiles.removeIf(profile -> profile.id().equals(imported.id()));
            profiles.add(imported);
            profilePicker.getItems().setAll(profiles);
            selected = imported;
            loadSelectedProfile();
            status.setText("캐릭터를 불러왔습니다.");
        });
    }

    private void saveProfile() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) { showError("캐릭터 이름을 입력하세요", new IllegalArgumentException("저장하려면 캐릭터 이름이 필요합니다.")); return; }
        String id = selected.id().equals("builtin") ? "user-" + name.toLowerCase().replaceAll("[^a-z0-9가-힣_-]+", "-") : selected.id();
        PetCustomization candidate=fromControls(id,name,selected.animations());
        runFileTask("캐릭터 저장",() -> repository.save(candidate),saved -> {
            selected=saved;
            profiles.removeIf(profile -> profile.id().equals(selected.id()));
            profiles.add(selected);
            profilePicker.getItems().setAll(profiles);
            profilePicker.setValue(selected);
            status.setText("캐릭터를 사용자 폴더에 저장했습니다.");
        });
    }

    private void openEditor() {
        PetAnimationProject project = new PetAnimationProject(
                selected.animations().get(PetState.IDLE).getFrame(0), selected.animations());
        PixelEditorWindow editor = new PixelEditorWindow(stage, project.getFrame(PetState.IDLE, 0),
                selected.animations(), edited -> {
                    String id=selected.id().equals("builtin")?"user-"+java.util.UUID.randomUUID():selected.id();
                    selected = fromControls(id, displayName(), edited);
                    int index = profileIndex(selected.id());
                    if (index >= 0) profiles.set(index, selected);else profiles.add(selected);
                    updating=true;profilePicker.getItems().setAll(profiles);profilePicker.setValue(selected);updating=false;
                    drawPreview();
                    applyHandler.accept(selected);
                    status.setText("편집한 펫은 다음 실행에도 복원됩니다. 목록에 보관하려면 캐릭터 저장을 누르세요.");
                }, () -> stage.toFront(), this::registerNewProfile);
        editor.show();
    }

    private void registerNewProfile(Map<PetState, Animation> animations) {
        updating = true;
        try {
            selected = new PetCustomization("user-" + java.util.UUID.randomUUID(), "새 캐릭터", animations,
                    1.0, false, Color.WHITE);
            profiles.add(selected);
            profilePicker.getItems().setAll(profiles);
        } finally { updating = false; }
        loadSelectedProfile();
        status.setText("새 캐릭터를 만들었어요. 그림을 그린 뒤 이름을 정해 저장하세요.");
    }

    private PetCustomization fromControls(String id, String name, Map<PetState, Animation> animations) {
        return new PetCustomization(id, name, animations, sizeSlider.getValue(), flipCheck.isSelected(), tintPicker.getValue(), recolorBody, chosenAccessories());
    }

    private Set<PetAccessory> chosenAccessories() {
        return accessoryChoices.entrySet().stream().filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }

    private int profileIndex(String id) {
        for (int i = 0; i < profiles.size(); i++) if (profiles.get(i).id().equals(id)) return i;
        return -1;
    }

    private String displayName(){String name=nameField.getText();return name==null||name.isBlank()?selected.name():name.trim();}

    private void loadProfiles(){
        CompletableFuture.supplyAsync(() -> {try{return repository.loadProfiles();}catch(IOException failure){throw new CompletionException(failure);}},files)
                .whenComplete((loaded,failure) -> Platform.runLater(() -> {
                    if(closed)return;
                    if(failure!=null){status.setText("캐릭터 목록을 읽지 못했어요. 기존 파일은 보존했습니다.");return;}
                    for(PetCustomization profile:loaded)if(profileIndex(profile.id())<0)profiles.add(profile);
                    updating=true;profilePicker.getItems().setAll(profiles);profilePicker.setValue(selected);updating=false;
                    if(!repository.getLoadNotice().isBlank())status.setText(repository.getLoadNotice());
                }));
    }

    private <T> void runFileTask(String operation,Callable<T> task,Consumer<T> done){
        stage.getScene().getRoot().setDisable(true);status.setText(operation+" 중…");
        CompletableFuture.supplyAsync(() -> {try{return task.call();}catch(Exception failure){throw new CompletionException(failure);}},files)
                .whenComplete((result,failure) -> Platform.runLater(() -> {
                    if(closed)return;stage.getScene().getRoot().setDisable(false);
                    if(failure!=null){Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();showError(operation+" 실패",new IOException(cause.getMessage(),cause));}
                    else done.accept(result);
                }));
    }

    private static Button button(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: #26364D; -fx-text-fill: #F3F6FC; -fx-border-color: #52647A;");
        button.setFocusTraversable(false);
        return button;
    }

    private static String labelStyle() { return "-fx-text-fill: #D6DFEB;"; }

    private void showError(String title, Exception error) {
        Alert alert = new Alert(Alert.AlertType.ERROR, error.getMessage());
        alert.setTitle(title); alert.setHeaderText(title); alert.initOwner(stage); alert.show();
    }
}
