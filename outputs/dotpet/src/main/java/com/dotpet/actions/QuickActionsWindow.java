package com.dotpet.actions;

import com.dotpet.input.InputAction;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/** The quick-action UI shares execution and emergency stop with global bindings. */
public final class QuickActionsWindow implements AutoCloseable {
    private final Stage stage = new Stage();
    private final QuickActionService service;
    private final boolean ownsService;
    private boolean closed;

    public QuickActionsWindow(Stage owner, Runnable onHidden) {
        this(owner, onHidden, QuickActionService.standalone(), true);
    }

    public QuickActionsWindow(Stage owner, Runnable onHidden, QuickActionService service) {
        this(owner, onHidden, service, false);
    }

    private QuickActionsWindow(Stage owner, Runnable onHidden, QuickActionService service, boolean ownsService) {
        this.service = service;
        this.ownsService = ownsService;
        stage.initOwner(owner);
        stage.setTitle("DoT Pet · 빠른 작업");
        stage.setResizable(false);
        var settings = service.getPreferences();

        Label title = label("빠른 작업");
        title.setStyle("-fx-text-fill: #F3F6FC; -fx-font-size: 21px; -fx-font-weight: bold;");
        Label help = label("버튼이나 설정한 단축키로 실행하세요. 준비 시간에 대상 프로그램을 선택할 수 있어요.");
        help.setWrapText(true);
        Button browser = button("브라우저 주소 복사 · 5초 준비");
        browser.setOnAction(event -> { stage.setAlwaysOnTop(false); service.copyBrowserUrl(5); });
        Button file = button("파일 선택 후 경로 복사");
        file.setOnAction(event -> service.copyFilePath(stage));

        TextArea text = new TextArea(settings.savedText());
        text.setPromptText("자주 사용하는 문구");
        text.setWrapText(true);
        text.setPrefRowCount(2);
        text.setStyle("-fx-control-inner-background: #101826; -fx-text-fill: #F3F6FC;");
        Spinner<Integer> interval = new Spinner<>(AutoClickService.MIN_INTERVAL_MILLIS,
                AutoClickService.MAX_INTERVAL_MILLIS, settings.clickIntervalMillis(), 100);
        Spinner<Integer> count = new Spinner<>(1, AutoClickService.MAX_CLICKS, settings.clickCount());
        interval.setEditable(true); count.setEditable(true);
        interval.setPrefWidth(150); count.setPrefWidth(150);
        CheckBox infinite = new CheckBox("무한 반복 · 중지할 때까지 클릭");
        infinite.setId("auto-infinite");
        infinite.setSelected(settings.infiniteClick());
        infinite.setStyle("-fx-text-fill: #F4D6A1;");

        Button copy = button("문구 복사");
        copy.setOnAction(event -> saveThen(text, interval, count, infinite, service::copyText));
        Button paste = button("붙여넣기 · 3초 준비");
        paste.setOnAction(event -> saveThen(text, interval, count, infinite, () -> {
            stage.setAlwaysOnTop(false); service.pasteText(3);
        }));
        Button save = button("문구·클릭 설정 저장");
        save.setId("quick-save");
        save.setOnAction(event -> saveThen(text, interval, count, infinite, () -> service.report("문구와 클릭 설정을 저장했어요.")));
        Button start = button("오토 클릭 시작 · 3초 준비");
        start.setId("auto-start");
        start.setOnAction(event -> saveThen(text, interval, count, infinite, () -> {
            stage.setAlwaysOnTop(false); service.startAutoClick();
        }));
        Button stop = button("■ 긴급 중지");
        stop.setId("auto-stop");
        stop.setStyle("-fx-background-color: #914B60; -fx-text-fill: white; -fx-padding: 9 14;");
        stop.setOnAction(event -> service.stop());
        Label emergency = label("창이 뒤에 있어도 " + settings.hotkeys().get(InputAction.STOP_AUTO_CLICK) + "로 중지합니다.\n중지 키를 확보하지 못하면 오토 클릭은 시작하지 않아요.");
        emergency.setWrapText(true);
        emergency.setStyle("-fx-text-fill: #F0C2CD; -fx-font-size: 12px;");

        for (Button action : new Button[] {browser, file, copy, paste, save, start}) action.disableProperty().bind(service.busyProperty());
        text.disableProperty().bind(service.busyProperty());
        interval.disableProperty().bind(service.busyProperty());
        count.setId("auto-count"); interval.setId("auto-interval");
        count.disableProperty().bind(service.busyProperty().or(infinite.selectedProperty()));
        infinite.disableProperty().bind(service.busyProperty());
        Label status = label("");
        status.setWrapText(true);
        status.setMinHeight(42);
        status.textProperty().bind(service.statusProperty());
        VBox content = new VBox(12, title, help,
                section("주소·파일", browser, file),
                section("등록 문구", text, new HBox(8, copy, paste)),
                section("오토 클릭", new HBox(10, new VBox(4, label("간격 (ms)"), interval),
                        new VBox(4, label("횟수"), count)), infinite, new HBox(8, start, stop), emergency),
                save, status);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #1A2233;");
        stage.setScene(new Scene(content, 470, 740, Color.web("#1A2233")));
        stage.setOnHidden(event -> {
            close();
            if (onHidden != null) onHidden.run();
        });
    }

    public Stage getStage() { return stage; }
    public void show() { stage.show(); stage.toFront(); }

    private void saveThen(TextArea text, Spinner<Integer> interval, Spinner<Integer> count, CheckBox infinite, Runnable action) {
        CompletableFuture<Void> saving;
        try {
            int milliseconds = Integer.parseInt(interval.getEditor().getText().trim());
            int clicks = infinite.isSelected() ? count.getValue() : Integer.parseInt(count.getEditor().getText().trim());
            saving = service.updatePreferences(text.getText(), milliseconds, clicks, infinite.isSelected());
        } catch (RuntimeException error) {
            service.report("설정값을 확인하세요: " + error.getMessage());
            return;
        }
        saving.whenComplete((ignored, failure) -> Platform.runLater(() -> {
            if (closed) return;
            if (failure != null) {
                Throwable root = failure; while (root.getCause() != null) root = root.getCause();
                service.report("설정을 저장하지 못했어요: " + root.getMessage());
            } else action.run();
        }));
    }

    private static Label label(String text) {
        Label label = new Label(text); label.setStyle("-fx-text-fill: #C5D0E0;"); return label;
    }

    private static Button button(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: #33435B; -fx-text-fill: #F3F6FC; -fx-padding: 8 10; -fx-background-radius: 6;");
        return button;
    }

    private static VBox section(String text, javafx.scene.Node... controls) {
        VBox box = new VBox(8, label(text)); box.getChildren().addAll(controls);
        box.setStyle("-fx-background-color: #222E43; -fx-background-radius: 8;");
        box.setPadding(new Insets(10)); return box;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (ownsService) service.close(); else service.stop();
    }
}
