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
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** A small search prompt with remembered engine/browser choices and no history. */
public final class SearchWindow {
    private final Stage stage = new Stage();
    private final TextField query = new TextField();
    private final ComboBox<SearchEngine> engine = new ComboBox<>();
    private final CheckBox customBrowser = new CheckBox("브라우저 직접 지정");
    private final TextField executable = new TextField();
    private final Label status = label("검색 설정을 불러오는 중…");
    private final VBox content;
    private final WebSearchService service;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> new Thread(task, "dotpet-search"));
    private boolean loadingStarted, closed;

    public SearchWindow(Stage owner, WebSearchService service, Runnable onHidden) {
        this.service = service;
        stage.initOwner(owner); stage.setTitle("DoT Pet · 검색");
        query.setId("search-query"); query.setPromptText("검색어를 입력하고 Enter");
        query.setOnAction(event -> submit());
        engine.getItems().setAll(SearchEngine.values()); engine.setValue(SearchEngine.GOOGLE); engine.setId("search-engine");
        customBrowser.setId("search-custom-browser"); customBrowser.setStyle("-fx-text-fill: #D7E3F4;");
        executable.setEditable(false); executable.setId("search-browser-path"); executable.setPromptText("브라우저 .exe 선택");
        Button choose = button("찾아보기", "search-choose-browser"); choose.setOnAction(event -> chooseBrowser());
        HBox browserRow = new HBox(8, executable, choose); HBox.setHgrow(executable, Priority.ALWAYS);
        browserRow.disableProperty().bind(customBrowser.selectedProperty().not());
        Button search = button("검색", "search-submit"); search.setOnAction(event -> submit());
        Button close = button("닫기", "search-close"); close.setOnAction(event -> stage.close());
        Label help = label("검색할 때 검색엔진과 브라우저 선택을 기억합니다.\n직접 지정을 끄면 Windows 기본 브라우저를 사용합니다.\n결과는 브라우저 설정에 따라 탭 또는 창으로 열립니다. 검색어는 저장하지 않습니다.");
        help.setWrapText(true); status.setWrapText(true); status.setId("search-status");
        content = new VBox(12, label("무엇을 찾아볼까요?"), query, new HBox(10, label("검색엔진"), engine),
                customBrowser, browserRow, help, new HBox(8, search, close), status);
        content.setPadding(new Insets(20)); content.setStyle("-fx-background-color: #111824;"); content.setDisable(true);
        stage.setScene(new Scene(content, 540, 340, Color.web("#111824"))); stage.setMinWidth(480); stage.setMinHeight(340);
        stage.getScene().setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ESCAPE) stage.close(); });
        stage.setOnHidden(event -> { closed = true; worker.shutdown(); query.clear(); onHidden.run(); });
    }

    public Stage getStage() { return stage; }
    public void show() {
        stage.show(); stage.toFront();
        if (loadingStarted) return;
        loadingStarted = true;
        CompletableFuture.supplyAsync(service::loadSettings, worker).whenComplete((settings, failure) -> Platform.runLater(() -> {
            if (closed) return;
            SearchSettings restored = failure == null ? settings : SearchSettings.defaults();
            engine.setValue(restored.engine()); executable.setText(restored.browserExecutable());
            customBrowser.setSelected(!restored.browserExecutable().isEmpty());
            status.setText(failure == null ? service.getLoadNotice() : "기본 검색 설정으로 열었어요.");
            content.setDisable(false); query.requestFocus();
        }));
    }

    private void chooseBrowser() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("설치된 브라우저 실행 파일 선택");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Windows 실행 파일", "*.exe"));
        var file = chooser.showOpenDialog(stage); if (file != null) executable.setText(file.getAbsolutePath());
    }

    private void submit() {
        if (content.isDisabled() || closed) return;
        SearchSettings preferences = new SearchSettings(engine.getValue(), customBrowser.isSelected() ? executable.getText() : "");
        try {
            preferences.engine().searchUri(query.getText());
            if (customBrowser.isSelected() && preferences.browserExecutable().isEmpty()) throw new IllegalArgumentException("브라우저 실행 파일을 선택하세요.");
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
    private static Button button(String text, String id) { Button button = new Button(text); button.setId(id);
        button.setStyle("-fx-background-color: #334A63; -fx-text-fill: #F3F6FC; -fx-padding: 8 12;"); return button; }
}
