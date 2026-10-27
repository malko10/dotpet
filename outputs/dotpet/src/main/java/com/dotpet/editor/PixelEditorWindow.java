package com.dotpet.editor;

import com.dotpet.animation.Animation;
import com.dotpet.animation.Frame;
import com.dotpet.pet.PetState;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import javafx.animation.AnimationTimer;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javax.imageio.ImageIO;

/** Pixel and multi-frame animation editor for the four desktop-pet behaviors. */
public final class PixelEditorWindow {
    private static final double CANVAS_DISPLAY_SIZE = 512;
    private static final Color[] PALETTE = {
            Color.web("#26384C"), Color.web("#9AE66E"), Color.web("#68B957"), Color.web("#F3F6FC"),
            Color.web("#E88D8D"), Color.web("#F5C96A"), Color.web("#68B9D8"), Color.web("#B6A2FF"), Color.BLACK
    };

    private final Stage stage;
    private final Canvas canvas = new Canvas(CANVAS_DISPLAY_SIZE, CANVAS_DISPLAY_SIZE);
    private final ImageView preview = new ImageView();
    private final ColorPicker colorPicker = new ColorPicker(Color.web("#9AE66E"));
    private final ComboBox<Integer> dimensions = new ComboBox<>();
    private final ComboBox<PetState> statePicker = new ComboBox<>();
    private final HBox frameStrip = new HBox(8);
    private final TitledPane animationPane = new TitledPane();
    private final Spinner<Double> fpsPicker = new Spinner<>(1.0, 24.0, 8.0, 1.0);
    private final CheckBox onionSkin = new CheckBox("이전 프레임 비침");
    private final Label status = new Label();
    private PetAnimationProject project;
    private PixelDocument document;
    private final Consumer<Map<PetState, Animation>> applyHandler;
    private final Runnable onHidden;
    private final Consumer<Map<PetState, Animation>> newProjectHandler;
    private final AnimationTimer previewTimer;
    private PetAnimationProject bulkUndo;
    private PetAnimationProject bulkRedo;
    private Button pencilButton;
    private Button eraserButton;
    private boolean dirty;
    private boolean newProjectMode;
    private boolean eraser;
    private boolean strokeActive;
    private boolean updatingSelectors;
    private int previousX;
    private int previousY;
    private long previewStartedAt;

    public PixelEditorWindow(Stage owner, Frame initialFrame, Map<PetState, Animation> seedAnimations,
            Consumer<Map<PetState, Animation>> applyHandler, Runnable onHidden) {
        this(owner, initialFrame, seedAnimations, applyHandler, onHidden, ignored -> { });
    }

