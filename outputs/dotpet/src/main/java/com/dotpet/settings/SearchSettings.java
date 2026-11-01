package com.dotpet.settings;

import com.dotpet.actions.SearchEngine;

/** Only preferences are saved; search terms and search history are not stored. */
public record SearchSettings(SearchEngine engine, String browserExecutable) {
    public SearchSettings {
        if (engine == null || browserExecutable == null || browserExecutable.length() > 32767)
            throw new IllegalArgumentException("검색 설정이 올바르지 않습니다.");
        browserExecutable = browserExecutable.strip();
    }
    public static SearchSettings defaults() { return new SearchSettings(SearchEngine.GOOGLE, ""); }
}
