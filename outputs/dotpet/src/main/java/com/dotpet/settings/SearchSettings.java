package com.dotpet.settings;

import com.dotpet.actions.SearchEngine;

/** Only preferences are saved; search terms and search history are not stored. */
public record SearchSettings(SearchEngine engine) {
    public SearchSettings {
        if (engine == null)
            throw new IllegalArgumentException("검색 설정이 올바르지 않습니다.");
    }
    public static SearchSettings defaults() { return new SearchSettings(SearchEngine.GOOGLE); }
}
