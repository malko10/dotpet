package com.dotpet.actions;

import com.dotpet.settings.SearchSettings;
import com.dotpet.settings.SettingsService;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

/** Opens a search only after the user submits it. Call outside the JavaFX thread. */
public final class WebSearchService {
    @FunctionalInterface public interface BrowserOpener { void open(URI uri) throws IOException; }
    private final SettingsService settings;
    private final BrowserOpener opener;

    public WebSearchService(SettingsService settings) { this(settings, WebSearchService::openBrowser); }
    public WebSearchService(SettingsService settings, BrowserOpener opener) { this.settings = settings; this.opener = opener; }
    public SearchSettings loadSettings() { return settings.loadSearchSettings(); }
    public String getLoadNotice() { return settings.getSearchLoadNotice(); }

    public void search(String query, SearchSettings preferences) throws IOException {
        URI uri = preferences.engine().searchUri(query);
        settings.saveSearchSettings(preferences);
        opener.open(uri);
    }

    private static void openBrowser(URI uri) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            throw new IOException("Windows 설정에서 기본 브라우저를 지정해 주세요.");
        Desktop.getDesktop().browse(uri);
    }
}
