package com.dotpet.actions;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Fixed HTTPS search endpoints; entered text is always encoded as a query. */
public enum SearchEngine {
    GOOGLE("Google", "https://www.google.com/search?q="),
    NAVER("네이버", "https://search.naver.com/search.naver?query="),
    BING("Bing", "https://www.bing.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=");

    private final String label;
    private final String endpoint;
    SearchEngine(String label, String endpoint) { this.label = label; this.endpoint = endpoint; }
    @Override public String toString() { return label; }

    public URI searchUri(String text) {
        String query = text == null ? "" : text.strip();
        if (query.isEmpty()) throw new IllegalArgumentException("검색어를 입력하세요.");
        if (query.length() > 1000) throw new IllegalArgumentException("검색어는 1,000자 이내로 입력하세요.");
        return URI.create(endpoint + URLEncoder.encode(query, StandardCharsets.UTF_8));
    }
}
