package com.dotpet.actions;

import com.dotpet.settings.SearchSettings;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** A compact prompt anchored to the pet; search terms are never stored. */
public final class SearchWindow {
    private final Stage stage = new Stage();
    private final TextField query = new TextField();
    private final ComboBox<SearchEngine> engine = new ComboBox<>();
    private final Stage owner;
    private final Label status = label("검색 설정을 불러오는 중…");
    private final VBox content;
    private final WebSearchService service;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> new Thread(task, "dotpet-search"));
    private boolean loadingStarted, closed;

    public SearchWindow(Stage owner, WebSearchService service, Runnable onHidden) {
        this.service = service;
        this.owner = owner;
        stage.initOwner(owner); stage.setTitle("DoT Pet · 검색");
        stage.initStyle(StageStyle.TRANSPARENT); stage.setResizable(false);
        query.setId("search-query"); query.setPromptText("검색어를 입력하고 Enter");
        query.setOnAction(event -> submit());
        engine.getItems().setAll(SearchEngine.values()); engine.setValue(SearchEngine.GOOGLE); engine.setId("search-engine");
        engine.setPrefWidth(130); engine.setAccessibleText("검색엔진");
        query.setAccessibleText("검색어, Enter로 검색, Esc로 닫기");
        query.setTooltip(new Tooltip("Enter로 검색 · Esc로 닫기"));
        Button close = new Button("×"); close.setId("search-close"); close.setAccessibleText("검색 닫기");
        close.setStyle("-fx-background-color: transparent; -fx-text-fill: #C5D0E0; -fx-font-size: 18;");
        close.setOnAction(event -> stage.close());
        HBox row = new HBox(8, engine, query, close); row.setAlignment(javafx.geometry.Pos.CENTER);
        HBox.setHgrow(query, Priority.ALWAYS);
        status.setWrapText(true); status.setId("search-status");
        status.managedProperty().bind(status.textProperty().isNotEmpty());
        status.visibleProperty().bind(status.managedProperty());
        content = new VBox(7, row, status);
        content.setPadding(new Insets(10)); content.setPrefWidth(440);
        content.setStyle("-fx-background-color: #111824; -fx-background-radius: 12; -fx-border-color: #61778D; -fx-border-radius: 12;");
        content.setDisable(true);
        stage.setScene(new Scene(content, Color.TRANSPARENT));
        stage.heightProperty().addListener((observable, before, after) -> { if (stage.isShowing()) PetPromptPosition.place(stage, owner); });
        status.textProperty().addListener((observable, before, after) -> Platform.runLater(() -> { if (!closed) stage.sizeToScene(); }));
        stage.getScene().setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ESCAPE) stage.close(); });
        stage.setOnHidden(event -> { closed = true; worker.shutdown(); query.clear(); onHidden.run(); });
    }

    public Stage getStage() { return stage; }
    public void show() {
        stage.sizeToScene(); stage.show(); PetPromptPosition.place(stage, owner); stage.toFront();
        if (loadingStarted) return;
        loadingStarted = true;
        CompletableFuture.supplyAsync(service::loadSettings, worker).whenComplete((settings, failure) -> Platform.runLater(() -> {
            if (closed) return;
            SearchSettings restored = failure == null ? settings : SearchSettings.defaults();
            engine.setValue(restored.engine());
            status.setText(failure == null ? service.getLoadNotice() : "기본 검색 설정으로 열었어요.");
            content.setDisable(false); query.requestFocus();
        }));
    }

    private void submit() {
        if (content.isDisabled() || closed) return;
        SearchSettings preferences = new SearchSettings(engine.getValue());
        try {
            preferences.engine().searchUri(query.getText());
        } catch (IllegalArgumentException failure) { status.setText(failure.getMessage()); query.requestFocus(); return; }
        String text = query.getText(); content.setDisable(true); status.setText("브라우저에서 검색을 여는 중…");
        CompletableFuture.runAsync(() -> { try { service.search(text, preferences); }
                catch (Exception failure) { throw new CompletionException(failure); } }, worker)
                .whenComplete((ignored, failure) -> Platform.runLater(() -> {
                    if (closed) return;
                    if (failure == null) stage.close();
                    else { Throwable cause = failure; while (cause.getCause() != null) cause = cause.getCause();
                        status.setText("검색을 열지 못했어요: " + cause.getMessage()); content.setDisable(false); }
                }));
    }

    private static Label label(String text) { Label label = new Label(text); label.setStyle("-fx-text-fill: #C5D0E0;"); return label; }
}
