package com.dotpet.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Temporary files are created beside their destination, then replaced without truncating the old file. */
public final class AtomicFiles {
    private AtomicFiles() { }

    public static void preserveCorrupt(Path destination) throws IOException {
        if (Files.isRegularFile(destination)) {
            String suffix = ".corrupt-" + java.time.Instant.now().toEpochMilli() + "-" + java.util.UUID.randomUUID().toString().substring(0,8);
            Files.copy(destination,destination.resolveSibling(destination.getFileName()+suffix));
        }
    }

    public static void replace(Path temporary, Path destination, boolean backup) throws IOException {
        if (backup && Files.isRegularFile(destination))
            Files.copy(destination, destination.resolveSibling(destination.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
    }

    public static void writeProperties(Path destination, Properties values, boolean backup) throws IOException {
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(destination.toAbsolutePath().getParent(), "settings-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) { values.store(writer, "DoT Pet settings"); }
            replace(temporary, destination, backup);
        } finally { Files.deleteIfExists(temporary); }
    }

    public static void writeString(Path destination, String text) throws IOException {
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(destination.toAbsolutePath().getParent(), "project-", ".tmp");
        try { Files.writeString(temporary, text); replace(temporary, destination, true); }
        finally { Files.deleteIfExists(temporary); }
    }
}
