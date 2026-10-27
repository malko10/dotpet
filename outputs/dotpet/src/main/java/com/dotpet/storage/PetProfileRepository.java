package com.dotpet.storage;

import com.dotpet.animation.Animation;
import com.dotpet.animation.Frame;
import com.dotpet.customization.PetCustomization;
import com.dotpet.editor.PetAnimationProject;
import com.dotpet.editor.PixelEditorWindow;
import com.dotpet.pet.PetState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javafx.scene.paint.Color;

/** Stores user-created character profiles outside the application directory. */
public final class PetProfileRepository {
    private final Path directory;
    private String loadNotice = "";

    public PetProfileRepository() {
        directory = new com.dotpet.settings.SettingsService().getDirectory().resolve("pets");
    }

    public PetProfileRepository(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public List<PetCustomization> loadProfiles() throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        List<PetCustomization> profiles = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            for (Path projectFile : paths.filter(path -> path.getFileName().toString().endsWith(".dotpet")).toList()) {
                try {
                    PetAnimationProject project = PixelEditorWindow.loadProject(projectFile);
                    String id = projectFile.getFileName().toString().replaceFirst("\\.dotpet$", "");
                    Properties metadata = new Properties();
                    Path metadataFile = directory.resolve(id + ".properties");
                    if (Files.isRegularFile(metadataFile)) {
                        try (var reader = Files.newBufferedReader(metadataFile)) { metadata.load(reader); }
                    }
                    String name = metadata.getProperty("name", id);
                    double scale = parseScale(metadata.getProperty("sizeScale", "1.0"));
                    boolean flip = Boolean.parseBoolean(metadata.getProperty("horizontalFlip", "false"));
                    Color tint = parseColor(metadata.getProperty("tint", "#FFFFFFFF"));
                    profiles.add(new PetCustomization(id, name, project.toAnimations(), scale, flip, tint));
                } catch (RuntimeException | IOException ignored) {
                    loadNotice = "일부 캐릭터를 읽지 못했습니다. 해당 파일은 보존했어요.";
                }
            }
        }
        return List.copyOf(profiles);
    }

    public PetCustomization save(PetCustomization profile) throws IOException {
        Files.createDirectories(directory);
        String id = sanitize(profile.id());
        Path projectPath = directory.resolve(id + ".dotpet");
        Frame base = profile.animations().get(PetState.IDLE).getFrame(0);
        PetAnimationProject project = new PetAnimationProject(base, profile.animations());
        for (PetState state : project.getEditableStates()) {
            Animation animation = profile.animations().get(state);
            if (animation != null) project.setFramesPerSecond(state, Math.max(1, Math.min(24, animation.getFramesPerSecond())));
        }
        PixelEditorWindow.writeProject(project, projectPath);

        Properties metadata = new Properties();
        metadata.setProperty("name", profile.name());
        metadata.setProperty("sizeScale", Double.toString(profile.sizeScale()));
        metadata.setProperty("horizontalFlip", Boolean.toString(profile.horizontalFlip()));
        metadata.setProperty("tint", colorHex(profile.tint()));
        AtomicFiles.writeProperties(directory.resolve(id + ".properties"), metadata, true);
        return new PetCustomization(id, profile.name(), project.toAnimations(), profile.sizeScale(),
                profile.horizontalFlip(), profile.tint());
    }

    public Path getDirectory() { return directory; }
    public String getLoadNotice() { return loadNotice; }

    private static String sanitize(String text) {
        String safe = text.toLowerCase().replaceAll("[^a-z0-9가-힣_-]+", "-").replaceAll("^-|-$", "");
        if (safe.isBlank()) safe = "my-pet";
        return safe;
    }

    private static double parseScale(String value) {
        try { double scale = Double.parseDouble(value); return Double.isFinite(scale) ? Math.max(0.5, Math.min(2.0, scale)) : 1; }
        catch (NumberFormatException ex) { return 1.0; }
    }

    private static Color parseColor(String value) {
        try { return Color.web(value); }
        catch (IllegalArgumentException ex) { return Color.WHITE; }
    }

    private static String colorHex(Color color) {
        return String.format("#%02X%02X%02X%02X", (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255), (int) Math.round(color.getBlue() * 255),
                (int) Math.round(color.getOpacity() * 255));
    }
}
