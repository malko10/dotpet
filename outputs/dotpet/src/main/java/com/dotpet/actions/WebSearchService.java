package com.dotpet.actions;

import com.dotpet.settings.SearchSettings;
import com.dotpet.settings.SettingsService;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Opens a search only after the user submits it. Call outside the JavaFX thread. */
public final class WebSearchService {
    @FunctionalInterface public interface BrowserOpener { void open(URI uri, String executable) throws IOException; }
    private final SettingsService settings;
    private final BrowserOpener opener;

    public WebSearchService(SettingsService settings) { this(settings, WebSearchService::openBrowser); }
    public WebSearchService(SettingsService settings, BrowserOpener opener) { this.settings = settings; this.opener = opener; }
    public SearchSettings loadSettings() { return settings.loadSearchSettings(); }
    public String getLoadNotice() { return settings.getSearchLoadNotice(); }

    public void search(String query, SearchSettings preferences) throws IOException {
        URI uri = preferences.engine().searchUri(query);
        if (!preferences.browserExecutable().isEmpty()) validateBrowser(preferences.browserExecutable());
        settings.saveSearchSettings(preferences);
        opener.open(uri, preferences.browserExecutable());
    }

    public static Path validateBrowser(String executable) throws IOException {
        Path file;
        try { file = Path.of(executable); }
        catch (RuntimeException failure) { throw new IOException("브라우저 실행 파일 경로를 확인하세요.", failure); }
        if (!file.isAbsolute() || !Files.isRegularFile(file) || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe"))
            throw new IOException("설치된 브라우저의 .exe 파일을 선택하세요.");
        return file;
    }

    private static void openBrowser(URI uri, String executable) throws IOException {
        if (!executable.isEmpty()) {
            new ProcessBuilder(executable, uri.toASCIIString()).start();
        } else {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw new IOException("Windows 기본 브라우저를 열 수 없어요. 브라우저를 직접 지정해 주세요.");
            Desktop.getDesktop().browse(uri);
        }
    }
}
