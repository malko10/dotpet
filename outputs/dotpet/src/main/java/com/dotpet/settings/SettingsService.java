package com.dotpet.settings;

import com.dotpet.input.GestureDirection;
import com.dotpet.input.Hotkey;
import com.dotpet.input.InputAction;
import com.dotpet.storage.AtomicFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Properties;

/** Configured preferences only; observed clipboard/input contents are never stored. */
public final class SettingsService {
    private final Path directory;
    private String loadNotice = "", programLoadNotice = "";
    private boolean corruptInput, corruptProgram;

    public SettingsService() {
        this(System.getProperty("dotpet.dataDir") != null ? Path.of(System.getProperty("dotpet.dataDir"))
                : Path.of(System.getenv("APPDATA") == null ? System.getProperty("user.home") : System.getenv("APPDATA"), "DoTPet"));
    }
    public SettingsService(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    public Path getDirectory() { return directory; }
    public String getLoadNotice() { return loadNotice; }
    public String getProgramLoadNotice() { return programLoadNotice; }

    public InputSettings loadInputSettings() {
        Path file = directory.resolve("input.properties");
        if (!Files.exists(file)) return InputSettings.defaults();
        try { return parseInput(readProperties(file)); }
        catch (IOException | RuntimeException failure) {
            corruptInput = true;
            try {
                InputSettings recovered = parseInput(readProperties(directory.resolve("input.properties.bak")));
                loadNotice = "입력 설정을 읽지 못해 백업을 복원했습니다. 기존 파일은 보존했어요.";
                return recovered;
            } catch (IOException | RuntimeException unavailable) {
                loadNotice = "입력 설정을 읽지 못해 기본값으로 시작했습니다. 기존 파일은 보존했어요.";
                return InputSettings.defaults();
            }
        }
    }

    private InputSettings parseInput(Properties values) {
        InputSettings defaults = InputSettings.defaults();
        EnumMap<InputAction, Hotkey> hotkeys = new EnumMap<>(InputAction.class);
        for (InputAction action : InputAction.values()) {
            if (action == InputAction.NONE) continue;
            String encoded = values.getProperty("hotkey." + action.name());
            if (encoded == null) { if (defaults.hotkeys().containsKey(action)) hotkeys.put(action, defaults.hotkeys().get(action)); }
            else if (!encoded.isBlank()) hotkeys.put(action, Hotkey.decode(encoded));
        }
        EnumMap<GestureDirection, InputAction> gestures = new EnumMap<>(GestureDirection.class);
        boolean legacyEnter = !values.getProperty("hotkey.PASTE_CLIPBOARD_ENTER", "").isBlank();
        boolean plainPaste = hotkeys.containsKey(InputAction.PASTE_CLIPBOARD);
        for (GestureDirection direction : GestureDirection.values()) {
            String action = values.getProperty("gesture." + direction.name(), defaults.gestures().get(direction).name());
            plainPaste |= action.equals("PASTE_CLIPBOARD");
            if (action.equals("PASTE_CLIPBOARD_ENTER")) { legacyEnter = true; action = "PASTE_CLIPBOARD"; }
            gestures.put(direction, InputAction.valueOf(action));
        }
        if (legacyEnter) {
            String oldKey = values.getProperty("hotkey.PASTE_CLIPBOARD_ENTER", "");
            if (!hotkeys.containsKey(InputAction.PASTE_CLIPBOARD) && !oldKey.isBlank())
                hotkeys.put(InputAction.PASTE_CLIPBOARD, Hotkey.decode(oldKey));
            loadNotice = "이전 ‘붙여넣고 Enter’를 ‘붙여넣기’에 통합했어요. 키 설정에서 단축키와 Enter 체크를 확인하세요.";
        }
        return new InputSettings(flag(values, "hotkeysEnabled", false), flag(values, "gesturesEnabled", false), hotkeys, gestures,
                Integer.parseInt(values.getProperty("gestureThreshold", "70")), values.getProperty("savedText", ""),
                Integer.parseInt(values.getProperty("clickIntervalMillis", "1000")), Integer.parseInt(values.getProperty("clickCount", "10")),
                flag(values, "infiniteClick", false), flag(values, "pasteTextEnter", false),
                flag(values, "pasteClipboardEnter", legacyEnter && !plainPaste));
    }

    public synchronized void saveInputSettings(InputSettings settings) throws IOException {
        Properties values = new Properties();
        values.setProperty("hotkeysEnabled", String.valueOf(settings.hotkeysEnabled()));
        values.setProperty("gesturesEnabled", String.valueOf(settings.gesturesEnabled()));
        values.setProperty("gestureThreshold", String.valueOf(settings.gestureThreshold()));
        values.setProperty("savedText", settings.savedText());
        values.setProperty("clickIntervalMillis", String.valueOf(settings.clickIntervalMillis()));
        values.setProperty("clickCount", String.valueOf(settings.clickCount()));
        values.setProperty("infiniteClick", String.valueOf(settings.infiniteClick()));
        values.setProperty("pasteTextEnter", String.valueOf(settings.pasteTextEnter()));
        values.setProperty("pasteClipboardEnter", String.valueOf(settings.pasteClipboardEnter()));
        for (InputAction action : InputAction.values()) if (action != InputAction.NONE)
            values.setProperty("hotkey." + action.name(), settings.hotkeys().containsKey(action) ? settings.hotkeys().get(action).encode() : "");
        for (GestureDirection direction : GestureDirection.values()) values.setProperty("gesture." + direction.name(),
                settings.gestures().getOrDefault(direction, InputAction.NONE).name());
        if(corruptInput)AtomicFiles.preserveCorrupt(directory.resolve("input.properties"));
        AtomicFiles.writeProperties(directory.resolve("input.properties"), values, !corruptInput);
        corruptInput = false;
    }

    public ProgramSettings loadProgramSettings() {
        Path file = directory.resolve("program.properties");
        if (!Files.exists(file)) return ProgramSettings.defaults();
        try { return parseProgram(readProperties(file)); }
        catch (IOException | RuntimeException failure) {
            corruptProgram = true;
            try {
                ProgramSettings recovered = parseProgram(readProperties(directory.resolve("program.properties.bak")));
                programLoadNotice = "프로그램 설정을 읽지 못해 백업을 복원했어요. 원본은 보존했습니다.";
                return recovered;
            } catch (IOException | RuntimeException unavailable) {
                programLoadNotice = "프로그램 설정을 읽지 못해 기본값으로 시작했어요. 원본은 보존했습니다.";
                return ProgramSettings.defaults();
            }
        }
    }

    private static ProgramSettings parseProgram(Properties values) {
        return new ProgramSettings(flag(values, "movementEnabled", true), flag(values, "gravityEnabled", true),
                flag(values, "speechEnabled", true), flag(values, "alwaysOnTop", true), flag(values, "rememberPosition", true),
                flag(values, "hasPosition", false), Double.parseDouble(values.getProperty("x", "0")),
                Double.parseDouble(values.getProperty("y", "0")), Double.parseDouble(values.getProperty("sizeScale", "1")));
    }

    public synchronized void saveProgramSettings(ProgramSettings settings) throws IOException {
        Properties values = new Properties();
        values.setProperty("movementEnabled", String.valueOf(settings.movementEnabled()));
        values.setProperty("gravityEnabled", String.valueOf(settings.gravityEnabled()));
        values.setProperty("speechEnabled", String.valueOf(settings.speechEnabled()));
        values.setProperty("alwaysOnTop", String.valueOf(settings.alwaysOnTop()));
        values.setProperty("rememberPosition", String.valueOf(settings.rememberPosition()));
        values.setProperty("hasPosition", String.valueOf(settings.hasPosition()));
        values.setProperty("x", Double.toString(settings.x())); values.setProperty("y", Double.toString(settings.y()));
        values.setProperty("sizeScale", Double.toString(settings.sizeScale()));
        if(corruptProgram)AtomicFiles.preserveCorrupt(directory.resolve("program.properties"));
        AtomicFiles.writeProperties(directory.resolve("program.properties"), values, !corruptProgram);
        corruptProgram = false;
    }

    private static Properties readProperties(Path path) throws IOException {
        if (Files.size(path) > 262_144) throw new IOException("설정 파일이 너무 큽니다.");
        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(path)) { values.load(reader); }
        if(values.isEmpty())throw new IOException("설정 파일이 비어 있습니다.");
        return values;
    }

    private static boolean flag(Properties values, String name, boolean fallback) {
        String value = values.getProperty(name, Boolean.toString(fallback));
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IllegalArgumentException("잘못된 설정: " + name);
        return Boolean.parseBoolean(value);
    }

    public void recordFailure(String operation, Throwable failure) {
        try {
            Path logs = directory.resolve("logs");Files.createDirectories(logs);
            Files.writeString(logs.resolve("dotpet.log"),java.time.Instant.now()+" "+operation+": "+failure+System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
        } catch(IOException ignored) { }
    }
}
