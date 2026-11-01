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
        AtomicReference<URI> opened = new AtomicReference<>(); AtomicReference<String> browser = new AtomicReference<>();
        WebSearchService search = new WebSearchService(settings, (uri, executable) -> {opened.set(uri); browser.set(executable);});
        Path fakeBrowser = root.resolve("브라우저 with spaces.exe"); Files.writeString(fakeBrowser, "test placeholder; never executed");
        var prefs = new SearchSettings(SearchEngine.NAVER, fakeBrowser.toString());
        search.search("private search 41", prefs);
        require(opened.get().equals(SearchEngine.NAVER.searchUri("private search 41")) && browser.get().equals(fakeBrowser.toString()), "Wrong browser or URL");
        require(new SettingsService(settings.getDirectory()).loadSearchSettings().equals(prefs), "Search preferences not restored");
        require(!Files.readString(settings.getDirectory().resolve("search.properties")).contains("private search"), "Search text was persisted");
        Path command = root.resolve("not-a-browser.cmd"); Files.writeString(command, "test");
        rejects(() -> search.search("x", new SearchSettings(SearchEngine.GOOGLE, command.toString())));
        rejects(() -> search.search("x", new SearchSettings(SearchEngine.GOOGLE, root.resolve("missing.exe").toString())));
        rejects(() -> search.search("x", new SearchSettings(SearchEngine.GOOGLE, "relative.exe")));
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
        System.out.println("Passed " + checks + " search encoding/browser preference/backup/privacy and recycle-guard checks; no external actions performed.");
    }
}