    public PixelEditorWindow(Stage owner, Frame initialFrame, Map<PetState, Animation> seedAnimations,
            Consumer<Map<PetState, Animation>> applyHandler, Runnable onHidden,
            Consumer<Map<PetState, Animation>> newProjectHandler) {
        this.stage = new Stage();
        this.stage.initOwner(owner);
        this.stage.setTitle("DoT Pet · 픽셀 / 애니메이션 편집기");
        this.project = new PetAnimationProject(initialFrame, seedAnimations);
        this.document = new PixelDocument(initialFrame.getWidth());
        this.document.replace(initialFrame.getWidth(), initialFrame.getHeight(), initialFrame.copyPixels());
        this.applyHandler = applyHandler;
        this.onHidden = onHidden;
        this.newProjectHandler = newProjectHandler;
        this.newProjectMode = project.isEmpty();
        this.previewTimer = new AnimationTimer() {
            @Override public void handle(long now) { updatePreview(now); }
        };
        this.stage.setScene(new Scene(createLayout(), 980, 790, Color.web("#111824")));
        var stylesheet = PixelEditorWindow.class.getResource("/styles/editor.css");
        if (stylesheet != null) stage.getScene().getStylesheets().add(stylesheet.toExternalForm());
        this.stage.setMinWidth(860);
        this.stage.setMinHeight(700);
        this.stage.setOnHidden(event -> {
            previewTimer.stop();
            onHidden.run();
        });
        installCanvasHandlers();
        installKeyboardShortcuts();
        loadSelectedFrame();
        redraw();
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    private BorderPane createLayout() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #111824; -fx-padding: 18;");

        dimensions.getItems().addAll(16, 32, 64);
        dimensions.setValue(project.getCanvasSize());
        dimensions.setPrefWidth(76);
        dimensions.setOnAction(event -> resizeProject());
        statePicker.getItems().setAll(project.getEditableStates());
        statePicker.setConverter(new StringConverter<>() {
            @Override public String toString(PetState state) { return state == null ? "" : stateLabel(state); }
            @Override public PetState fromString(String value) { return null; }
        });
        statePicker.setValue(PetState.IDLE);
        statePicker.setOnAction(event -> selectStateFromPicker());
        fpsPicker.setPrefWidth(82);
        fpsPicker.setEditable(false);
        fpsPicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!updatingSelectors && newValue != null && Double.isFinite(newValue)) {
                project.setFramesPerSecond(newValue);
                dirty = true;
            }
        });
        onionSkin.setSelected(false);
        onionSkin.setStyle(textStyle());
        onionSkin.setOnAction(event -> redraw());

        Button create = button("새로 만들기");
        create.setId("new-project");
        create.setOnAction(event -> newProject());
        Button open = button("열기");
        open.setOnAction(event -> openProject());
        Button save = button("저장");
        save.setId("save-project");
        save.setOnAction(event -> saveProject());
        Button png = button("PNG 내보내기");
        png.setOnAction(event -> exportPng());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox fileTools = new HBox(8, create, open, save, png, spacer, caption("픽셀 크기"), dimensions);
        fileTools.setStyle("-fx-alignment: center-left; -fx-padding: 0 0 14 0;");
        root.setTop(fileTools);

        pencilButton = iconButton(EditorIcons.Tool.PENCIL, "연필 · B", "tool-pencil");
        eraserButton = iconButton(EditorIcons.Tool.ERASER, "지우개 · E", "tool-eraser");
        pencilButton.setOnAction(event -> { eraser = false; updateToolStyles(pencilButton, eraserButton); });
        eraserButton.setOnAction(event -> { eraser = true; updateToolStyles(pencilButton, eraserButton); });
        updateToolStyles(pencilButton, eraserButton);
        Button undo = iconButton(EditorIcons.Tool.UNDO, "실행 취소 · Ctrl+Z", "undo");
        undo.setOnAction(event -> undoEdit());
        Button redo = iconButton(EditorIcons.Tool.REDO, "다시 실행 · Ctrl+Y", "redo");
        redo.setOnAction(event -> redoEdit());
        Button copy = iconButton(EditorIcons.Tool.COPY, "현재 프레임 복사 · Ctrl+C", "copy-frame");
        copy.setOnAction(event -> copyFrameToClipboard());
        Button paste = iconButton(EditorIcons.Tool.PASTE, "현재 프레임에 붙여넣기 · Ctrl+V", "paste-frame");
        paste.setOnAction(event -> pasteFrameFromClipboard());
        Button clear = iconButton(EditorIcons.Tool.CLEAR, "현재 프레임 비우기", "clear-frame");
        clear.setOnAction(event -> { document.clear(); commitFrameEdit(); });
        VBox drawingTools = new VBox(8, pencilButton, eraserButton, new Separator(), undo, redo,
                new Separator(), copy, paste, clear);
        drawingTools.setStyle("-fx-padding: 0 10 0 0;");
        drawingTools.setPrefWidth(58);

        ScrollPane drawingArea = new ScrollPane(canvas);
        drawingArea.setFitToWidth(false);
        drawingArea.setFitToHeight(false);
        drawingArea.setPannable(false);
        drawingArea.setStyle("-fx-background: #202A39; -fx-background-color: #202A39;");
        HBox.setHgrow(drawingArea, Priority.ALWAYS);

        FlowPane palette = new FlowPane(7, 7);
        palette.setPrefWrapLength(190);
        for (Color color : PALETTE) {
            Button swatch = new Button();
            String hex = color.toString().substring(2, 8);
            swatch.setTooltip(new Tooltip("#" + hex));
            swatch.setAccessibleText("색상 #" + hex);
            swatch.setMinSize(28, 28);
            swatch.setPrefSize(28, 28);
            swatch.setMaxSize(28, 28);
            swatch.setStyle("-fx-background-color: #" + hex + "; -fx-border-color: #D6DFEB; -fx-border-radius: 4;");
            swatch.setOnAction(event -> colorPicker.setValue(color));
            palette.getChildren().add(swatch);
        }
        preview.setFitWidth(144);
        preview.setFitHeight(144);
        preview.setPreserveRatio(true);
        preview.setSmooth(false);
        StackPane previewTile = new StackPane(checkerboard(144), preview);
        previewTile.setMaxSize(144, 144);
        ToggleGroup previewDirection = new ToggleGroup();
        RadioButton originalPreview = new RadioButton("← 원본");
        RadioButton reflectedPreview = new RadioButton("반전 →");
        originalPreview.setToggleGroup(previewDirection);
        reflectedPreview.setToggleGroup(previewDirection);
        originalPreview.setSelected(true);
        originalPreview.setStyle(textStyle());
        reflectedPreview.setStyle(textStyle());
        reflectedPreview.setId("preview-reflected");
        reflectedPreview.selectedProperty().addListener((observable, previous, reflected) -> preview.setScaleX(reflected ? -1 : 1));
        Button play = iconButton(EditorIcons.Tool.PLAY, "선택한 동작 재생", "preview-play");
        Button stop = iconButton(EditorIcons.Tool.STOP, "미리보기 정지", "preview-stop");
        play.setOnAction(event -> startPreview());
        stop.setOnAction(event -> stopPreview());
        Label help = caption("새 펫: 그림 그리기 → 적용\n\n같은 그림을 모든 동작에 쓰려면 ‘모든 프레임에 복사’를 누르세요.\n\n걷기·달리기는 왼쪽 한 방향만 그립니다.");
        help.setWrapText(true);
        help.setStyle("-fx-text-fill: #AEBBD0; -fx-font-size: 12px; -fx-line-spacing: 4;");
        VBox side = new VBox(12, caption("색상"), colorPicker, palette, new Separator(),
                caption("펫 미리보기"), previewTile, new HBox(8, originalPreview, reflectedPreview), new HBox(8, play, stop), help);
        side.setMinWidth(196);
        side.setPrefWidth(206);
        side.setMaxWidth(220);
        side.setStyle("-fx-padding: 0 0 0 14;");
        HBox center = new HBox(drawingTools, drawingArea, side);
        root.setCenter(center);

        Button add = iconButton(EditorIcons.Tool.ADD, "프레임 추가", "frame-add");
        add.setOnAction(event -> { syncSelectedFrame(); project.addFrame(); dirty = true; loadSelectedFrame(); });
        Button duplicate = iconButton(EditorIcons.Tool.COPY, "선택한 프레임 복제", "frame-duplicate");
        duplicate.setOnAction(event -> { syncSelectedFrame(); project.copyFrame(); dirty = true; loadSelectedFrame(); });
        Button delete = iconButton(EditorIcons.Tool.DELETE, "선택한 프레임 삭제", "frame-delete");
        delete.setOnAction(event -> {
            syncSelectedFrame();
            if (project.getFrameCount(project.getSelectedState()) > 1) { project.deleteFrame(); dirty = true; }
            loadSelectedFrame();
        });
        Button left = iconButton(EditorIcons.Tool.LEFT, "프레임을 앞으로", "frame-left");
        left.setOnAction(event -> reorderFrame(-1));
        Button right = iconButton(EditorIcons.Tool.RIGHT, "프레임을 뒤로", "frame-right");
        right.setOnAction(event -> reorderFrame(1));
        HBox animationControls = new HBox(9, caption("동작"), statePicker, caption("속도 FPS"), fpsPicker,
                onionSkin, add, duplicate, delete, left, right);
        animationControls.setStyle("-fx-alignment: center-left;");
        ScrollPane timeline = new ScrollPane(frameStrip);
        timeline.setFitToHeight(true);
        timeline.setPrefHeight(94);
        timeline.setStyle("-fx-background: #182230; -fx-background-color: #182230;");
        animationPane.setText("애니메이션 편집 · 동작과 프레임");
        animationPane.setId("animation-details");
        Label directionGuide = caption("← 걷기·달리기는 왼쪽을 보는 프레임 한 벌만 그리세요. 오른쪽 이동은 자동 반전됩니다.");
        directionGuide.setId("walk-direction-guide");
        directionGuide.setWrapText(true);
        directionGuide.setStyle("-fx-text-fill: #B7E49C; -fx-font-size: 12px;");
        animationPane.setContent(new VBox(8, directionGuide, animationControls, timeline));
        animationPane.setExpanded(false);
        animationPane.expandedProperty().addListener((observable, previous, expanded) -> {
            if (expanded) {
                newProjectMode = false;
                makeRoomForTimeline();
            }
        });

        Button copyAll = button("모든 프레임에 복사");
        copyAll.setId("copy-all-frames");
        copyAll.setTooltip(new Tooltip("현재 그림을 대기·걷기·달리기·잠자기의 모든 프레임에 복사합니다. 실행 취소로 되돌릴 수 있어요."));
        copyAll.setOnAction(event -> copyToAllFrames());
        Button apply = button("펫에 적용 후 닫기");
        apply.setId("apply-pet");
        apply.setStyle("-fx-background-color: #9AE66E; -fx-text-fill: #14221B; -fx-font-weight: bold; -fx-padding: 9 18;");
        apply.setOnAction(event -> applyProject());
        HBox footer = new HBox(10, copyAll, apply);
        footer.setStyle("-fx-alignment: center-right; -fx-padding: 10 0 0 0;");
        status.setStyle("-fx-text-fill: #AEBBD0; -fx-padding: 6 0 0 0;");
        status.setWrapText(true);
        root.setBottom(new VBox(8, animationPane, footer, status));
        return root;
    }
    private void newProject() {
        if (dirty) {
            ButtonType start = new ButtonType("새로 시작");
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "저장하지 않은 편집 내용이 있어요. 필요하면 취소 후 먼저 저장하세요.", start, ButtonType.CANCEL);
            confirm.setTitle("새 펫 만들기");
            confirm.setHeaderText("빈 캔버스로 새 펫을 만들까요?");
            confirm.initOwner(stage);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != start) return;
        }
        stopPreview();
        int size = project.getCanvasSize();
        project = new PetAnimationProject(new Frame(size, size, new int[size * size]));
        bulkUndo = null;
        bulkRedo = null;
        animationPane.setExpanded(false);
        newProjectMode = true;
        dirty = false;
        eraser = false;
        updateToolStyles(pencilButton, eraserButton);
        loadSelectedFrame();
        newProjectHandler.accept(project.toAnimations());
        status.setText("새 펫 · 그림을 그린 뒤 적용하세요. 기본 모드에서는 모든 동작에 같은 그림을 사용해요.");
    }

    private void commitFrameEdit() {
        dirty = true;
        bulkRedo = null;
        syncSelectedFrame();
        redraw();
    }

    private void makeRoomForTimeline() {
        if (!stage.isShowing()) return;
        var screen = javafx.stage.Screen.getScreensForRectangle(stage.getX(), stage.getY(),
                stage.getWidth(), stage.getHeight()).stream().findFirst().orElse(javafx.stage.Screen.getPrimary());
        var area = screen.getVisualBounds();
        double height = Math.min(area.getHeight() - 24, Math.max(stage.getHeight(), 1000));
        if (height > stage.getHeight()) stage.setHeight(height);
        stage.setY(Math.max(area.getMinY(), Math.min(stage.getY(), area.getMaxY() - stage.getHeight())));
    }

    private void copyToAllFrames() {
        syncSelectedFrame();
        bulkUndo = project.copy();
        bulkRedo = null;
        project.copySelectedFrameToAll();
        dirty = true;
        loadSelectedFrame();
        int count = project.getEditableStates().stream().mapToInt(project::getFrameCount).sum();
        status.setText("모든 동작의 " + count + "개 프레임에 그림을 복사했어요. 실행 취소로 되돌릴 수 있어요.");
    }

    private void undoEdit() {
        if (document.undo()) {
            syncSelectedFrame();
            refreshFrameStrip();
            redraw();
        } else if (bulkUndo != null) {
            bulkRedo = project.copy();
            project = bulkUndo;
            bulkUndo = null;
            loadSelectedFrame();
            status.setText("모든 프레임에 복사하기를 취소했어요.");
        }
        dirty = true;
    }

    private void redoEdit() {
        if (document.redo()) {
            syncSelectedFrame();
            refreshFrameStrip();
            redraw();
        } else if (bulkRedo != null) {
            bulkUndo = project.copy();
            project = bulkRedo;
            bulkRedo = null;
            loadSelectedFrame();
        }
        dirty = true;
    }

    private void pasteFrameFromClipboard() {
        javafx.scene.image.Image image = Clipboard.getSystemClipboard().getImage();
        if (image == null || image.getPixelReader() == null) {
            status.setText("복사한 그림이 없어요. 먼저 프레임 복사 버튼을 누르세요.");
            return;
        }
        int size = project.getCanvasSize();
        document.beginEdit();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int sourceX = Math.min((int) image.getWidth() - 1, (int) (x * image.getWidth() / size));
                int sourceY = Math.min((int) image.getHeight() - 1, (int) (y * image.getHeight() / size));
                document.paint(x, y, image.getPixelReader().getArgb(sourceX, sourceY));
            }
        }
        commitFrameEdit();
        refreshFrameStrip();
        status.setText("현재 프레임에 그림을 붙여넣었어요.");
    }

    private void applyProject() {
        syncSelectedFrame();
        if (project.isEmpty()) {
            status.setText("그림이 비어 있어요. 픽셀을 그리거나 PNG를 불러온 뒤 적용하세요.");
            return;
        }
        if (newProjectMode) project.copySelectedFrameToAll();
        applyHandler.accept(project.toAnimations());
        stopPreview();
        stage.close();
    }

    private void installKeyboardShortcuts() {
        stage.getScene().setOnKeyPressed(event -> {
            if (event.getTarget() instanceof TextInputControl) return;
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case Z -> undoEdit();
                    case Y -> redoEdit();
                    case C -> copyFrameToClipboard();
                    case V -> pasteFrameFromClipboard();
                    case S -> saveProject();
                    case N -> newProject();
                    default -> { return; }
                }
            } else if (event.getCode() == KeyCode.B) pencilButton.fire();
            else if (event.getCode() == KeyCode.E) eraserButton.fire();
            else return;
            event.consume();
        });
    }

    private static Canvas checkerboard(int size) {
        Canvas board = new Canvas(size, size);
        GraphicsContext g = board.getGraphicsContext2D();
        for (int y = 0; y < size; y += 12) {
            for (int x = 0; x < size; x += 12) {
                g.setFill(((x / 12 + y / 12) & 1) == 0 ? Color.web("#DCE2E8") : Color.web("#BBC5D0"));
                g.fillRect(x, y, Math.min(12, size - x), Math.min(12, size - y));
            }
        }
        return board;
    }

    private static Label caption(String text) {
        Label label = new Label(text);
        label.setStyle(textStyle());
        return label;
    }

    private static Button iconButton(EditorIcons.Tool tool, String description, String id) {
        Button button = new Button("", EditorIcons.create(tool));
        button.setId(id);
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        button.setFocusTraversable(false);
        button.setMinSize(42, 42);
        button.setPrefSize(42, 42);
        button.setMaxSize(42, 42);
        button.setStyle(toolStyle(false));
        return button;
    }

    private void installCanvasHandlers() {
        canvas.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            int[] point = pixelAt(event.getX(), event.getY());
            if (point == null) return;
            document.beginEdit();
            strokeActive = true;
            previousX = point[0]; previousY = point[1];
            paint(point[0], point[1]); event.consume();
        });
        canvas.setOnMouseDragged(event -> {
            if (!strokeActive || !event.isPrimaryButtonDown()) return;
            int[] point = pixelAt(event.getX(), event.getY());
            if (point == null) return;
            drawLine(previousX, previousY, point[0], point[1]);
            previousX = point[0]; previousY = point[1];
            commitFrameEdit();
        });
        canvas.setOnMouseReleased(event -> { strokeActive = false; refreshFrameStrip(); });
    }

    private void drawLine(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            document.paint(x0, y0, eraser ? 0 : toArgb(colorPicker.getValue()));
            if (x0 == x1 && y0 == y1) break;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
    }

    private void paint(int x, int y) {
        document.paint(x, y, eraser ? 0 : toArgb(colorPicker.getValue()));
        commitFrameEdit();
    }

    private int[] pixelAt(double x, double y) {
        if (x < 0 || y < 0 || x >= CANVAS_DISPLAY_SIZE || y >= CANVAS_DISPLAY_SIZE) return null;
        double cell = CANVAS_DISPLAY_SIZE / project.getCanvasSize();
        return new int[] {(int) (x / cell), (int) (y / cell)};
    }

    private void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        int size = project.getCanvasSize();
        double cell = CANVAS_DISPLAY_SIZE / size;
        g.clearRect(0, 0, CANVAS_DISPLAY_SIZE, CANVAS_DISPLAY_SIZE);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                g.setFill(((x + y) & 1) == 0 ? Color.web("#DCE2E8") : Color.web("#BBC5D0"));
                g.fillRect(x * cell, y * cell, cell, cell);
                if (onionSkin.isSelected() && project.getSelectedFrameIndex() > 0) {
                    int previous = project.getFrame(project.getSelectedState(), project.getSelectedFrameIndex() - 1).getPixel(x, y);
                    drawPixel(g, x, y, cell, previous, 0.28);
                }
                drawPixel(g, x, y, cell, document.getPixel(x, y), 1.0);
            }
        }
        g.setStroke(Color.rgb(50, 62, 78, 0.52));
        g.setLineWidth(1);
        for (int i = 0; i <= size; i++) {
            double coordinate = i * cell + 0.5;
            g.strokeLine(coordinate, 0, coordinate, CANVAS_DISPLAY_SIZE);
            g.strokeLine(0, coordinate, CANVAS_DISPLAY_SIZE, coordinate);
        }
        updatePreviewImage(project.getFrame(project.getSelectedState(), project.getSelectedFrameIndex()));
        status.setText(stateLabel(project.getSelectedState()) + " · 프레임 "
                + (project.getSelectedFrameIndex() + 1) + "/" + project.getFrameCount(project.getSelectedState())
                + " · " + size + "×" + size);
    }

    private static void drawPixel(GraphicsContext g, int x, int y, double cell, int argb, double alpha) {
        if ((argb >>> 24) == 0) return;
        g.setFill(Color.rgb((argb >> 16) & 255, (argb >> 8) & 255, argb & 255,
                ((argb >>> 24) & 255) / 255.0 * alpha));
        g.fillRect(x * cell, y * cell, cell, cell);
    }

    private void selectStateFromPicker() {
        PetState selected = statePicker.getValue();
        if (selected == null || updatingSelectors) return;
        syncSelectedFrame();
        project.select(selected, 0);
        loadSelectedFrame();
    }

    private void selectFrame(int index) {
        syncSelectedFrame();
        project.select(project.getSelectedState(), index);
        loadSelectedFrame();
    }

    private void refreshFrameStrip() {
        frameStrip.getChildren().clear();
        PetState state = project.getSelectedState();
        for (int index = 0; index < project.getFrameCount(state); index++) {
            Frame frame = project.getFrame(state, index);
            ImageView thumbnail = new ImageView(scaledImage(frame, 44));
            thumbnail.setSmooth(false);
            thumbnail.setFitWidth(44);
            thumbnail.setFitHeight(44);
            Label number = caption(String.valueOf(index + 1));
            VBox graphic = new VBox(3, thumbnail, number);
            graphic.setStyle("-fx-alignment: center;");
            Button button = new Button("", graphic);
            button.setId("frame-" + index);
            button.setAccessibleText(stateLabel(state) + " 프레임 " + (index + 1));
            button.setTooltip(new Tooltip("프레임 " + (index + 1) + " 편집"));
            button.setStyle(toolStyle(index == project.getSelectedFrameIndex()));
            int selected = index;
            button.setOnAction(event -> selectFrame(selected));
            frameStrip.getChildren().add(button);
        }
    }

    private void loadSelectedFrame() {
        Frame frame = project.getFrame(project.getSelectedState(), project.getSelectedFrameIndex());
        document = new PixelDocument(frame.getWidth());
        document.replace(frame.getWidth(), frame.getHeight(), frame.copyPixels());
        updatingSelectors = true;
        try {
            statePicker.setValue(project.getSelectedState());
            dimensions.setValue(project.getCanvasSize());
            fpsPicker.getValueFactory().setValue(project.getFramesPerSecond());
        } finally { updatingSelectors = false; }
        refreshFrameStrip(); redraw();
    }

    private void syncSelectedFrame() {
        if (project == null || document == null) return;
        project.updateSelected(document.toFrame());
        if (fpsPicker.getValue() != null) project.setFramesPerSecond(fpsPicker.getValue());
    }

    private void resizeProject() {
        if (updatingSelectors || dimensions.getValue() == null || dimensions.getValue() == project.getCanvasSize()) return;
        syncSelectedFrame();
        project = project.resizedCopy(dimensions.getValue());
        dirty = true;
        bulkUndo = null;
        bulkRedo = null;
        loadSelectedFrame();
    }

    private void reorderFrame(int offset) {
        syncSelectedFrame();
        project.moveSelectedFrame(offset);
        dirty = true;
        loadSelectedFrame();
    }

    private void startPreview() {
        syncSelectedFrame();
        previewStartedAt = System.nanoTime();
        previewTimer.start();
    }

    private void stopPreview() {
        previewTimer.stop();
        updatePreviewImage(project.getFrame(project.getSelectedState(), project.getSelectedFrameIndex()));
    }

    private void updatePreview(long now) {
        PetState selectedState = project.getSelectedState();
        int count = project.getFrameCount(selectedState);
        int index = (int) (((now - previewStartedAt) / 1_000_000_000.0 * project.getFramesPerSecond()) % count);
        updatePreviewImage(project.getFrame(selectedState, index));
    }

    private void updatePreviewImage(Frame frame) {
        preview.setImage(scaledImage(frame, 144));
    }

    private static WritableImage scaledImage(Frame frame, int size) {
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = frame.getPixel(x * frame.getWidth() / size, y * frame.getHeight() / size);
            }
        }
        return toImage(size, size, pixels);
    }

    private void copyFrameToClipboard() {
        ClipboardContent content = new ClipboardContent();
        content.putImage(toImage(project.getCanvasSize(), project.getCanvasSize(), document.copyPixels()));
        Clipboard.getSystemClipboard().setContent(content);
        status.setText("현재 프레임 이미지를 클립보드에 복사했습니다.");
    }

    private void exportPng() {
        Path path = chooseSave(chooser("PNG 이미지", "*.png"), "my-pet.png");
        if (path == null) return;
        try {
            BufferedImage image = bufferedImage(project.getCanvasSize(), project.getCanvasSize(), document.copyPixels());
            if (!ImageIO.write(image, "png", path.toFile())) throw new IOException("PNG writer unavailable.");
            status.setText("현재 프레임 PNG 저장: " + path.getFileName());
        } catch (IOException ex) { showError("PNG 저장 실패", ex); }
    }

    private void saveProject() {
        syncSelectedFrame();
        Path path = chooseSave(chooser("DoT Pet 프로젝트", "*.dotpet"), "my-pet.dotpet");
        if (path == null) return;
        try { writeProject(project, path); dirty = false; status.setText("애니메이션 프로젝트 저장: " + path.getFileName()); }
        catch (IOException ex) { showError("프로젝트 저장 실패", ex); }
    }

    public static void writeProject(PetAnimationProject project, Path path) throws IOException {
        StringBuilder json = new StringBuilder("{\n  \"width\": ").append(project.getCanvasSize())
                .append(",\n  \"height\": ").append(project.getCanvasSize())
                .append(",\n  \"sourceDirection\": \"LEFT\"")
                .append(",\n  \"fps\": ").append(project.getFramesPerSecond())
                .append(",\n  \"fpsByState\": {");
        for (int i = 0; i < project.getEditableStates().size(); i++) {
            PetState state = project.getEditableStates().get(i);
            if (i > 0) json.append(", ");
            json.append("\"").append(state.name()).append("\": ").append(project.getFramesPerSecond(state));
        }
        json.append("},\n  \"animations\": {\n");
        int stateCount = 0;
        for (PetState state : project.getEditableStates()) {
            if (stateCount++ > 0) json.append(",\n");
            json.append("    \"").append(state.name()).append("\": [\n");
            List<Frame> frames = project.copyAnimations().get(state);
            for (int fi = 0; fi < frames.size(); fi++) {
                if (fi > 0) json.append(",\n");
                json.append("      [");
                int[] pixels = frames.get(fi).copyPixels();
                for (int pi = 0; pi < pixels.length; pi++) {
                    if (pi > 0) json.append(",");
                    if (pi % project.getCanvasSize() == 0) json.append("\n        ");
                    json.append(String.format("\"%08X\"", pixels[pi]));
                }
                json.append("\n      ]");
            }
            json.append("\n    ]");
        }
        json.append("\n  }\n}\n");
        Files.writeString(path, json);
    }

    public static PetAnimationProject loadProject(Path path) throws IOException {
        return readProject(Files.readString(path));
    }

    private void openProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("DoT Pet 프로젝트 또는 PNG 열기");
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("DoT Pet / PNG", "*.dotpet", "*.png"),
                new FileChooser.ExtensionFilter("모든 파일", "*.*"));
        var selected = chooser.showOpenDialog(stage);
        if (selected == null) return;
        try {
            if (selected.getName().toLowerCase().endsWith(".png")) {
                BufferedImage source = ImageIO.read(selected);
                if (source == null) throw new IOException("이미지를 읽을 수 없습니다.");
                int size = source.getWidth();
                if (source.getWidth() != source.getHeight() || (size != 16 && size != 32 && size != 64))
                    throw new IOException("16×16, 32×32, 64×64 정사각 PNG만 열 수 있습니다.");
                Frame frame = new Frame(size, size, source.getRGB(0, 0, size, size, null, 0, size));
                project = new PetAnimationProject(frame);
            } else {
                project = loadProject(selected.toPath());
            }
            bulkUndo = null;
            bulkRedo = null;
            dirty = false;
            newProjectMode = project.isEmpty();
            loadSelectedFrame();
            status.setText("열기 완료: " + selected.getName());
        } catch (Exception ex) { showError("파일을 열 수 없습니다", ex); }
    }

    private static PetAnimationProject readProject(String json) throws IOException {
        int width = parseNumber(json, "width");
        int height = parseNumber(json, "height");
        if (width != height || (width != 16 && width != 32 && width != 64)) throw new IOException("지원하지 않는 캔버스입니다.");
        double fps = parseDouble(json, "fps", 8);
        EnumMap<PetState, List<Frame>> framesByState = new EnumMap<>(PetState.class);
        Matcher stateMatcher = Pattern.compile("\\\"(IDLE|WALKING|RUNNING|SLEEPING)\\\"\\s*:\\s*\\[").matcher(json);
        while (stateMatcher.find()) {
            PetState state = PetState.valueOf(stateMatcher.group(1));
            int contentStart = stateMatcher.end();
            int depth = 1;
            boolean inString = false;
            boolean escaped = false;
            int end = contentStart;
            for (; end < json.length() && depth > 0; end++) {
                char ch = json.charAt(end);
                if (inString) {
                    if (escaped) escaped = false;
                    else if (ch == '\\') escaped = true;
                    else if (ch == '"') inString = false;
                } else if (ch == '"') inString = true;
                else if (ch == '[') depth++;
                else if (ch == ']') depth--;
            }
            if (depth != 0) throw new IOException("애니메이션 배열이 손상되었습니다.");
            String body = json.substring(contentStart, end - 1);
            Matcher frameMatcher = Pattern.compile("\\[([^\\[\\]]*)\\]").matcher(body);
            List<Frame> frames = new ArrayList<>();
            while (frameMatcher.find()) {
                Matcher pixelMatcher = Pattern.compile("\\\"([0-9a-fA-F]{8})\\\"").matcher(frameMatcher.group(1));
                int[] pixels = new int[width * height];
                int index = 0;
                while (pixelMatcher.find() && index < pixels.length) pixels[index++] = (int) Long.parseLong(pixelMatcher.group(1), 16);
                if (index != pixels.length) throw new IOException(state + " 프레임 데이터 크기가 잘못되었습니다.");
                frames.add(new Frame(width, height, pixels));
            }
            if (frames.isEmpty()) throw new IOException(state + " 애니메이션이 비어 있습니다.");
            framesByState.put(state, frames);
        }

        if (framesByState.isEmpty()) { // Backward compatibility with the first single-frame editor project.
            Matcher pixelsMatcher = Pattern.compile("\\\"pixels\\\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);
            if (!pixelsMatcher.find()) throw new IOException("애니메이션 또는 픽셀 데이터를 찾을 수 없습니다.");
            Matcher pixelMatcher = Pattern.compile("\\\"([0-9a-fA-F]{8})\\\"").matcher(pixelsMatcher.group(1));
            int[] pixels = new int[width * height];
            int index = 0;
            while (pixelMatcher.find() && index < pixels.length) pixels[index++] = (int) Long.parseLong(pixelMatcher.group(1), 16);
            if (index != pixels.length) throw new IOException("픽셀 데이터 크기가 잘못되었습니다.");
            framesByState.put(PetState.IDLE, List.of(new Frame(width, height, pixels)));
        }
        Frame initial = framesByState.values().iterator().next().getFirst();
        PetAnimationProject result = new PetAnimationProject(initial);
        for (PetState state : result.getEditableStates()) {
            if (framesByState.containsKey(state)) result.replaceFrames(state, framesByState.get(state));
            double stateFps = parseDoubleProperty(json, "fpsByState", state.name(), fps);
            result.setFramesPerSecond(state, Math.max(1, Math.min(24, stateFps)));
        }
        result.select(PetState.IDLE, 0);
        return result;
    }

    private static int parseNumber(String json, String key) throws IOException {
        Matcher matcher = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) throw new IOException("프로젝트에 " + key + " 값이 없습니다.");
        return Integer.parseInt(matcher.group(1));
    }

    private static double parseDouble(String json, String key, double fallback) {
        Matcher matcher = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*([0-9.]+)").matcher(json);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : fallback;
    }

    private static double parseDoubleProperty(String json, String objectKey, String key, double fallback) {
        Matcher object = Pattern.compile("\\\"" + objectKey + "\\\"\\s*:\\s*\\{([^}]*)}").matcher(json);
        if (!object.find()) return fallback;
        Matcher value = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*([0-9.]+)").matcher(object.group(1));
        return value.find() ? Double.parseDouble(value.group(1)) : fallback;
    }

    private Path chooseSave(FileChooser chooser, String name) {
        chooser.setTitle("저장 위치 선택"); chooser.setInitialFileName(name);
        var file = chooser.showSaveDialog(stage);
        return file == null ? null : file.toPath();
    }

    private static FileChooser chooser(String description, String extension) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, extension));
        return chooser;
    }

    private static WritableImage toImage(int width, int height, int[] pixels) {
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter().setPixels(0, 0, width, height,
                javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return image;
    }

    private static BufferedImage bufferedImage(int width, int height, int[] pixels) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private void updateToolStyles(Button pencil, Button eraserButton) {
        pencil.setStyle(toolStyle(!eraser)); eraserButton.setStyle(toolStyle(eraser));
    }

    private static Button button(String text) {
        Button button = new Button(text); button.setStyle(toolStyle(false)); button.setFocusTraversable(false); return button;
    }

    private static String toolStyle(boolean selected) {
        return "-fx-background-color: " + (selected ? "#3B506C" : "#26364D")
                + "; -fx-text-fill: #F3F6FC; -fx-border-color: " + (selected ? "#9AE66E" : "#37485C")
                + "; -fx-border-width: 1.5; -fx-padding: 6 9; -fx-border-radius: 6; -fx-background-radius: 6;";
    }

    private static String textStyle() { return "-fx-text-fill: #D6DFEB;"; }

    private static String stateLabel(PetState state) {
        return switch (state) {
            case IDLE -> "대기"; case WALKING -> "걷기 ←"; case RUNNING -> "달리기 ←"; case SLEEPING -> "잠자기";
            default -> state.name();
        };
    }

    private static int toArgb(Color color) {
        return ((int) Math.round(color.getOpacity() * 255) << 24)
                | ((int) Math.round(color.getRed() * 255) << 16)
                | ((int) Math.round(color.getGreen() * 255) << 8)
                | (int) Math.round(color.getBlue() * 255);
    }

    private void showError(String title, Exception error) {
        Alert alert = new Alert(Alert.AlertType.ERROR, error.getMessage());
        alert.setTitle(title); alert.setHeaderText(title); alert.initOwner(stage); alert.show();
    }
}
