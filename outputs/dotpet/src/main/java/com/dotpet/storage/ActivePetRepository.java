package com.dotpet.storage;

import com.dotpet.customization.PetCustomization;
import com.dotpet.editor.PetAnimationProject;
import com.dotpet.editor.PixelEditorWindow;
import com.dotpet.pet.PetState;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javafx.scene.paint.Color;

/** One atomic snapshot contains the applied character and all four animation timelines. */
public final class ActivePetRepository {
    private static final int MAX_PROJECT_BYTES = 64 * 1024 * 1024;
    private final Path directory;
    private boolean corruptOriginal;
    private String loadNotice = "";

    public ActivePetRepository(Path directory) { this.directory = directory; }
    public String getLoadNotice() { return loadNotice; }

    public Optional<PetCustomization> load() {
        Path file = directory.resolve("active-pet.zip");
        if (!Files.exists(file)) return Optional.empty();
        try { return Optional.of(read(file)); }
        catch (IOException | RuntimeException failure) {
            corruptOriginal = true;
            try {
                PetCustomization backup = read(directory.resolve("active-pet.zip.bak"));
                loadNotice = "적용한 펫 파일을 읽지 못해 백업을 복원했어요. 원본은 보존했습니다.";
                return Optional.of(backup);
            } catch (IOException | RuntimeException unavailable) {
                loadNotice = "적용한 펫을 읽지 못해 기본 캐릭터로 시작했어요. 기존 파일은 보존했습니다.";
                return Optional.empty();
            }
        }
    }

    public void save(PetCustomization profile) throws IOException {
        Files.createDirectories(directory);
        Path projectFile = Files.createTempFile(directory, "active-project-", ".dotpet");
        Path archive = Files.createTempFile(directory, "active-pet-", ".tmp");
        try {
            var project = new PetAnimationProject(profile.animations().get(PetState.IDLE).getFrame(0), profile.animations());
            PixelEditorWindow.writeProject(project, projectFile);
            Properties appearance = new Properties();
            appearance.setProperty("id", profile.id()); appearance.setProperty("name", profile.name());
            appearance.setProperty("sizeScale", Double.toString(profile.sizeScale()));
            appearance.setProperty("horizontalFlip", Boolean.toString(profile.horizontalFlip()));
            appearance.setProperty("tint", profile.tint().toString());
            StringWriter metadata = new StringWriter(); appearance.store(metadata, "Applied DoT Pet");
            try (var zip = new ZipOutputStream(Files.newOutputStream(archive), StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry("project.dotpet")); Files.copy(projectFile, zip); zip.closeEntry();
                zip.putNextEntry(new ZipEntry("appearance.properties")); zip.write(metadata.toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
            if(corruptOriginal)AtomicFiles.preserveCorrupt(directory.resolve("active-pet.zip"));
            AtomicFiles.replace(archive, directory.resolve("active-pet.zip"), !corruptOriginal);
            corruptOriginal = false;
        } finally {
            Files.deleteIfExists(projectFile);
            Files.deleteIfExists(projectFile.resolveSibling(projectFile.getFileName() + ".bak"));
            Files.deleteIfExists(archive);
        }
    }

    private PetCustomization read(Path file) throws IOException {
        try (var zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
            String json = readEntry(zip, "project.dotpet", MAX_PROJECT_BYTES);
            Properties metadata = new Properties();
            metadata.load(new StringReader(readEntry(zip, "appearance.properties", 65536)));
            var project = PixelEditorWindow.readProject(json);
            return new PetCustomization(metadata.getProperty("id"), metadata.getProperty("name"), project.toAnimations(),
                    Double.parseDouble(metadata.getProperty("sizeScale")),
                    Boolean.parseBoolean(metadata.getProperty("horizontalFlip")), Color.web(metadata.getProperty("tint")));
        }
    }

    private static String readEntry(ZipFile zip, String name, int limit) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) throw new IOException("펫 파일에 필요한 데이터가 없습니다.");
        try (var input = zip.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("펫 파일이 너무 큽니다.");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
