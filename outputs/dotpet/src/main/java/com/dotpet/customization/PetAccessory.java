package com.dotpet.customization;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public enum PetAccessory {
    CROWN("보석 왕관",
            ".K.....K.....K.", "KYK...KYK...KYK", "KYGK.KYHYK.KGYK", "KYHYYYHYHYYYHYK",
            ".KYYYRYCYYYYYK.", ".KGYYYYYYYYYGK.", "..KKKKKKKKKKK.."),
    HAT("실크 모자",
            "....KKKKKKKKK....", "...KBHHBBBBBbK...", "...KBHBBBBBBbK...", "...KBBBBBBBBbK...",
            "...KBBBBBBBBbK...", "...KRRRRRRRRrK...", ".KKKrrrrrrrrrKKK.", "KBBBBBBBBBBBBbbbK", ".KKKKKKKKKKKKKKK."),
    WIZARD_HAT("마법사 모자",
            "........KK.......", ".......KPAK......", "......KPAPpK.....", ".....KPPPPppK....",
            ".....KPYPPppK....", "....KPPYPPPppK...", "....KPPPPPPppK...", "...KYYYYYYYYYGK..",
            ".KKPPPPPPPPPPppK.", "KKKKKKKKKKKKKKKKK"),
    PARTY_HAT("파티 모자",
            ".......HW........", "......WHH........", ".......KK........", "......KRRK.......",
            ".....KRRRRK......", ".....KYYYYK......", "....KCCCCCCK.....", "....KPPPPPPK.....",
            "...KRRRRRRRrK....", "...KKKKKKKKKK...."),
    STRAW_HAT("밀짚모자",
            ".....KKKKKKK.....", "....KTHHTTTtK....", "...KTHTTTTTttK...", "...KTTTTTTTttK...",
            "...KRRRRRRRrrK...", ".KKTTTTTTTTTTtKK.", "KTTHTHTTTTTTTtttK", ".KKKKKKKKKKKKKKK."),
    BERET("베레모",
            ".......KKK.......", "......KrK........", "...KKKRRRRKK.....", "..KRHHRRRRRrKK...",
            ".KRHRRRRRRRRrrK..", ".KRRRRRRRRRrrrK..", "..KrrrrrrrrrrK...", "...KKKKKKKKKK...."),
    BEANIE("방울 비니",
            "......KKKKK......", ".....KAHHAAK.....", ".....KAAAAAK.....", "....KKPPPPPpK....",
            "...KPAAPAPPppK...", "...KPPAPPAPppK...", "..KPPPPPPPPpppK..", "..KAAAAAAAAAAAK..",
            "..KKKKKKKKKKKKK.."),
    FLOWER_CROWN("꽃 화관",
            "...W.......R.....", "..WHW.....RYR....", "...W...W...R.....", "..nNn.WYW.nNn....",
            ".nNNNN.W.NNNNNn..", "..nnNNNNNNNNnn...", "....nnnnnnnn.....");
    private final String label;
    private final List<String> rows;
    PetAccessory(String label, String... rows) { this.label = label; this.rows = List.of(rows); }
    public String getLabel() { return label; }
    List<String> rows() { return rows; }

    /** Headwear occupies one slot; old face accessories and unknown names are ignored. */
    static Set<PetAccessory> headwear(Set<PetAccessory> items) {
        return Arrays.stream(values()).filter(items::contains).findFirst().map(Set::of).orElseGet(Set::of);
    }

    public static Set<PetAccessory> decode(String value) {
        return headwear(Arrays.stream(values()).filter(item -> Arrays.asList(value.split(",")).contains(item.name()))
                .collect(Collectors.toUnmodifiableSet()));
    }

    public static String encode(Set<PetAccessory> items) {
        return Arrays.stream(values()).filter(items::contains).map(Enum::name).collect(Collectors.joining(","));
    }
}
