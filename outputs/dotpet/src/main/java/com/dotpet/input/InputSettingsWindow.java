package com.dotpet.input;

import com.dotpet.settings.InputSettings;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** User-controlled global bindings; key capture only occurs inside the focused field. */
public final class InputSettingsWindow {
    private final Stage stage = new Stage();
    private final CheckBox hotkeysEnabled = new CheckBox("다른 프로그램에서도 단축키 사용");
    private final CheckBox gesturesEnabled = new CheckBox("휠 버튼 드래그 제스처 사용");
    private final EnumMap<InputAction, Hotkey> bindings = new EnumMap<>(InputAction.class);
    private final EnumMap<InputAction, TextField> fields = new EnumMap<>(InputAction.class);
    private final EnumMap<InputAction, CheckBox> pasteOptions = new EnumMap<>(InputAction.class);
    private final EnumMap<GestureDirection, ComboBox<InputAction>> gestureChoices = new EnumMap<>(GestureDirection.class);
    private final Spinner<Integer> threshold = new Spinner<>(32, 200, 70, 10);
    private final Label feedback = label("");
    private final Button save = button("저장하고 닫기");
    private final Supplier<InputSettings> currentSettings;
    private final Function<InputSettings, CompletableFuture<Void>> applySettings;
    private final WindowsInputService input;
    private final Consumer<String> messageHandler;
    private boolean closed;

