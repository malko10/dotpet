package com.dotpet.settings;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/** Real desktop preferences with immediate application and background persistence. */
public final class ProgramSettingsWindow {
    private final Stage stage = new Stage();
    private final CheckBox movement = option("자동으로 걷기", "settings-movement");
    private final CheckBox gravity = option("중력 사용", "settings-gravity");
    private final CheckBox speech = option("가끔 말풍선으로 이야기하기", "settings-speech");
    private final CheckBox top = option("다른 창 위에 펫 표시", "settings-on-top");
    private final CheckBox remember = option("다음 실행 때 위치 기억하기", "settings-remember-position");
    private final Slider size = new Slider(0.5, 2, 1);
    private final Supplier<ProgramSettings> current;
    private final Consumer<ProgramSettings> apply;
    private boolean updating;

    public ProgramSettingsWindow(Stage owner, Supplier<ProgramSettings> current, Consumer<ProgramSettings> apply,
            ReadOnlyStringProperty saveStatus, Path dataDirectory, Runnable ground, Runnable onHidden) {
        this.current = current; this.apply = apply;
        stage.initOwner(owner); stage.setTitle("DoT Pet · 프로그램 설정");
        ProgramSettings initial = current.get(); setControls(initial);
        Label title = label("프로그램 설정"); title.setStyle("-fx-text-fill: #F3F6FC; -fx-font-size: 21px; -fx-font-weight: bold;");
        Label help = label("변경 사항은 바로 적용되고 자동 저장됩니다.\n적용한 캐릭터와 애니메이션도 다음 실행에 복원됩니다.\n중력을 끄면 펫이 현재 위치에 고정됩니다.");help.setWrapText(true);
        Label scale = label("");scale.textProperty().bind(size.valueProperty().multiply(100).asString("펫 크기 · %.0f%%"));
        size.setId("settings-size");size.setShowTickLabels(true);size.setShowTickMarks(true);size.setMajorTickUnit(.5);size.setBlockIncrement(.1);
        for (CheckBox option : new CheckBox[]{movement, gravity, speech, top, remember}) option.setOnAction(event -> publish());
        size.valueProperty().addListener((o, before, after) -> {if (!size.isValueChanging()) publish();});
        size.valueChangingProperty().addListener((o, before, after) -> {if (!after) publish();});
        Button returnToGround = button("펫을 작업표시줄 위로 이동");returnToGround.setId("settings-ground");returnToGround.setOnAction(event -> ground.run());
        TextField location = new TextField(dataDirectory.toString());location.setEditable(false);location.setId("settings-data-path");
        Label dataHelp = label("이 폴더를 복사하면 캐릭터와 설정을 백업할 수 있어요.\n클립보드 내용이나 입력 기록은 저장하지 않습니다.");dataHelp.setWrapText(true);
        Label saved = label("");saved.setWrapText(true);saved.textProperty().bind(saveStatus);saved.setId("settings-save-status");
        Button defaults = button("기본 설정으로");defaults.setId("settings-defaults");defaults.setOnAction(event -> {setControls(ProgramSettings.defaults());publish();});
        Button close = button("닫기");close.setOnAction(event -> stage.close());
        VBox content = new VBox(14, title, help, movement, gravity, speech, top, remember, scale, size,
                returnToGround, label("사용자 데이터 폴더"), location, dataHelp, saved, new javafx.scene.layout.HBox(9, defaults, close));
        content.setPadding(new Insets(22));content.setStyle("-fx-background-color: #111824;");
        ScrollPane scroll = new ScrollPane(content);scroll.setFitToWidth(true);scroll.setStyle("-fx-background: #111824; -fx-background-color: #111824;");
        stage.setScene(new Scene(scroll, 520, 640, Color.web("#111824")));stage.setMinWidth(470);stage.setMinHeight(480);
        stage.setOnHidden(event -> onHidden.run());
    }

    public Stage getStage() { return stage; }
    public void show() { stage.show(); stage.toFront(); }
    private void setControls(ProgramSettings value) {
        updating = true;
        movement.setSelected(value.movementEnabled());gravity.setSelected(value.gravityEnabled());speech.setSelected(value.speechEnabled());
        top.setSelected(value.alwaysOnTop());remember.setSelected(value.rememberPosition());size.setValue(value.sizeScale());updating = false;
    }
    private void publish() {
        if (updating) return;
        ProgramSettings before = current.get();
        apply.accept(new ProgramSettings(movement.isSelected(),gravity.isSelected(),speech.isSelected(),top.isSelected(),
                remember.isSelected(),before.hasPosition(),before.x(),before.y(),size.getValue()));
    }
    private static CheckBox option(String text,String id) {CheckBox box=new CheckBox(text);box.setId(id);box.setStyle("-fx-text-fill: #D7E3F4;");return box;}
    private static Label label(String text) {Label label=new Label(text);label.setStyle("-fx-text-fill: #C5D0E0;");return label;}
    private static Button button(String text) {Button button=new Button(text);button.setStyle("-fx-background-color: #334A63; -fx-text-fill: #F3F6FC; -fx-padding: 8 12;");return button;}
}
