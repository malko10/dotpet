package com.dotpet.customization;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum PetAccessory {
    CROWN("왕관"), HAT("모자"), EYE_PATCH("안대"), MUSTACHE("수염");
    private final String label;
    PetAccessory(String label) { this.label = label; }
    public String getLabel() { return label; }

    public static Set<PetAccessory> decode(String value) {
        return Arrays.stream(values()).filter(item -> Arrays.asList(value.split(",")).contains(item.name())).collect(Collectors.toUnmodifiableSet());
    }

    public static String encode(Set<PetAccessory> items) {
        return Arrays.stream(values()).filter(items::contains).map(Enum::name).collect(Collectors.joining(","));
    }
}