    public InputSettingsWindow(Stage owner, Supplier<InputSettings> currentSettings,
            Function<InputSettings, CompletableFuture<Void>> applySettings, WindowsInputService input,
            String loadNotice, Consumer<String> messageHandler, Runnable onHidden) {
        this.currentSettings = currentSettings;
        this.applySettings = applySettings;
        this.input = input;
        this.messageHandler = messageHandler;
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("DoT Pet · 단축키와 제스처");
        InputSettings initial = currentSettings.get();
        bindings.putAll(initial.hotkeys());
        hotkeysEnabled.setSelected(initial.hotkeysEnabled());
        gesturesEnabled.setSelected(initial.gesturesEnabled());
        hotkeysEnabled.setId("hotkeys-enabled");
        gesturesEnabled.setId("gestures-enabled");
        hotkeysEnabled.setStyle(textStyle()); gesturesEnabled.setStyle(textStyle());
        threshold.getValueFactory().setValue(initial.gestureThreshold());
        threshold.setPrefWidth(90);

        Label title = label("단축키와 제스처");
        title.setStyle("-fx-text-fill: #F3F6FC; -fx-font-size: 21; -fx-font-weight: bold;");
        Label introduction = label("원하는 기능을 연결한 뒤 위 스위치를 켜세요. 설정 중에는 전역 동작이 잠시 멈춥니다.");
        introduction.setWrapText(true);
        VBox header = new VBox(10, title, introduction, hotkeysEnabled, gesturesEnabled);
        header.setPadding(new Insets(0, 0, 14, 0));

        GridPane keyGrid = new GridPane(); keyGrid.setHgap(10); keyGrid.setVgap(8);
        int row = 0;
        for (InputAction action : InputAction.values()) {
            if (action == InputAction.NONE) continue;
            Label name = label(action.getLabel()); name.setMinWidth(190);
            TextField key = new TextField(bindings.containsKey(action) ? bindings.get(action).toString() : "사용 안 함");
            key.setEditable(false); key.setPrefWidth(190);
            key.setId("binding-" + action.name());
            key.setAccessibleText(action.getLabel() + " 단축키");
            key.setTooltip(new Tooltip("이 칸을 클릭한 뒤 원하는 키 조합을 누르세요."));
            key.setOnKeyPressed(event -> {
                event.consume();
                if (event.getCode().isModifierKey()) return;
                if (event.getCode() == KeyCode.ESCAPE) { key.getParent().requestFocus(); return; }
                if (event.getCode() == KeyCode.BACK_SPACE || event.getCode() == KeyCode.DELETE) { clearBinding(action); return; }
                try {
                    if (event.isMetaDown()) throw new IllegalArgumentException("Windows 키가 포함된 조합은 지정할 수 없어요.");
                    int modifiers = (event.isControlDown() ? Hotkey.CTRL : 0) | (event.isAltDown() ? Hotkey.ALT : 0) | (event.isShiftDown() ? Hotkey.SHIFT : 0);
                    Hotkey combo = new Hotkey(modifiers, event.getCode().getCode());
                    bindings.put(action, combo); key.setText(combo.toString());
                    if (validate()) feedback.setText(combo.localConflictHint().isBlank() ? "키 조합을 지정했어요. 저장할 때 Windows 등록 충돌도 확인합니다." : combo.localConflictHint());
                } catch (IllegalArgumentException error) { feedback.setText(error.getMessage()); }
            });
            fields.put(action, key);
            Button clear = button("지우기"); clear.setDisable(action == InputAction.STOP_AUTO_CLICK);
            clear.setOnAction(event -> clearBinding(action));
            keyGrid.addRow(row++, name, key, clear);
            if (action == InputAction.PASTE_TEXT || action == InputAction.PASTE_CLIPBOARD) {
                CheckBox enter = new CheckBox("붙여넣은 뒤 Enter 누르기");
                enter.setId("paste-enter-" + action.name());
                enter.setStyle(textStyle());
                enter.setSelected(action == InputAction.PASTE_TEXT ? initial.pasteTextEnter() : initial.pasteClipboardEnter());
                enter.setTooltip(new Tooltip("이 기능에 연결한 단축키와 제스처에 함께 적용됩니다."));
                pasteOptions.put(action, enter);
                keyGrid.add(enter, 1, row++, 2, 1);
            }
        }
        Label keyHelp = label("키 칸 클릭 → 키 조합 누르기. Ctrl+Alt 조합을 권장해요. 긴급 중지는 필수입니다.\n붙여넣기의 Enter 체크는 각 기능별로 저장되며 제스처에도 적용됩니다.\n전체 선택은 Ctrl+A, 전체 선택 후 복사는 Ctrl+A 다음 Ctrl+C를 보냅니다.\n앱 내부 단축키까지 모두 검사할 수는 없으며, Windows 전역 단축키 충돌은 저장 시 안내합니다.");
        keyHelp.setWrapText(true);

        GridPane gestureGrid = new GridPane(); gestureGrid.setHgap(10); gestureGrid.setVgap(8);
        row = 0;
        for (GestureDirection direction : GestureDirection.values()) {
            ComboBox<InputAction> choice = new ComboBox<>();
            choice.getItems().setAll(InputAction.values());
            choice.setValue(initial.gestures().getOrDefault(direction, InputAction.NONE));
            choice.setMaxWidth(Double.MAX_VALUE);
            choice.setId("gesture-" + direction.name());
            gestureChoices.put(direction, choice);
            gestureGrid.addRow(row++, label("휠 + " + direction.getLabel()), choice);
        }
        Label gestureHelp = label("휠 버튼을 누른 채 지정 거리 이상 드래그한 뒤 놓으면 실행합니다.\n짧은 동작·대각선·펫 드래그 중인 동작은 무시합니다. 일부 앱의 휠 자동 스크롤과 함께 동작할 수 있어요.");
        gestureHelp.setWrapText(true);
        Label privacy = label("일반 타이핑 내용과 클립보드를 기록하지 않습니다. 제스처는 휠 드래그의 시작·끝 좌표만 잠시 사용합니다.\n단축키를 꺼도 직접 시작한 오토 클릭이 실행되는 동안에는 긴급 중지 키만 별도로 동작합니다.\n관리자 권한으로 실행된 앱에서는 자동 입력이 제한될 수 있습니다. DoT Pet은 권한을 자동으로 높이지 않습니다.");
        privacy.setWrapText(true);
        privacy.setStyle("-fx-text-fill: #9AAEC9; -fx-font-size: 11px;");
        VBox form = new VBox(12, section("단축키", keyHelp, keyGrid),
                section("마우스 제스처", gestureGrid, new HBox(9, label("필요한 드래그 거리 (px)"), threshold), gestureHelp),
                label("문구·오토 클릭 간격·횟수는 빠른 작업 창에서 저장할 수 있어요."), privacy);
        ScrollPane scroll = new ScrollPane(form); scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #111824; -fx-background-color: #111824;");

        Button defaults = button("기본 조합으로");
        defaults.setOnAction(event -> reset());
        Button disableAll = button("전역 입력 모두 끄기");
        disableAll.setId("disable-global-input");
        disableAll.setOnAction(event -> {
            InputSettings current = currentSettings.get();
            hotkeysEnabled.setSelected(false); gesturesEnabled.setSelected(false);
            submit(new InputSettings(false, false, current.hotkeys(), current.gestures(), current.gestureThreshold(),
                    current.savedText(), current.clickIntervalMillis(), current.clickCount(), current.infiniteClick(),
                    current.pasteTextEnter(), current.pasteClipboardEnter()));
        });
        save.setId("save-input-settings");
        save.setOnAction(event -> submit());
        feedback.setWrapText(true); feedback.setMinHeight(40);
        feedback.setStyle("-fx-text-fill: #B8DAA0;");
        feedback.setText(loadNotice.isBlank() ? "현재 저장값: 단축키 " + (initial.hotkeysEnabled() ? "켜짐" : "꺼짐")
                + " · 제스처 " + (initial.gesturesEnabled() ? "켜짐" : "꺼짐") + ". 저장한 설정은 다음 실행에도 유지됩니다." : loadNotice);
        VBox footer = new VBox(8, feedback, new HBox(8, defaults, disableAll, save));
        footer.setPadding(new Insets(12, 0, 0, 0));
        BorderPane root = new BorderPane(scroll, header, null, footer, null);
        root.setPadding(new Insets(18)); root.setStyle("-fx-background-color: #111824;");
        stage.setScene(new Scene(root, 660, 760, Color.web("#111824")));
        stage.setMinWidth(620); stage.setMinHeight(600);
        root.setDisable(true);
        input.suspendForEditing(true).whenComplete((ignored, failure) -> Platform.runLater(() -> {
            if (closed) return;
            root.setDisable(false);
            if (failure != null) feedback.setText("입력 준비 실패: " + message(failure));
        }));
        stage.setOnHidden(event -> {
            if (closed) return; closed = true;
            input.suspendForEditing(false).whenComplete((ignored, failure) -> {
                if (failure != null) messageHandler.accept("전역 입력 재개 실패: " + message(failure));
            });
            onHidden.run();
        });
    }

