package com.dotpet.actions;

import com.dotpet.settings.SearchSettings;
import com.dotpet.settings.SettingsService;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Runs without launching a browser, deleting a file, or emptying a real Recycle Bin. */
public final class DesktopUtilitiesCheck {
    private static int checks;
    private static void require(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    @FunctionalInterface private interface Checked {void run() throws Exception;}
    private static void rejects(Checked action) throws Exception {
        checks++;
        try {action.run();} catch (IOException | IllegalArgumentException expected) {return;}
        throw new AssertionError("Invalid operation was accepted");
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().resolve("run-" + java.util.UUID.randomUUID()); Files.createDirectories(root);
        for (SearchEngine engine : SearchEngine.values()) {
            for (String query : Set.of("슬라임 왕관", "a&b=#?+%", "https://example.com/", "file:///C:/test", "--argument $(text) \"quote\"")) {
                URI uri = engine.searchUri(query);
                require(uri.getScheme().equals("https") && uri.getFragment() == null && uri.getUserInfo() == null, "Query escaped search endpoint");
                require(URLDecoder.decode(uri.getRawQuery().substring(uri.getRawQuery().indexOf('=') + 1), StandardCharsets.UTF_8).equals(query), "Query text was corrupted");
            }
            rejects(() -> engine.searchUri("  ")); rejects(() -> engine.searchUri("x".repeat(1001)));
        }
        SettingsService settings = new SettingsService(root.resolve("settings"));
        require(settings.loadSearchSettings().equals(SearchSettings.defaults()), "Fresh defaults failed");
        AtomicReference<URI> opened = new AtomicReference<>();
        WebSearchService search = new WebSearchService(settings, opened::set);
        var prefs = new SearchSettings(SearchEngine.NAVER);
        search.search("private search 41", prefs);
        require(opened.get().equals(SearchEngine.NAVER.searchUri("private search 41")), "Wrong URL");
        require(new SettingsService(settings.getDirectory()).loadSearchSettings().equals(prefs), "Search preferences not restored");
        require(!Files.readString(settings.getDirectory().resolve("search.properties")).contains("private search"), "Search text was persisted");
        Files.writeString(settings.getDirectory().resolve("search.properties"), "engine=NAVER\nbrowserExecutable=C:/old/browser.exe");
        require(settings.loadSearchSettings().equals(prefs), "Legacy browser must be ignored");
        search.search("migration", settings.loadSearchSettings());
        require(!Files.readString(settings.getDirectory().resolve("search.properties")).contains("browserExecutable"), "Obsolete browser preference persisted");
        settings.saveSearchSettings(SearchSettings.defaults());
        Path file = settings.getDirectory().resolve("search.properties"); Files.writeString(file, "engine=NO_SUCH_ENGINE");
        SettingsService recover = new SettingsService(settings.getDirectory());
        require(recover.loadSearchSettings().equals(prefs) && !recover.getSearchLoadNotice().isBlank(), "Backup recovery failed");
        recover.saveSearchSettings(SearchSettings.defaults());
        try (var files = Files.list(settings.getDirectory())) {require(files.anyMatch(p -> p.getFileName().toString().contains(".corrupt-")), "Corrupt settings were not preserved");}
        require((WindowsRecycleBin.RECYCLE_FLAGS & 0x00080000) != 0 && (WindowsRecycleBin.RECYCLE_FLAGS & 0x10040000) == 0,
                "Missing recycle-only flag / automatic elevation enabled");
        var guard = new WindowsRecycleBin.RecycleGuard();
        require(guard.beforeDelete(0) < 0 && guard.blocked, "Permanent delete was allowed");
        require(new WindowsRecycleBin.RecycleGuard().beforeDelete(0x80) == 0, "Recycling flag was rejected");
        try {Thread.currentThread().interrupt(); require(new WindowsRecycleBin.RecycleGuard().beforeDelete(0x80) < 0, "Interrupted operation not blocked");}
        finally {Thread.interrupted();}
        rejects(() -> new RecycleBinService().empty(false));
        verifyAutoEmpty(root.resolve("recycle"));
        verifyProfileDeletion(root.resolve("profiles"));
        System.out.println("Passed " + checks + " search, recycle/auto-empty and profile deletion checks; no external actions performed.");
    }

    private static void verifyAutoEmpty(Path root) throws Exception {
        SettingsService settings = new SettingsService(root);
        class FakeBin extends RecycleBinService {
            int empties, moves;
            boolean failEmpty, failMove;
            Result next = new Result(1, java.util.List.of());
            FakeBin() { super(settings); }
            @Override public synchronized Result recycle(java.util.List<Path> files) throws IOException {
                moves++; if (failMove) throw new IOException("move failed"); return next;
            }
            @Override public synchronized void empty(boolean confirmed) throws IOException {
                require(confirmed, "Empty without enabled preference");
                empties++; if (failEmpty) throw new IOException("empty failed");
            }
        }
        FakeBin bin = new FakeBin();
        var files = java.util.List.of(root.resolve("not-a-real-file"));
        require(!bin.isAutoEmpty(), "Auto empty must default off");
        bin.receivePetDrop(files); require(bin.empties == 0, "Default drop emptied bin");
        bin.setAutoEmpty(true); require(bin.empties == 0, "Enabling emptied bin immediately");
        require(new SettingsService(root).loadRecycleAutoEmpty(), "Enabled preference not restored");
        bin.receivePetDrop(files); require(bin.empties == 1, "Successful drop did not empty bin");
        bin.next = new RecycleBinService.Result(1, java.util.List.of(new RecycleBinService.Failure(files.getFirst(), "failed")));
        bin.receivePetDrop(files); require(bin.empties == 1, "Partial failure emptied bin");
        bin.next = new RecycleBinService.Result(0, java.util.List.of());
        bin.receivePetDrop(files); require(bin.empties == 1, "Empty result emptied bin");
        bin.failMove = true; rejects(() -> bin.receivePetDrop(files));
        require(bin.empties == 1, "Thrown move failure emptied bin"); bin.failMove = false;
        bin.next = new RecycleBinService.Result(1, java.util.List.of()); bin.failEmpty = true;
        require(bin.receivePetDrop(files).contains("empty failed"), "Empty failure not reported");
        bin.setAutoEmpty(false); bin.receivePetDrop(files);
        require(bin.empties == 2, "Disabled option emptied bin");
        // Never restore a stale enabled backup for an irreversible action.
        Files.writeString(root.resolve("recycle.properties.bak"), "autoEmpty=true");
        Files.writeString(root.resolve("recycle.properties"), "autoEmpty=broken");
        require(!bin.isAutoEmpty(), "Corrupt setting enabled destructive behavior");
        bin.receivePetDrop(files); require(bin.empties == 2, "Corrupt setting emptied bin");
        bin.setAutoEmpty(false);
        try (var entries = Files.list(root)) {
            require(entries.anyMatch(p -> p.getFileName().toString().contains(".corrupt-")), "Corrupt recycle settings not preserved");
        }
    }

    private static void verifyProfileDeletion(Path root) throws Exception {
        var repository = new com.dotpet.storage.PetProfileRepository(root);
        var animations = com.dotpet.animation.DefaultPetAnimations.get();
        var profile = new com.dotpet.customization.PetCustomization("user-delete", "삭제할 펫", animations,
                1, false, javafx.scene.paint.Color.WHITE);
        repository.save(profile); repository.save(profile);
        var other = new com.dotpet.customization.PetCustomization("user-keep", "남길 펫", animations,
                1.5, true, javafx.scene.paint.Color.PINK);
        repository.save(other);
        byte[] keptProject = Files.readAllBytes(root.resolve("user-keep.dotpet"));
        byte[] keptMetadata = Files.readAllBytes(root.resolve("user-keep.properties"));
        Path outside = root.resolveSibling("outside.dotpet"); Files.writeString(outside, "keep");
        for (String id : java.util.List.of("builtin", "../outside", "..\\outside", root.resolve("user-keep").toString(), "", "user.delete"))
            rejects(() -> repository.delete(id));
        require(repository.loadProfiles().size() == 2 && Files.readString(outside).equals("keep"), "Invalid id changed profiles/outside file");
        Path original = root.resolveSibling("import-source.dotpet"); Files.copy(root.resolve("user-delete.dotpet"), original);
        repository.delete(profile.id());
        for (String suffix : java.util.List.of(".dotpet", ".dotpet.bak", ".properties", ".properties.bak"))
            require(!Files.exists(root.resolve(profile.id() + suffix)), "Deleted entry or backup remained: " + suffix);
        require(Files.exists(original), "Imported source was deleted");
        var reloaded = new com.dotpet.storage.PetProfileRepository(root).loadProfiles();
        require(reloaded.size() == 1 && reloaded.getFirst().id().equals(other.id()) && reloaded.getFirst().name().equals(other.name()), "Deleted entry returned after reload");
        require(java.util.Arrays.equals(keptProject, Files.readAllBytes(root.resolve("user-keep.dotpet")))
                && java.util.Arrays.equals(keptMetadata, Files.readAllBytes(root.resolve("user-keep.properties"))), "Delete changed another profile");
        repository.delete(profile.id()); // An unsaved/new entry also has nothing to remove from disk.
        require(repository.loadProfiles().size() == 1, "Repeated/missing deletion affected another profile");
        Path blocker = root.resolve("user-keep.properties.bak"); Files.createDirectory(blocker);
        rejects(() -> repository.delete(other.id()));
        require(Files.isRegularFile(root.resolve("user-keep.dotpet")) && Files.isRegularFile(root.resolve("user-keep.properties")), "Preflight error changed existing entry");
    }
}
