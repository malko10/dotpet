package com.dotpet.actions;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Windows Recycle Bin controls; emptying always requires a separate confirmation. */
public final class RecycleBinWindow {
    private final Stage stage = new Stage();
    private final RecycleBinService service;
    private final Label count = label("휴지통을 확인하는 중…");
    private final Label status = label("");
    private final Button empty = button("휴지통 비우기", "recycle-empty");
    private final HBox actions;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> new Thread(task, "dotpet-recycle-window"));
    private boolean closed, started, busy, confirming;
    private RecycleBinService.BinInfo lastInfo;

    public RecycleBinWindow(Stage owner, RecycleBinService service, Runnable onHidden) {
        this.service = service; stage.initOwner(owner); stage.setTitle("DoT Pet · 휴지통");
        count.setId("recycle-count"); status.setId("recycle-status"); status.setWrapText(true);
        Label help = label("파일을 펫 위로 끌어놓으면 Windows 휴지통으로 보냅니다.\n로컬 고정 드라이브의 일반 파일만 지원합니다.\n폴더·심볼릭 링크·네트워크·이동식 드라이브는 보내지 않습니다.\n복원하려면 Windows 휴지통을 열어 주세요."); help.setWrapText(true);
        Button select = button("파일 선택해서 보내기", "recycle-select"); select.setOnAction(event -> selectFiles());
        Button open = button("휴지통 열기", "recycle-open"); open.setOnAction(event -> run("휴지통을 여는 중…", () -> {service.open(); return true;}, ignored -> status.setText("Windows 휴지통을 열었어요.")));
        empty.setOnAction(event -> confirmEmpty());
        Button refresh = button("새로고침", "recycle-refresh"); refresh.setOnAction(event -> refresh());
        actions = new HBox(8, select, open, refresh);
        Button close = button("닫기", "recycle-close"); close.setOnAction(event -> stage.close());
        VBox root = new VBox(16, label("펫에게 파일 정리를 맡겨 보세요"), help, count, actions,
                new HBox(8, empty, close), status);
        root.setPadding(new Insets(22)); root.setStyle("-fx-background-color: #111824;");
        stage.setScene(new Scene(root, 560, 340, Color.web("#111824"))); stage.setMinWidth(540); stage.setMinHeight(340);
        stage.getScene().setOnKeyPressed(event -> {if (event.getCode() == KeyCode.ESCAPE) stage.close();});
        stage.setOnHidden(event -> {closed = true; worker.shutdown(); onHidden.run();});
    }

    public Stage getStage() { return stage; }
    public void show() {stage.show(); stage.toFront(); if (!started) {started = true; refresh();}}
    public boolean acceptsPetDrop() { return !closed && !busy && !confirming; }
    public void beginPetDrop() { busy = true; updateControls(); status.setText("펫이 파일을 휴지통으로 보내는 중…"); }
    public void finishPetDrop(String message) { if (closed) return; busy = false; status.setText(message); refresh(); }

    private void selectFiles() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("휴지통으로 보낼 파일 선택");
        var files = chooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) sendFiles(files.stream().map(java.io.File::toPath).toList());
    }

    void sendFiles(List<Path> paths) {
        run("파일을 휴지통으로 보내는 중…", () -> service.recycle(paths), result -> {
            status.setText(result.summary()); refresh();
        });
    }

    private void refresh() {
        run(null, service::query, info -> {
            lastInfo = info;
            count.setText(String.format("Windows 휴지통 · %,d개 항목 · %.1f MB", info.items(), info.bytes() / 1048576.0));
            empty.setDisable(info.items() == 0);
        });
    }

    private void confirmEmpty() {
        if (lastInfo == null || lastInfo.items() == 0 || empty.isDisabled()) return;
        ButtonType erase = new ButtonType("모두 비우기", ButtonBar.ButtonData.OK_DONE);
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "펫으로 보낸 파일뿐 아니라 Windows 휴지통의 모든 항목이 영구 삭제됩니다.\n비운 항목은 휴지통에서 복원할 수 없습니다.", erase, ButtonType.CANCEL);
        confirmation.initOwner(stage); confirmation.setTitle("휴지통을 비울까요?");
        confirmation.setHeaderText(String.format("휴지통의 %,d개 항목을 모두 비울까요?", lastInfo.items()));
        confirmation.getDialogPane().setId("recycle-empty-confirmation");
        var eraseButton = (Button) confirmation.getDialogPane().lookupButton(erase);
        eraseButton.setId("recycle-confirm-empty"); eraseButton.setDefaultButton(false);
        var cancelButton = (Button) confirmation.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setId("recycle-cancel-empty"); cancelButton.setDefaultButton(true);
        confirmation.setOnShown(event -> cancelButton.requestFocus());
        confirming = true; updateControls();
        confirmation.setOnHidden(event -> {
            confirming = false; updateControls();
            if (!closed && confirmation.getResult() == erase)
                run("휴지통을 비우는 중…", () -> {service.empty(true); return true;}, ignored -> {status.setText("휴지통을 비웠어요."); refresh();});
        });
        confirmation.show();
    }

    private <T> void run(String message, Callable<T> operation, Consumer<T> done) {
        if (closed || busy || confirming) return;
        busy = true; updateControls(); if (message != null) status.setText(message);
        CompletableFuture.supplyAsync(() -> {try {return operation.call();} catch (Exception failure) {throw new CompletionException(failure);}}, worker)
                .whenComplete((value, failure) -> Platform.runLater(() -> {
                    if (closed) return;
                    busy = false; updateControls();
                    if (failure == null) done.accept(value);
                    else {Throwable cause = failure; while (cause.getCause() != null) cause = cause.getCause();
                        status.setText("작업을 마치지 못했어요: " + cause.getMessage());}
                }));
    }

    private void updateControls() {
        actions.setDisable(busy || confirming);
        empty.setDisable(busy || confirming || lastInfo == null || lastInfo.items() == 0);
    }

    private static Label label(String text) {Label label = new Label(text); label.setStyle("-fx-text-fill: #C5D0E0;"); return label;}
    private static Button button(String text, String id) {Button button = new Button(text); button.setId(id);
        button.setStyle("-fx-background-color: #334A63; -fx-text-fill: #F3F6FC; -fx-padding: 8 12;"); return button;}
}