    public Stage getStage() { return stage; }
    public void show() { stage.show(); stage.toFront(); }

    private void clearBinding(InputAction action) {
        if (action == InputAction.STOP_AUTO_CLICK) { feedback.setText("긴급 중지 단축키는 지울 수 없어요. 다른 키 조합으로 변경하세요."); return; }
        bindings.remove(action); fields.get(action).setText("사용 안 함"); validate();
    }

    private InputSettings draft() {
        InputSettings current = currentSettings.get();
        EnumMap<GestureDirection, InputAction> gestures = new EnumMap<>(GestureDirection.class);
        gestureChoices.forEach((direction, choice) -> gestures.put(direction, choice.getValue()));
        return new InputSettings(hotkeysEnabled.isSelected(), gesturesEnabled.isSelected(), bindings, gestures,
                threshold.getValue(), current.savedText(), current.clickIntervalMillis(), current.clickCount(), current.infiniteClick(),
                pasteOptions.get(InputAction.PASTE_TEXT).isSelected(), pasteOptions.get(InputAction.PASTE_CLIPBOARD).isSelected());
    }

    private boolean validate() {
        try { draft(); save.setDisable(false); return true; }
        catch (IllegalArgumentException error) { save.setDisable(true); feedback.setText(error.getMessage()); return false; }
    }

    private void reset() {
        InputSettings defaults = InputSettings.defaults();
        hotkeysEnabled.setSelected(false); gesturesEnabled.setSelected(false);
        bindings.clear(); bindings.putAll(defaults.hotkeys());
        fields.forEach((action, field) -> field.setText(bindings.containsKey(action) ? bindings.get(action).toString() : "사용 안 함"));
        gestureChoices.forEach((direction, choice) -> choice.setValue(defaults.gestures().get(direction)));
        threshold.getValueFactory().setValue(defaults.gestureThreshold());
        pasteOptions.values().forEach(option -> option.setSelected(false));
        validate(); feedback.setText("기본 조합으로 되돌렸어요. 저장하면 적용됩니다.");
    }

    private void submit() {
        if (!validate()) return;
        submit(draft());
    }

    private void submit(InputSettings candidate) {
        stage.getScene().getRoot().setDisable(true);
        feedback.setText("Windows 단축키 등록 상태를 확인하고 저장하는 중이에요…");
        applySettings.apply(candidate).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (closed) return;
            if (error == null) { messageHandler.accept("단축키·제스처 설정을 저장했어요."); stage.close(); }
            else {
                input.suspendForEditing(true);
                stage.getScene().getRoot().setDisable(false);
                feedback.setText(message(error));
            }
        }));
    }

    private static String message(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error.getMessage(); }
    private static Label label(String text) { Label label = new Label(text); label.setStyle(textStyle()); return label; }
    private static String textStyle() { return "-fx-text-fill: #C5D0E0;"; }
    private static Button button(String text) { Button button = new Button(text); button.setStyle("-fx-background-color: #334A63; -fx-text-fill: #F3F6FC; -fx-padding: 8 11;"); return button; }
    private static VBox section(String title, javafx.scene.Node... controls) {
        Label heading = label(title); heading.setStyle("-fx-text-fill: #A4D98D; -fx-font-weight: bold;");
        VBox box = new VBox(9, heading); box.getChildren().addAll(controls); box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: #1C293B; -fx-background-radius: 8;"); return box;
    }
}
