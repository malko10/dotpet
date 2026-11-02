package com.dotpet.actions;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Compact bin status and explicit opt-in; merely enabling the option never empties the bin. */
public final class RecycleBinWindow {
    private final Stage stage = new Stage();
    private final RecycleBinService service;
    private final Label count = label("휴지통을 확인하는 중…");
    private final Label status = label("");
    private final CheckBox autoEmpty = new CheckBox("펫에 파일을 넣은 뒤 휴지통 전체 바로 비우기");
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> new Thread(task, "dotpet-recycle-window"));
    private final Timeline refreshTimer = new Timeline(new KeyFrame(Duration.seconds(3), event -> refresh()));
    private boolean closed, started, busy, ready;

    public RecycleBinWindow(Stage owner, RecycleBinService service, Runnable onHidden) {
        this.service = service; stage.initOwner(owner); stage.setTitle("DoT Pet · 휴지통");
        count.setId("recycle-count"); count.setStyle("-fx-text-fill: #E0F3EC; -fx-font-size: 18; -fx-font-weight: bold;");
        status.setId("recycle-status"); status.setWrapText(true);
        autoEmpty.setId("recycle-auto-empty"); autoEmpty.setStyle("-fx-text-fill: #D7E3F4;");
        autoEmpty.setDisable(true);
        autoEmpty.setOnAction(event -> saveOption());
        Label help = label("체크하면 기존 항목까지 모두 영구 삭제됩니다.\n체크한 이후 펫에 파일을 놓을 때부터 적용됩니다.");
        help.setWrapText(true); help.setStyle("-fx-text-fill: #E6BE94; -fx-font-size: 12;");
        VBox root = new VBox(12, count, autoEmpty, help, status);
        root.setPadding(new Insets(18)); root.setStyle("-fx-background-color: #111824;");
        root.setPrefWidth(390);
        stage.setScene(new Scene(root, Color.web("#111824"))); stage.setResizable(false);
        status.textProperty().addListener((observable, before, after) -> Platform.runLater(() -> {
            if (!closed) { stage.sizeToScene(); PetPromptPosition.place(stage, owner); }
        }));
        stage.getScene().setOnKeyPressed(event -> {if (event.getCode() == KeyCode.ESCAPE) stage.close();});
        stage.setOnHidden(event -> {closed = true; refreshTimer.stop(); worker.shutdown(); onHidden.run();});
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
    }

    public Stage getStage() { return stage; }
    public void show() {
        stage.show(); PetPromptPosition.place(stage, (Stage) stage.getOwner()); stage.toFront();
        if (started) return;
        started = true;
        run(service::isAutoEmpty, enabled -> {
            autoEmpty.setSelected(enabled); ready = true; updateControls(); refresh(); refreshTimer.play();
        });
    }
    public boolean acceptsPetDrop() { return !closed && ready && !busy; }
    public void beginPetDrop() { busy = true; updateControls(); status.setText("펫이 파일을 처리하는 중…"); }
    public void finishPetDrop(String message) { if (closed) return; busy = false; status.setText(message); refresh(); }

    private void saveOption() {
        boolean enabled = autoEmpty.isSelected();
        run(() -> {service.setAutoEmpty(enabled); return enabled;}, saved -> {
            status.setText(saved ? "다음 파일부터 전체 휴지통을 비웁니다." : "파일을 휴지통에 보관합니다.");
        }, () -> autoEmpty.setSelected(!enabled));
    }

    private void refresh() {
        if (!ready) return;
        run(service::query, info -> count.setText(String.format("휴지통 · %,d개 항목", info.items())));
    }

    private <T> void run(Callable<T> operation, Consumer<T> done) { run(operation, done, () -> { }); }
    private <T> void run(Callable<T> operation, Consumer<T> done, Runnable failed) {
        if (closed || busy) return;
        busy = true; updateControls();
        CompletableFuture.supplyAsync(() -> {try {return operation.call();} catch (Exception failure) {throw new CompletionException(failure);}}, worker)
                .whenComplete((value, failure) -> Platform.runLater(() -> {
                    if (closed) return;
                    busy = false; updateControls();
                    if (failure == null) done.accept(value);
                    else {failed.run(); Throwable cause = failure; while (cause.getCause() != null) cause = cause.getCause();
                        status.setText("작업을 마치지 못했어요: " + cause.getMessage());}
                }));
    }

    private void updateControls() { autoEmpty.setDisable(busy || !ready); }
    private static Label label(String text) {Label label = new Label(text); label.setStyle("-fx-text-fill: #C5D0E0;"); return label;}
}
