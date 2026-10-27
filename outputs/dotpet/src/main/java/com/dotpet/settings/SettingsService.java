package com.dotpet.settings;

import com.dotpet.input.GestureDirection;
import com.dotpet.input.Hotkey;
import com.dotpet.input.InputAction;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Properties;

/** Saves configured bindings, never raw input events or observed clipboard text. */
public final class SettingsService {
    private final Path directory;
    private String loadNotice = "";

    public SettingsService() {
        this(System.getProperty("dotpet.dataDir") != null ? Path.of(System.getProperty("dotpet.dataDir"))
                : Path.of(System.getenv("APPDATA") == null ? System.getProperty("user.home") : System.getenv("APPDATA"), "DoTPet"));
    }
    public SettingsService(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    public Path getDirectory() { return directory; }
    public String getLoadNotice() { return loadNotice; }

    public InputSettings loadInputSettings() {
        Path file = directory.resolve("input.properties");
        if (!Files.exists(file)) return InputSettings.defaults();
        try {
            if (Files.size(file) > 262_144) throw new IOException("입력 설정 파일이 너무 큽니다.");
            Properties values = new Properties();
            try (var reader = Files.newBufferedReader(file)) { values.load(reader); }
            InputSettings defaults = InputSettings.defaults();
            EnumMap<InputAction, Hotkey> hotkeys = new EnumMap<>(InputAction.class);
            for (InputAction action : InputAction.values()) {
                if (action == InputAction.NONE) continue;
                String encoded = values.getProperty("hotkey." + action.name());
                if (encoded == null) { if (defaults.hotkeys().containsKey(action)) hotkeys.put(action, defaults.hotkeys().get(action)); }
                else if (!encoded.isBlank()) hotkeys.put(action, Hotkey.decode(encoded));
            }
            EnumMap<GestureDirection, InputAction> gestures = new EnumMap<>(GestureDirection.class);
            for (GestureDirection direction : GestureDirection.values()) gestures.put(direction,
                    InputAction.valueOf(values.getProperty("gesture." + direction.name(), defaults.gestures().get(direction).name())));
            return new InputSettings(Boolean.parseBoolean(values.getProperty("hotkeysEnabled", "false")),
                    Boolean.parseBoolean(values.getProperty("gesturesEnabled", "false")), hotkeys, gestures,
                    Integer.parseInt(values.getProperty("gestureThreshold", "70")), values.getProperty("savedText", ""),
                    Integer.parseInt(values.getProperty("clickIntervalMillis", "1000")), Integer.parseInt(values.getProperty("clickCount", "10")),
                    Boolean.parseBoolean(values.getProperty("infiniteClick", "false")));
        } catch (IOException | RuntimeException failure) {
            loadNotice = "입력 설정을 읽지 못해 기본값으로 시작했습니다. 기존 파일은 보존했어요.";
            return InputSettings.defaults();
        }
    }

    public void saveInputSettings(InputSettings settings) throws IOException {
        Files.createDirectories(directory);
        Properties values = new Properties();
        values.setProperty("hotkeysEnabled", String.valueOf(settings.hotkeysEnabled()));
        values.setProperty("gesturesEnabled", String.valueOf(settings.gesturesEnabled()));
        values.setProperty("gestureThreshold", String.valueOf(settings.gestureThreshold()));
        values.setProperty("savedText", settings.savedText());
        values.setProperty("clickIntervalMillis", String.valueOf(settings.clickIntervalMillis()));
        values.setProperty("clickCount", String.valueOf(settings.clickCount()));
        values.setProperty("infiniteClick", String.valueOf(settings.infiniteClick()));
        for (InputAction action : InputAction.values()) if (action != InputAction.NONE)
            values.setProperty("hotkey." + action.name(), settings.hotkeys().containsKey(action) ? settings.hotkeys().get(action).encode() : "");
        for (GestureDirection direction : GestureDirection.values()) values.setProperty("gesture." + direction.name(),
                settings.gestures().getOrDefault(direction, InputAction.NONE).name());
        Path destination = directory.resolve("input.properties");
        Path temporary = Files.createTempFile(directory, "input-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) { values.store(writer, "DoT Pet configured input and quick actions"); }
            if (Files.exists(destination)) Files.copy(destination, directory.resolve("input.properties.bak"), StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
